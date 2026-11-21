"""¿La diferencia entre configuraciones es real o ruido? Repite la validación cruzada con varias particiones.

Uso, desde servidor/:  python -m scripts.evaluar_robusto [--semillas 10]
Compara, con las mismas 100 capturas etiquetadas:
  - Haiku solo, Haiku razonando solo
  - Cascada Haiku → Haiku razonando con doble llave (la segunda lectura decide sola)
  - Cascada Haiku → fusión conjunta (al escalar, decide un modelo que ve las DOS lecturas)
Reporta media ± desviación del costo operativo, VP, FP, FN, acierto general y tokens por captura.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np

from app.evaluacion import MATRIZ, decisiones_3, optimizar_umbrales_3
from scripts.calibrar import fuera_de_muestra


def leer(corrida: Path) -> dict:
    filas = [json.loads(l) for l in (corrida / "detalle.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]
    filas = sorted([f for f in filas if f.get("etiqueta") and f["detalle"].get("caracteristicas")],
                   key=lambda f: f["oficial"]["capture_id"])
    return {
        "ids": [f["oficial"]["capture_id"] for f in filas],
        "verdad": [f["etiqueta"]["decision"] for f in filas],
        "X": [f["detalle"]["caracteristicas"] for f in filas],
        "bloq": np.array([bool(f["detalle"].get("compuertas_fallidas")) for f in filas]),
        "tok": np.array([f["traza"].get("tokens_entrada", 0) + f["traza"].get("tokens_salida", 0) for f in filas]),
    }


def probs(X, verdad, semilla, l2):
    ya = np.array([v == "aprobada" for v in verdad], int)
    yr = np.array([v == "rechazada" for v in verdad], int)
    return fuera_de_muestra(X, ya, 5, 2, l2, semilla), fuera_de_muestra(X, yr, 5, 2, l2, semilla)


def sola(pa, pr, verdad, bloq):
    o = optimizar_umbrales_3(pa, pr, verdad, bloqueadas=bloq)["optimo"]
    return decisiones_3(pa, pr, o["umbral_aprobar"], o["umbral_p_rechazo"], bloq)


def cascada(pa_r, pr_r, bloq_r, dec_f, verdad):
    mejor = None
    for ta in np.linspace(0.5, 1.01, 26):
        for tr in np.linspace(0.3, 1.01, 30):
            dec_r = decisiones_3(pa_r, pr_r, ta, tr, bloq_r)
            usa = (dec_r == "revision_manual") | (dec_r == "aprobada")
            final = np.where(dec_r == "aprobada", np.where(dec_f == "aprobada", "aprobada", dec_f),
                             np.where(usa, dec_f, dec_r))
            apr = [v for d, v in zip(final, verdad) if d == "aprobada"]
            if apr and sum(v == "aprobada" for v in apr) / len(apr) < 0.97:
                continue
            c = sum(MATRIZ[v][d] for d, v in zip(final, verdad))
            if mejor is None or (c, usa.mean()) < (mejor[0], mejor[2].mean()):
                mejor = (c, final, usa)
    return mejor[1], mejor[2]


def medir(dec, verdad, tokens) -> dict:
    vp = sum(d == "aprobada" and v == "aprobada" for d, v in zip(dec, verdad))
    fp = sum(d == "aprobada" and v != "aprobada" for d, v in zip(dec, verdad))
    fn = sum(d == "rechazada" and v == "aprobada" for d, v in zip(dec, verdad))
    return {"costo": sum(MATRIZ[v][d] for d, v in zip(dec, verdad)), "VP": vp, "FP": fp, "FN": fn,
            "acierto": float(np.mean([d == v for d, v in zip(dec, verdad)])), "tokens": float(np.mean(tokens))}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--corridas", type=Path, default=Path("../datos/corridas"))
    ap.add_argument("--semillas", type=int, default=10)
    ap.add_argument("--l2", type=float, default=3.0)
    ap.add_argument("--salida", type=Path, default=None)
    ap.add_argument("--segunda", default="dev_haikupensar_p3", help="Corrida del segundo modelo.")
    ap.add_argument("--extra", default=None, help="Otra corrida de segundo modelo para comparar (p. ej. esfuerzo alto).")
    ap.add_argument("--sin-conjunta", action="store_true", help="No evaluar la fusión de las dos lecturas.")
    a = ap.parse_args()
    H, HR = leer(a.corridas / "dev_haiku_p3b"), leer(a.corridas / a.segunda)
    EX = leer(a.corridas / a.extra) if a.extra else None
    assert H["ids"] == HR["ids"]
    verdad = H["verdad"]
    XJ = [{**{f"h:{k}": v for k, v in xh.items()}, **{f"hr:{k}": v for k, v in xr.items()}}
          for xh, xr in zip(H["X"], HR["X"])]
    bloq_j = HR["bloq"]

    res: dict[str, list[dict]] = {}
    for s in range(a.semillas):
        semilla = 1000 + 37 * s
        pa_h, pr_h = probs(H["X"], verdad, semilla, a.l2)
        pa_hr, pr_hr = probs(HR["X"], verdad, semilla, a.l2)
        d_h, d_hr = sola(pa_h, pr_h, verdad, H["bloq"]), sola(pa_hr, pr_hr, verdad, HR["bloq"])
        c1, u1 = cascada(pa_h, pr_h, H["bloq"], d_hr, verdad)
        configs = [
            ("Haiku solo", d_h, H["tok"]),
            ("Haiku razonando solo", d_hr, HR["tok"]),
            ("Cascada Haiku → Haiku razonando (doble llave)", c1, H["tok"] + HR["tok"] * u1),
        ]
        if not a.sin_conjunta:
            pa_j, pr_j = probs(XJ, verdad, semilla, a.l2)
            d_j = sola(pa_j, pr_j, verdad, bloq_j)
            c2, u2 = cascada(pa_h, pr_h, H["bloq"], d_j, verdad)
            configs += [("Cascada Haiku → fusión de las dos lecturas", c2, H["tok"] + HR["tok"] * u2),
                        ("Siempre las dos lecturas (fusión)", d_j, H["tok"] + HR["tok"])]
        if EX is not None:
            pa_x, pr_x = probs(EX["X"], verdad, semilla, a.l2)
            d_x = sola(pa_x, pr_x, verdad, EX["bloq"])
            c3, u3 = cascada(pa_h, pr_h, H["bloq"], d_x, verdad)
            configs += [(f"{a.extra} solo", d_x, EX["tok"]),
                        (f"Cascada Haiku → {a.extra} (doble llave)", c3, H["tok"] + EX["tok"] * u3)]
        for nombre, dec, tok in configs:
            res.setdefault(nombre, []).append(medir(dec, verdad, tok))
        print(f"semilla {s + 1}/{a.semillas}: " + ", ".join(f"{k.split(' (')[0]}={v[-1]['costo']}" for k, v in res.items()), flush=True)

    lineas = [f"{a.semillas} particiones distintas de validación cruzada (5 pliegues × 2 repeticiones cada una), 100 capturas.", "",
              "| Configuración | Costo operativo (media ± desv.) | Mínimo–máximo | VP | FP | FN | Acierto general | Tokens/captura |",
              "|---|---:|---:|---:|---:|---:|---:|---:|"]
    base = np.array([r["costo"] for r in res["Haiku razonando solo"]])
    for nombre, rs in res.items():
        c = np.array([r["costo"] for r in rs])
        lineas.append(f"| {nombre} | {c.mean():.1f} ± {c.std():.1f} | {c.min()}–{c.max()} | "
                      f"{np.mean([r['VP'] for r in rs]):.1f} | {np.mean([r['FP'] for r in rs]):.1f} | {np.mean([r['FN'] for r in rs]):.1f} | "
                      f"{np.mean([r['acierto'] for r in rs]):.0%} | {np.mean([r['tokens'] for r in rs]):,.0f} |")
    lineas += ["", "Diferencia pareada contra «Haiku razonando solo» (misma partición; negativo = mejor):", ""]
    for nombre, rs in res.items():
        if nombre == "Haiku razonando solo":
            continue
        d = np.array([r["costo"] for r in rs]) - base
        lineas.append(f"- {nombre}: {d.mean():+.1f} puntos en promedio; es mejor en {int((d < 0).sum())} de {len(d)} particiones, peor en {int((d > 0).sum())}.")
    texto = "\n".join(lineas)
    print(texto)
    if a.salida:
        a.salida.write_text(texto + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
