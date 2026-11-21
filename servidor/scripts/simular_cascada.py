"""Simula la cascada rápido → fuerte con dos corridas etiquetadas (una por modelo), fuera de muestra.

Uso: python -m scripts.simular_cascada --rapido ../datos/corridas/dev_haiku_p2 --fuerte ../datos/corridas/dev_sonnet_p2
Para cada umbral de "confianza suficiente" del modelo rápido reporta costo operativo, % que escala y tokens.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np

from app.evaluacion import MATRIZ, decisiones_3, optimizar_umbrales_3
from scripts.calibrar import fuera_de_muestra

PRECIO = {"haiku": (0.10, 0.50), "sonnet": (2.0, 10.0), "opus": (4.0, 20.0)}  # USD por millón (entrada, salida)


def cargar(corrida: Path, l2: float):
    filas = [json.loads(l) for l in (corrida / "detalle.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]
    filas = sorted([f for f in filas if f.get("etiqueta") and f["detalle"].get("caracteristicas")],
                   key=lambda f: f["oficial"]["capture_id"])
    X = [f["detalle"]["caracteristicas"] for f in filas]
    verdad = [f["etiqueta"]["decision"] for f in filas]
    pa = fuera_de_muestra(X, np.array([v == "aprobada" for v in verdad], int), 5, 5, l2)
    pr = fuera_de_muestra(X, np.array([v == "rechazada" for v in verdad], int), 5, 5, l2)
    tok = [(f["traza"].get("tokens_entrada", 0), f["traza"].get("tokens_salida", 0)) for f in filas]
    ids = [f["oficial"]["capture_id"] for f in filas]
    bloq = np.array([bool(f["detalle"].get("compuertas_fallidas")) for f in filas])
    return ids, verdad, pa, pr, tok, bloq


def usd(tok, modelo: str) -> float:
    pin, pout = next(v for k, v in PRECIO.items() if k in modelo)
    return sum(i * pin + o * pout for i, o in tok) / 1e6


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--rapido", required=True, type=Path)
    ap.add_argument("--fuerte", required=True, type=Path)
    ap.add_argument("--l2", type=float, default=3.0)
    ap.add_argument("--guardar", action="store_true", help="Guarda los umbrales de cascada elegidos para el modelo rápido.")
    ap.add_argument("--max-escala", type=float, default=1.0, help="Máxima fracción que puede escalar al fuerte.")
    ap.add_argument("--modelo-rapido", default="claude-haiku-5-5")
    ap.add_argument("--verificar", action="store_true", help="Simular la doble llave en las aprobaciones.")
    a = ap.parse_args()
    ids_r, verdad, pa_r, pr_r, tok_r, bloq_r = cargar(a.rapido, a.l2)
    ids_f, verdad_f, pa_f, pr_f, tok_f, bloq_f = cargar(a.fuerte, a.l2)
    assert ids_r == ids_f, "Las corridas deben tener las mismas capturas con IA"
    n = len(verdad)

    of = optimizar_umbrales_3(pa_f, pr_f, verdad, bloqueadas=bloq_f)["optimo"]
    dec_f = decisiones_3(pa_f, pr_f, of["umbral_aprobar"], of["umbral_p_rechazo"], bloq_f)
    filas = []
    for ta in np.linspace(0.80, 1.01, 22):
        for tr in np.linspace(0.50, 1.01, 18):
            dec_r = decisiones_3(pa_r, pr_r, ta, tr, bloq_r)
            escala = dec_r == "revision_manual"
            if a.verificar:  # doble llave: lo que el rápido aprueba solo queda si el fuerte también aprueba
                escala = escala | (dec_r == "aprobada")
                final = np.where(dec_r == "aprobada", np.where(dec_f == "aprobada", "aprobada", dec_f),
                                 np.where(escala, dec_f, dec_r))
            else:
                final = np.where(escala, dec_f, dec_r)
            costo = sum(MATRIZ[v][d] for d, v in zip(final, verdad))
            apr = [v for d, v in zip(final, verdad) if d == "aprobada"]
            if apr and sum(v == "aprobada" for v in apr) / len(apr) < 0.97:
                continue
            tok = sum(i + o for i, o in tok_r) + sum(i + o for (i, o), e in zip(tok_f, escala) if e)
            dinero = usd(tok_r, "haiku") + usd([t for t, e in zip(tok_f, escala) if e], "sonnet")
            filas.append((costo, escala.mean(), tok / n, dinero / n * 10_000, ta, tr))
    filas = [f for f in filas if f[1] <= a.max_escala]
    filas.sort(key=lambda r: (r[0], r[2]))
    print(f"Solo rápido: costo {optimizar_umbrales_3(pa_r, pr_r, verdad, bloqueadas=bloq_r)['optimo']['costo']}, "
          f"{sum(i + o for i, o in tok_r) / n:.0f} tokens/captura, ${usd(tok_r, 'haiku') / n * 1e4:.2f} por 10k")
    print(f"Solo fuerte: costo {of['costo']}, {sum(i + o for i, o in tok_f) / n:.0f} tokens/captura, "
          f"${usd(tok_f, 'sonnet') / n * 1e4:.2f} por 10k")
    print("Cascada (mejores por costo):  costo | % escala | tokens/captura | USD por 10k | umbrales rápido")
    vistos = set()
    for costo, esc, tok, dinero, ta, tr in filas:
        clave = (costo, round(esc, 2))
        if clave in vistos:
            continue
        vistos.add(clave)
        print(f"  {costo:5d} | {esc:6.0%} | {tok:8.0f} | ${dinero:8.2f} | aprobar≥{ta:.2f} rechazar≥{tr:.2f}")
        if len(vistos) >= 12:
            break
    if a.guardar and filas:
        costo, esc, tok, dinero, ta, tr = filas[0]
        destino = Path("modelos") / a.modelo_rapido
        destino.mkdir(parents=True, exist_ok=True)
        (destino / "umbrales_cascada.json").write_text(json.dumps(
            {"umbral_aprobar": float(ta), "umbral_p_rechazo": float(tr), "costo_estimado": costo,
             "fraccion_escala": round(float(esc), 3), "tokens_por_captura": round(tok)}, indent=1), encoding="utf-8")
        print("Guardado", destino / "umbrales_cascada.json")


if __name__ == "__main__":
    main()
