"""Aprende la fusión (p_aprobar y p_rechazo) y los umbrales de menor costo con una corrida etiquetada.

Uso, desde servidor/:
  python -m scripts.calibrar --corrida ../datos/corridas/dev_haiku_p2 [--guardar] [--l2 3] [--margen 0.05]
Sin --guardar solo reporta. Con --guardar escribe modelos/fusion.json, modelos/fusion_rechazo.json y
modelos/umbrales.json, que el motor carga al arrancar.

Las probabilidades para elegir umbrales y medir costo salen de validación cruzada repetida (fuera de
muestra): el costo reportado es una estimación honesta de lo que pasaría con capturas nuevas.
"""
from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path

import numpy as np

from app.evaluacion import MATRIZ, costo_3, decisiones_3, optimizar_umbrales_3
from app.motor.fusion import CARPETA_MODELOS, entrenar, guardar_motivos

MODELOS = CARPETA_MODELOS
PREFIJOS_MOTIVO = ("ia_nitidez", "ia_tamano", "ia_legibilidad", "log_nitidez", "ia_descripcion_completa",
                   "ia_imagen_borrosa", "ia_captura_lejana", "compuerta_calidad", "compuerta_descripcion",
                   "ia_producto_visible", "ia_etiqueta_visible")


def fuera_de_muestra(X, y, pliegues: int, repeticiones: int, l2: float, semilla: int = 100) -> np.ndarray:
    n = len(y)
    acumulado, cuenta = np.zeros(n), np.zeros(n)
    for r in range(repeticiones):
        pliegue = np.random.default_rng(semilla + r).permutation(n) % pliegues
        for k in range(pliegues):
            tr, te = np.where(pliegue != k)[0], np.where(pliegue == k)[0]
            if len(set(y[tr])) < 2:
                continue
            m = entrenar([X[i] for i in tr], list(y[tr]), l2=l2)
            acumulado[te] += [m.p(X[i]) for i in te]
            cuenta[te] += 1
    return acumulado / np.maximum(cuenta, 1)


def reportes_motivo_reglas(reporte: dict) -> float:
    return reporte.get("acierto_motivo_reglas") or 0.0


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--corrida", required=True, type=Path)
    ap.add_argument("--precision", type=float, default=0.97)
    ap.add_argument("--margen", type=float, default=0.05)
    ap.add_argument("--pliegues", type=int, default=5)
    ap.add_argument("--repeticiones", type=int, default=5)
    ap.add_argument("--l2", type=float, default=3.0)
    ap.add_argument("--quitar", default="", help="Prefijos de características a excluir, separados por coma.")
    ap.add_argument("--guardar", action="store_true")
    ap.add_argument("--sin-compuertas", action="store_true", help="Medir sin las compuertas de aprobación.")
    ap.add_argument("--modelo", default=None, help="Modelo de IA de la corrida (por defecto, el de la traza).")
    a = ap.parse_args()

    todas = [json.loads(l) for l in (a.corrida / "detalle.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]
    todas = [f for f in todas if f.get("etiqueta")]
    filas = [f for f in todas if f["detalle"].get("caracteristicas")]
    fijas = [f for f in todas if not f["detalle"].get("caracteristicas")]  # decididas por la capa local
    quitar = [q for q in a.quitar.split(",") if q]
    X = [{k: v for k, v in f["detalle"]["caracteristicas"].items() if not any(k.startswith(q) for q in quitar)} for f in filas]
    verdad = [f["etiqueta"]["decision"] for f in filas]
    y_apr = np.array([v == "aprobada" for v in verdad], dtype=int)
    y_rech = np.array([v == "rechazada" for v in verdad], dtype=int)
    costo_fijas = sum(MATRIZ[f["etiqueta"]["decision"]][f["oficial"]["decision"]] for f in fijas)

    pa = fuera_de_muestra(X, y_apr, a.pliegues, a.repeticiones, a.l2)
    pr = fuera_de_muestra(X, y_rech, a.pliegues, a.repeticiones, a.l2)
    pa_reglas = np.array([f["detalle"]["p_reglas"] for f in filas])

    bloq = np.array([bool(f["detalle"].get("compuertas_fallidas")) for f in filas]) if not a.sin_compuertas else None
    aprendida = optimizar_umbrales_3(pa, pr, verdad, a.precision, a.margen, bloqueadas=bloq)
    reglas = optimizar_umbrales_3(pa_reglas, 1 - pa_reglas, verdad, a.precision, a.margen, bloqueadas=bloq)
    o = aprendida["optimo"]
    dec = decisiones_3(pa, pr, o["umbral_aprobar"], o["umbral_p_rechazo"], bloq)
    matriz = Counter(zip(verdad, dec))

    # Motivo de rechazo: un clasificador por motivo (uno contra el resto) con las capturas rechazadas.
    idx_r = [i for i, f in enumerate(filas) if verdad[i] == "rechazada" and f["etiqueta"].get("reason_code")]
    motivos_verdad = [filas[i]["etiqueta"]["reason_code"] for i in idx_r]
    clases = sorted({m for m in motivos_verdad if motivos_verdad.count(m) >= 3})
    acierto_motivo, modelos_motivo = None, {}
    if len(clases) >= 2:
        # Solo señales de calidad: con pocos rechazos, más variables sobreajustan el motivo.
        Xr = [{k: v for k, v in X[i].items() if k.startswith(PREFIJOS_MOTIVO)} for i in idx_r]
        oof = {k: fuera_de_muestra(Xr, np.array([m == k for m in motivos_verdad], int), a.pliegues, a.repeticiones, a.l2)
               for k in clases}
        pred = [max(clases, key=lambda k: oof[k][j]) for j in range(len(idx_r))]
        acierto_motivo = round(sum(p == v for p, v in zip(pred, motivos_verdad)) / len(idx_r), 3)
        reglas_motivo = round(sum(filas[i]["oficial"].get("reason_code") == motivos_verdad[j]
                                  for j, i in enumerate(idx_r)) / len(idx_r), 3)
        modelos_motivo = {k: entrenar(Xr, [int(m == k) for m in motivos_verdad], l2=a.l2) for k in clases}

    m_apr = entrenar(X, list(y_apr), l2=a.l2)
    m_rech = entrenar(X, list(y_rech), l2=a.l2)
    pesos = sorted(zip(m_apr.nombres, m_apr.pesos[:-1]), key=lambda t: -abs(t[1]))[:10]
    pesos_r = sorted(zip(m_rech.nombres, m_rech.pesos[:-1]), key=lambda t: -abs(t[1]))[:10]
    n_total = len(todas)
    reporte = {
        "capturas": n_total, "con_ia": len(filas), "decididas_local": len(fijas), "costo_local": costo_fijas,
        "costo_todo_a_revision": n_total,
        "aprendida_fuera_de_muestra": {**o, "costo_total": o["costo"] + costo_fijas,
                                       "costo_por_captura": round((o["costo"] + costo_fijas) / n_total, 3)},
        "reglas": {**reglas["optimo"], "costo_total": reglas["optimo"]["costo"] + costo_fijas},
        "verdad_vs_decision": {f"{v} -> {d}": n for (v, d), n in sorted(matriz.items())},
        "acierto_motivo_aprendido_fuera_de_muestra": acierto_motivo,
        "acierto_motivo_reglas": reglas_motivo if acierto_motivo is not None else None,
        "aprobaciones_bloqueadas_por_compuertas": int(sum(1 for i in range(len(filas))
                                                          if bloq is not None and bloq[i] and pa[i] >= o["umbral_aprobar"])),
        "compuertas_en_aprobadas_reales": dict(Counter(n for f in filas if f["etiqueta"]["decision"] == "aprobada"
                                                       for n in f["detalle"].get("compuertas_fallidas", []))),
        "pesos_aprobar": [(n, round(float(w), 2)) for n, w in pesos],
        "pesos_rechazo": [(n, round(float(w), 2)) for n, w in pesos_r],
    }
    print(json.dumps(reporte, ensure_ascii=False, indent=1))

    if a.guardar:
        modelo_ia = a.modelo or next((f["traza"]["modelos_usados"][-1] for f in filas if f["traza"].get("modelos_usados")), None)
        destino = MODELOS / modelo_ia if modelo_ia else MODELOS
        destino.mkdir(parents=True, exist_ok=True)
        m_apr.guardar(destino / "fusion.json")
        m_rech.guardar(destino / "fusion_rechazo.json")
        if modelos_motivo and acierto_motivo is not None and acierto_motivo > reportes_motivo_reglas(reporte):
            guardar_motivos(modelos_motivo, destino / "motivos.json")
        else:
            (destino / "motivos.json").unlink(missing_ok=True)
        (destino / "umbrales.json").write_text(json.dumps(
            {"umbral_aprobar": o["umbral_aprobar"], "umbral_p_rechazo": o["umbral_p_rechazo"],
             "corrida": str(a.corrida), "costo_estimado_por_captura": reporte["aprendida_fuera_de_muestra"]["costo_por_captura"]},
            indent=1), encoding="utf-8")
        print("Guardado en", destino.resolve())


if __name__ == "__main__":
    main()
