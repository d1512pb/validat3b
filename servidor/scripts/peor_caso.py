"""Tabla de peor caso: cada captura pasa completa por el modelo (sin atajos locales ni caché).

Uso, desde servidor/:  python -m scripts.peor_caso [--salida ../docs/pruebas/peor-caso.md]
Costo con los tokens reales de cada captura de desarrollo; precisión fuera de muestra (validación cruzada).
"""
from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np

from scripts.comparar import PRECIO, desglose, una_sola
from scripts.simular_cascada import cargar

MODELOS = [  # (nombre para Ari, corrida de un solo modelo, precio)
    ("Haiku 5.5", "dev_haiku_p3b", "haiku"),
    ("Haiku 5.5 razonando", "dev_haikupensar_p3", "haiku"),
    ("Sonnet 5.5", "dev_sonnet_p3b", "sonnet"),
]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--corridas", type=Path, default=Path("../datos/corridas"))
    ap.add_argument("--salida", type=Path, default=None)
    a = ap.parse_args()

    filas, detalle = [], []
    for nombre, corrida, precio in MODELOS:
        r = cargar(a.corridas / corrida, 3.0)
        verdad, tok = r[1], r[4]
        pin, pout = PRECIO[precio]
        usd = np.array([(i * pin + o * pout) / 1e6 for i, o in tok])
        d = desglose(verdad, una_sola(r))
        n = len(verdad)
        aprob = d["VP"] + d["FP"]
        prec = f"{d['VP'] / aprob:.0%}" if aprob else "— (no aprueba)"
        acierto = (d["VP"] + d["VN"] + d["rev_ok"]) / n
        solas = (d["VP"] + d["VN"] + d["FP"] + d["FN"] + d["FN_rev"]) / n
        c = usd.mean()
        filas.append(f"| **{nombre}** | ${c / 3:.4f} | ${c:.4f} | ${c * 10_000 / 3:,.0f} | ${c * 10_000:,.0f} | "
                     f"{prec} | {acierto:.0%} | {solas:.0%} | {d['puntos']} |")
        detalle.append(f"| {nombre} | {np.mean([i + o for i, o in tok]):,.0f} | {max(i + o for i, o in tok):,} | "
                       f"${usd.max() / 3:.4f} | ${usd.max() * 10_000:,.0f} | {d['VP']} | {d['VN']} | {d['FP']} | {d['FN']} | "
                       f"{d['rev_ok'] + d['rev_valida'] + d['rev_rechazo']} |")

    texto = "\n".join([
        "| Modelo | USD por imagen | USD por captura (3 fotos) | 10,000 fotos | 10,000 capturas (30,000 fotos) | Precisión al aprobar | Acierto general | Se resuelven sin humano | Costo operativo (100 capturas) |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|",
        *filas,
        "",
        "Detalle por modelo:",
        "",
        "| Modelo | Tokens promedio por captura | Tokens de la captura más cara | USD por imagen (captura más cara) | 10,000 capturas si todas fueran la más cara | VP | VN | FP | FN | A revisión |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
        *detalle,
    ])
    print(texto)
    if a.salida:
        a.salida.write_text(texto + "\n", encoding="utf-8")
        print("Escrito", a.salida)


if __name__ == "__main__":
    main()
