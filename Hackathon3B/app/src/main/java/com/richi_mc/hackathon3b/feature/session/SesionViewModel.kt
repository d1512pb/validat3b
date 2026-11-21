package com.richi_mc.hackathon3b.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.richi_mc.hackathon3b.app.AppContainer
import com.richi_mc.hackathon3b.core.model.Cadena
import com.richi_mc.hackathon3b.core.model.Problema
import com.richi_mc.hackathon3b.core.model.Sesion
import com.richi_mc.hackathon3b.core.model.TipoFoto
import com.richi_mc.hackathon3b.core.model.Ubicacion
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface GpsUi {
    data object Buscando : GpsUi
    data class Listo(val ubicacion: Ubicacion) : GpsUi
    data object SinUbicacion : GpsUi
}

/** Foto de fachada rechazada on-device. */
data class RechazoFachada(val path: String, val problemas: List<Problema>)

data class SesionUiState(
    val cadena: Cadena? = null,
    val tienda: String = "",
    val gps: GpsUi = GpsUi.Buscando,
    val camaraAbierta: Boolean = false,
    val analizando: Boolean = false,
    val rechazo: RechazoFachada? = null,
) {
    val puedeTomarFoto get() = cadena != null && tienda.isNotBlank() && !analizando
}

class SesionViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SesionUiState())
    val state: StateFlow<SesionUiState> = _state

    private var gpsJob: Job? = null

    fun elegirCadena(c: Cadena) = _state.update { it.copy(cadena = c) }
    fun cambiarTienda(t: String) = _state.update { it.copy(tienda = t) }

    fun refrescarUbicacion() {
        gpsJob?.cancel()
        _state.update { it.copy(gps = GpsUi.Buscando) }
        gpsJob = viewModelScope.launch {
            val u = container.locationProvider.ubicacionActual()
            _state.update { it.copy(gps = if (u != null) GpsUi.Listo(u) else GpsUi.SinUbicacion) }
        }
    }

    fun abrirCamara() = _state.update { it.copy(camaraAbierta = true, rechazo = null) }
    fun cerrarCamara() = _state.update { it.copy(camaraAbierta = false) }

    /** Analiza la fachada; si es válida crea la sesión y llama [onListo]. */
    fun onFoto(path: String, onListo: () -> Unit) {
        _state.update { it.copy(camaraAbierta = false, analizando = true, rechazo = null) }
        viewModelScope.launch {
            try {
                val analisis = container.imageAnalyzer.analizar(path, TipoFoto.FACHADA)
                val bloqueantes = analisis.problemas.filter { it in PROBLEMAS_FACHADA }
                if (bloqueantes.isNotEmpty()) {
                    _state.update { it.copy(analizando = false, rechazo = RechazoFachada(path, bloqueantes)) }
                    return@launch
                }
                val s = _state.value
                val u = (s.gps as? GpsUi.Listo)?.ubicacion
                container.sessionStore.iniciar(
                    Sesion(
                        id = UUID.randomUUID().toString(),
                        cadena = s.cadena ?: return@launch,
                        tienda = s.tienda.trim(),
                        lat = u?.lat,
                        lon = u?.lon,
                        precisionM = u?.precisionM,
                        fachadaPath = path,
                        inicioMillis = System.currentTimeMillis(),
                        fachadaAnalisis = analisis,
                    )
                )
                _state.update { it.copy(analizando = false) }
                onListo()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(analizando = false) }
            }
        }
    }

    companion object {
        /** Problemas que bloquean la fachada; el resto (texto, código, distancia) no aplica. */
        val PROBLEMAS_FACHADA = setOf(Problema.BORROSA, Problema.OSCURA, Problema.SOBREEXPUESTA)
    }
}
