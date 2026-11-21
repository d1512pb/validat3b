"""Las pruebas no deben depender de los modelos calibrados en modelos/ ni de la llave real."""
import pytest

from app.config import config
from app.motor import fusion, ia, orquestador
from app.motor.huellas import IndiceHuellas


@pytest.fixture(autouse=True)
def sin_modelos_calibrados(tmp_path, monkeypatch):
    monkeypatch.setattr(fusion, "CARPETA_MODELOS", tmp_path / "modelos_vacios")
    fusion._cargar.cache_clear()
    fusion.umbrales.cache_clear()
    fusion._motivos.cache_clear()
    monkeypatch.setattr(config(), "umbral_aprobar", 0.95)
    monkeypatch.setattr(config(), "umbral_p_rechazo", 0.67)
    monkeypatch.setattr(config(), "cache_ia_dir", None)
    # Cascada fija para las pruebas, sin importar lo que diga el .env local.
    monkeypatch.setattr(config(), "modelo_rapido", "claude-haiku-5-5")
    monkeypatch.setattr(config(), "modelo_fuerte", "claude-haiku-5-5-pensar")
    monkeypatch.setattr(config(), "escalar_a_fuerte", True)
    monkeypatch.setattr(config(), "verificar_aprobaciones", True)
    ia._cache.cache_clear()

    def sin_red(*a, **k):
        raise RuntimeError("Las pruebas no llaman a la API real")
    monkeypatch.setattr(ia, "_cliente", sin_red)
    monkeypatch.setattr(orquestador, "INDICE_GLOBAL", IndiceHuellas())
    yield
    fusion._cargar.cache_clear()
    fusion.umbrales.cache_clear()
    fusion._motivos.cache_clear()
