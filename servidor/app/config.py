"""Configuración por variables de entorno (o archivo .env en servidor/)."""
from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Config(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # Proveedor: "auto" usa Foundry si hay FOUNDRY_API_KEY y si no, la API de Anthropic (ANTHROPIC_API_KEY)
    proveedor: Literal["auto", "foundry", "anthropic"] = "auto"
    anthropic_api_key: str | None = None
    anthropic_workspace_id: str | None = None  # obligatorio si la llave no está ligada a un workspace

    # Endpoint del hackatón (Azure AI Foundry con modelos de Anthropic)
    foundry_api_key: str | None = None
    foundry_base_url: str = "https://softwaredevelopment-resource.services.ai.azure.com/anthropic"
    modelo_rapido: str = "claude-haiku-5-5"
    modelo_fuerte: str = "claude-sonnet-5-5"
    escalar_a_fuerte: bool = True

    # "real" llama a la IA; "simulado" responde sin gastar tokens (para integrar la app)
    modo: Literal["real", "simulado"] = "real"

    # Umbrales de decisión. p_aprobar = probabilidad de que la captura sea válida;
    # p_rechazo = probabilidad de que la verdad sea "rechazada" (si no hay modelo, 1 - p_aprobar).
    # Con costos 10 (aprobar mala), 3 (rechazar buena) y 1 (revisión): aprobar si p_aprobar >= 0.90 y
    # rechazar si p_rechazo >= 2/3. Aprobamos más arriba para cuidar la precisión >= 97%.
    umbral_aprobar: float = 0.95
    umbral_p_rechazo: float = 0.67

    # Imágenes que se mandan al modelo (lado mayor en px). Menos px = menos tokens.
    # Medido en desarrollo: 768/384 da el mismo costo operativo que 960/640 con 15% menos tokens.
    lado_max_fachada: int = 384
    lado_max_producto: int = 768
    lado_max_etiqueta: int = 768
    max_mb_foto: float = 8.0

    # Si se define, la app debe mandar el header X-API-Key con este valor
    servidor_token: str | None = None

    # Compuertas de aprobación (app/motor/compuertas.py): aprobar solo si todas pasan.
    compuertas_activas: bool = True
    compuerta_nitidez_min: float = 0.5
    compuerta_legibilidad_min: float = 0.5
    compuerta_confianza_min: float = 0.8
    compuerta_desvio_unitario: float = 0.15
    compuerta_anomalias: bool = False  # el modelo lista anomalías triviales en 18 de 33 aprobadas reales: solo señal
    # Doble llave: toda aprobación del modelo rápido se confirma con el fuerte antes de aprobar.
    verificar_aprobaciones: bool = True

    # Caché en disco de respuestas del modelo (None = sin caché). El modo lote la activa.
    cache_ia_dir: str | None = None
    # Tokens por minuto de la llave (0 = sin límite). Se ajusta cuando sepamos el TPM real.
    tpm_limite: int = 0

    # Modelo de fusión aprendido (JSON). Si no existe, se usan las severidades de reglas.
    ruta_fusion: str = "modelos/fusion.json"
    ruta_fusion_rechazo: str = "modelos/fusion_rechazo.json"
    # CSV de tiendas por cadena con lat/lon (compartir/tiendas_por_cadena.csv). Opcional.
    ruta_tiendas: str | None = None


RUTA_UMBRALES = "modelos/umbrales.json"


@lru_cache
def config() -> Config:
    """Las variables de entorno mandan; si no definen umbrales, se usan los calibrados (modelos/umbrales.json)."""
    import json
    import os
    from pathlib import Path

    cfg = Config()
    ruta = Path(RUTA_UMBRALES)
    claves = ("umbral_aprobar", "umbral_p_rechazo")
    if ruta.exists() and not any(k.upper() in os.environ for k in claves):
        calibrados = json.loads(ruta.read_text(encoding="utf-8"))
        cfg = cfg.model_copy(update={k: float(v) for k, v in calibrados.items() if k in claves})
    return cfg
