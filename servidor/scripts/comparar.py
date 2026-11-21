"""Compara configuraciones del motor con las mismas capturas etiquetadas, fuera de muestra.

Uso, desde servidor/:
  python -m scripts.comparar --corridas ../datos/corridas [--salida ../docs/pruebas/comparacion.md]

Necesita las corridas de un solo modelo (sin escalar ni verificar) de cada modelo:
  dev_haiku_p3b (Haiku sin razonar), dev_haikupensar_p3 (Haiku razonando), dev_sonnet_p3b (Sonnet).
Para cada configuración reporta el desglose VP / VN / FP / FN con los puntos de la tabla oficial,
el costo operativo, los tokens y el dinero por captura, por imagen y por 10,000 capturas.
"""
from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np

from app.evaluacion import MATRIZ, decisiones_3, optimizar_umbrales_3
from scripts.simular_cascada import cargar

PRECIO = {"haiku": (0.10, 0.50), "sonnet": (2.0, 10.0)}  # USD por millón de tokens (entrada, salida)


def usd(tok, modelo: str) -> np.ndarray:
    pin, pout = next(v for k, v in PRECIO.items() if k in modelo)
    return np.array([(i * pin + o * pout) / 1e6 for i, o in tok])


def una_sola(r) -> np.ndarray:
    _, verdad, pa, pr, _, bloq = r
    o = optimizar_umbrales_3(pa, pr, verdad, bloqueadas=bloq)["optimo"]
    return decisiones_3(pa, pr, o["umbral_aprobar"], o["umbral_p_rechazo"], bloq)


def cascada(rapido, fuerte, max_escala: float = 0.9):
    """Cascada con doble llave; elige los umbrales del rápido de menor costo (fuera de muestra)."""
    _, verdad, pa_r, pr_r, _, bloq_r = rapido
    dec_f = una_sola(fuerte)
    mejor = None
    for ta in np.linspace(0.5, 1.01, 52):
        for tr in np.linspace(0.3, 1.01, 72):
            dec_r = decisiones_3(pa_r, pr_r, ta, tr, bloq_r)
            usa_f = (dec_r == "revision_manual") | (dec_r == "aprobada")
            if usa_f.mean() > max_escala:
                continue
            final = np.where(dec_r == "aprobada", np.where(dec_f == "aprobada", "aprobada", dec_f),
                             np.where(usa_f, dec_f, dec_r))
            apr = [v for d, v in zip(final, verdad) if d == "aprobada"]
            if apr and sum(v == "aprobada" for v in apr) / len(apr) < 0.97:
                continue
            c = sum(MATRIZ[v][d] for d, v in zip(final, verdad))
            if mejor is None or (c, usa_f.mean()) < (mejor[0], mejor[2].mean()):
                mejor = (c, final, usa_f)
    return mejor[1], mejor[2]


def desglose(verdad, dec) -> dict:
    d = {"VP": 0, "VN": 0, "FP": 0, "FN": 0, "FN_rev": 0, "rev_ok": 0, "rev_valida": 0, "rev_rechazo": 0}
    for v, x in zip(verdad, dec):
        if x == "aprobada":
            d["VP" if v == "aprobada" else "FP"] += 1
        elif x == "rechazada":
            d["VN" if v == "rechazada" else ("FN" if v == "aprobada" else "FN_rev")] += 1
        else:
            d["rev_ok" if v == "revision_manual" else ("rev_valida" if v == "aprobada" else "rev_rechazo")] += 1
    d["puntos"] = sum(MATRIZ[v][x] for v, x in zip(verdad, dec))
    return d


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--corridas", type=Path, default=Path("../datos/corridas"))
    ap.add_argument("--salida", type=Path, default=None)
    ap.add_argument("--l2", type=float, default=3.0)
    a = ap.parse_args()

    H = cargar(a.corridas / "dev_haiku_p3b", a.l2)
    HP = cargar(a.corridas / "dev_haikupensar_p3", a.l2)
    S = cargar(a.corridas / "dev_sonnet_p3b", a.l2)
    assert H[0] == HP[0] == S[0], "Las corridas deben tener las mismas capturas"
    verdad = H[1]
    n = len(verdad)
    dinero = {"h": usd(H[4], "haiku"), "hp": usd(HP[4], "haiku"), "s": usd(S[4], "sonnet")}
    tokens = {k: np.array([i + o for i, o in r[4]]) for k, r in (("h", H), ("hp", HP), ("s", S))}

    filas = [("Todo a revisión manual (referencia)", np.full(n, "revision_manual", dtype=object), 0.0, 0.0, "—")]
    filas.append(("Solo Haiku (sin razonar)", una_sola(H), dinero["h"].mean(), tokens["h"].mean(), "0%"))
    filas.append(("Solo Haiku razonando", una_sola(HP), dinero["hp"].mean(), tokens["hp"].mean(), "0%"))
    dec, usa = cascada(H, HP)
    filas.append(("Cascada Haiku → Haiku razonando + doble llave", dec,
                  (dinero["h"] + dinero["hp"] * usa).mean(), (tokens["h"] + tokens["hp"] * usa).mean(), f"{usa.mean():.0%}"))
    filas.append(("Solo Sonnet", una_sola(S), dinero["s"].mean(), tokens["s"].mean(), "0%"))
    dec, usa = cascada(H, S)
    filas.append(("Cascada Haiku → Sonnet + doble llave (desplegada hoy)", dec,
                  (dinero["h"] + dinero["s"] * usa).mean(), (tokens["h"] + tokens["s"] * usa).mean(), f"{usa.mean():.0%}"))

    lineas = [
        f"Capturas con IA: {n} (las decididas por la capa local, sin IA, no cambian entre configuraciones).",
        "Fuera de muestra: validación cruzada 5×5; umbrales con precisión de aprobaciones ≥ 97%.",
        "",
        "| Configuración | Costo operativo | VP | VN | **FP** (×10) | **FN** (×3) | Rechazo de revisión (×3) | Revisión: era revisión | Revisión: era válida | Revisión: era rechazo | Escala al 2º modelo | Tokens/captura | USD/captura | USD/imagen | USD por 10,000 capturas |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for nombre, dec, usd_c, tok_c, esc in filas:
        d = desglose(verdad, dec)
        lineas.append(
            f"| {nombre} | **{d['puntos']}** | {d['VP']} | {d['VN']} | **{d['FP']}** | **{d['FN']}** | {d['FN_rev']} | "
            f"{d['rev_ok']} | {d['rev_valida']} | {d['rev_rechazo']} | {esc} | {tok_c:,.0f} | ${usd_c:.5f} | "
            f"${usd_c / 3:.5f} | ${usd_c * 10_000:,.2f} |")
    texto = "\n".join(lineas)
    print(texto)
    if a.salida:
        a.salida.parent.mkdir(parents=True, exist_ok=True)
        a.salida.write_text(texto + "\n", encoding="utf-8")
        print("Escrito", a.salida)


if __name__ == "__main__":
    main()
