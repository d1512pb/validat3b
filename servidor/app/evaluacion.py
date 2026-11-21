"""Cómo nos califican: costo operativo, precisión de aprobaciones y calidad del dato.

Réplica de la tabla oficial (0 / 1 / 3 / 10). Cuando llegue el script oficial, se compara contra él.
"""
from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np

COSTO = {"acierto": 0, "revision_manual": 1, "rechazo_de_valida": 3, "aprobacion_de_invalida": 10}
PRECISION_MINIMA = 0.97

# Costo según la verdad (filas) y lo que decidimos (columnas).
# Supuesto mientras no tengamos el script oficial: si la verdad es revision_manual, aprobar cuesta 10
# (no es válida), rechazar cuesta 3 y mandarla a revisión cuesta 1, como cualquier revisión.
MATRIZ = {
    "aprobada":        {"aprobada": 0,  "rechazada": 3, "revision_manual": 1},
    "rechazada":       {"aprobada": 10, "rechazada": 0, "revision_manual": 1},
    "revision_manual": {"aprobada": 10, "rechazada": 3, "revision_manual": 1},
}


def _verdad(v: bool | str) -> str:
    if isinstance(v, str):
        return v
    return "aprobada" if v else "rechazada"


def costo(decision: str, verdad: bool | str) -> int:
    if decision not in MATRIZ["aprobada"]:
        raise ValueError(f"decision inválida: {decision}")
    return MATRIZ[_verdad(verdad)][decision]


def wilson_inferior(aciertos: int, n: int, z: float = 1.645) -> float:
    """Cota inferior (95% de un lado) de una proporción. Protege el requisito de precisión >= 97%."""
    if n == 0:
        return 1.0
    p = aciertos / n
    den = 1 + z * z / n
    centro = p + z * z / (2 * n)
    margen = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n))
    return (centro - margen) / den


@dataclass
class Etiqueta:
    valida: bool | None = None
    reason_code: str | None = None
    precio: float | None = None
    decision: str | None = None   # aprobada | rechazada | revision_manual (decision_esperada)

    def __post_init__(self):
        if self.decision is None:
            self.decision = "aprobada" if self.valida else "rechazada"
        self.valida = self.decision == "aprobada"


def resumen(predicciones: list[dict], etiquetas: dict[str, Etiqueta]) -> dict:
    """predicciones: resultados oficiales (dicts). etiquetas: por capture_id."""
    filas = [(p, etiquetas[p["capture_id"]]) for p in predicciones if p["capture_id"] in etiquetas]
    n = len(filas)
    if n == 0:
        return {"n": 0}
    costos = [costo(p["decision"], e.decision) for p, e in filas]
    aprobadas = [(p, e) for p, e in filas if p["decision"] == "aprobada"]
    buenas = sum(e.valida for _, e in aprobadas)
    rechazos = [(p, e) for p, e in filas if p["decision"] == "rechazada" and e.decision == "rechazada" and e.reason_code]
    confusion: dict[str, dict[str, int]] = {}
    for p, e in filas:
        confusion.setdefault(e.decision, {}).setdefault(p["decision"], 0)
        confusion[e.decision][p["decision"]] += 1
    con_precio = [(p, e) for p, e in filas if e.precio is not None]
    precio_ok = sum(p.get("precio_detectado") is not None and abs(p["precio_detectado"] - e.precio) <= 0.011
                    for p, e in con_precio)
    precision = buenas / len(aprobadas) if aprobadas else 1.0
    return {
        "n": n,
        "costo_total": int(sum(costos)),
        "costo_por_captura": round(sum(costos) / n, 4),
        "costo_todo_a_revision": n,
        "costo_aprobar_todo": int(sum(costo("aprobada", e.decision) for _, e in filas)),
        "acierto_decision": round(sum(p["decision"] == e.decision for p, e in filas) / n, 4),
        "verdad_vs_decision": confusion,
        "aprobadas": len(aprobadas),
        "rechazadas": sum(p["decision"] == "rechazada" for p, _ in filas),
        "revision_manual": sum(p["decision"] == "revision_manual" for p, _ in filas),
        "precision_aprobadas": round(precision, 4),
        "precision_aprobadas_cota_inferior": round(wilson_inferior(buenas, len(aprobadas)), 4),
        "cumple_precision": precision >= PRECISION_MINIMA,
        "aprobaciones_de_invalidas": len(aprobadas) - buenas,
        "rechazos_de_validas": sum(p["decision"] == "rechazada" and e.valida for p, e in filas),
        "rechazos_de_revision": sum(p["decision"] == "rechazada" and e.decision == "revision_manual" for p, e in filas),
        "acierto_reason_code": round(sum(p["reason_code"] == e.reason_code for p, e in rechazos) / len(rechazos), 4) if rechazos else None,
        "acierto_precio": round(precio_ok / len(con_precio), 4) if con_precio else None,
    }


def decisiones_3(pa: np.ndarray, pr: np.ndarray, t_apr: float, t_rech: float, bloqueadas=None) -> np.ndarray:
    """bloqueadas: capturas con alguna compuerta fallida; nunca se aprueban (van a revisión)."""
    d = np.full(len(pa), "revision_manual", dtype=object)
    d[pr >= t_rech] = "rechazada"
    apr = pa >= t_apr
    if bloqueadas is not None:
        apr &= ~np.asarray(bloqueadas, bool)
    d[apr] = "aprobada"
    return d


def costo_3(decisiones, verdad) -> tuple[int, int, int]:
    """(costo total, aprobadas, aprobadas válidas) con la matriz de 3 clases."""
    c = sum(MATRIZ[v][d] for d, v in zip(decisiones, verdad))
    apr = [v for d, v in zip(decisiones, verdad) if d == "aprobada"]
    return int(c), len(apr), sum(v == "aprobada" for v in apr)


def optimizar_umbrales_3(pa, pr, verdad, precision_minima: float = PRECISION_MINIMA, margen: float = 0.05,
                         usar_cota: bool = False, bloqueadas=None) -> dict:
    """Umbrales (aprobar sobre p_aprobar, rechazar sobre p_rechazo) de menor costo.

    Robustez: la precisión de aprobaciones debe cumplirse en t_aprobar y también en t_aprobar - margen,
    para no quedar al filo. Con usar_cota exige además la cota inferior de Wilson (necesita muchos datos).
    """
    pa, pr, verdad = np.asarray(pa, float), np.asarray(pr, float), list(verdad)
    cand_a = np.unique(np.concatenate([np.linspace(0.5, 1.0, 51), pa[pa >= 0.5]]))
    cand_r = np.unique(np.concatenate([np.linspace(0.3, 1.0, 36), pr[pr >= 0.3]]))
    nunca = 1.01
    mejor = None

    def precision_ok(ta: float) -> tuple[bool, float, float]:
        _, n, ok = costo_3(decisiones_3(pa, np.zeros_like(pr), ta, nunca, bloqueadas), verdad)
        prec = ok / n if n else 1.0
        cumple = prec >= precision_minima and (not usar_cota or wilson_inferior(ok, n) >= precision_minima)
        return cumple, prec, wilson_inferior(ok, n)

    for ta in list(cand_a) + [nunca]:
        if ta < nunca:
            ok1, prec, cota = precision_ok(ta)
            ok2, _, _ = precision_ok(max(ta - margen, 0.0))
            if not (ok1 and ok2):
                continue
        else:
            prec, cota = 1.0, 1.0
        for tr in list(cand_r) + [nunca]:
            c, n, _ = costo_3(decisiones_3(pa, pr, ta, tr, bloqueadas), verdad)
            if mejor is None or c < mejor["costo"]:
                mejor = {"costo": c, "umbral_aprobar": float(ta), "umbral_p_rechazo": float(tr),
                         "aprobadas": n, "precision": round(prec, 4), "cota_inferior": round(cota, 4)}
    return {"optimo": mejor, "n": len(verdad), "costo_todo_a_revision": len(verdad)}


def costo_con_umbrales(p: np.ndarray, y: np.ndarray, t_apr: float, t_rech: float) -> tuple[int, int, int]:
    """Devuelve (costo, aprobadas, aprobadas_validas)."""
    apr = p >= t_apr
    rech = (p <= t_rech) & ~apr
    rev = ~apr & ~rech
    c = 10 * int((apr & (y == 0)).sum()) + 3 * int((rech & (y == 1)).sum()) + int(rev.sum())
    return c, int(apr.sum()), int((apr & (y == 1)).sum())


def optimizar_umbrales(p, y, precision_minima: float = PRECISION_MINIMA, usar_cota: bool = True) -> dict:
    """Busca (t_aprobar, t_rechazar) de menor costo con precisión de aprobaciones >= mínima.

    Con usar_cota=True exige la cota inferior de Wilson, no solo la precisión observada: margen
    para que el set ciego no nos deje fuera de la clasificación.
    """
    p, y = np.asarray(p, float), np.asarray(y, int)
    candidatos_apr = np.linspace(0.5, 1.0, 101)
    candidatos_rech = np.linspace(0.0, 0.5, 51)
    mejor = None
    for ta in candidatos_apr:
        for tr in candidatos_rech:
            if tr >= ta:
                continue
            c, n_apr, n_ok = costo_con_umbrales(p, y, ta, tr)
            prec = n_ok / n_apr if n_apr else 1.0
            prec_ok = (wilson_inferior(n_ok, n_apr) if usar_cota else prec) >= precision_minima
            if not prec_ok:
                continue
            if mejor is None or c < mejor["costo"] or (c == mejor["costo"] and ta > mejor["umbral_aprobar"]):
                mejor = {"costo": c, "umbral_aprobar": float(ta), "umbral_rechazar": float(tr),
                         "aprobadas": n_apr, "precision": round(prec, 4),
                         "cota_inferior": round(wilson_inferior(n_ok, n_apr), 4)}
    teorico = costo_con_umbrales(p, y, 0.90, 1 / 3)[0]
    return {"optimo": mejor, "costo_umbrales_teoricos_0.90_0.33": teorico, "n": len(y)}
