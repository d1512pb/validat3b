package com.richi_mc.hackathon3b.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.richi_mc.hackathon3b.app.AppContainer
import com.richi_mc.hackathon3b.core.model.Captura
import com.richi_mc.hackathon3b.core.model.EstadoEnvio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResumenSesion(val capturadas: Int = 0, val enviadas: Int = 0, val pendientes: Int = 0)

data class ConfirmacionUiState(
    val captura: Captura? = null,
    val resumen: ResumenSesion = ResumenSesion(),
    val exportando: Boolean = false,
    val errorExportar: String? = null,
    val reintentando: Boolean = false,
)

class ConfirmacionViewModel(
    private val container: AppContainer,
    private val captureId: String,
) : ViewModel() {

    private val local = MutableStateFlow(Pair(false, null as String?))
    private val reintentando = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val resumen = container.capturaRepository.observarCaptura(captureId)
        .map { it?.sesionId }
        .distinctUntilChanged()
        .flatMapLatest { sid ->
            if (sid == null) flowOf(ResumenSesion())
            else container.capturaRepository.observarPorSesion(sid).map { lista ->
                val enviadas = lista.count { it.estado == EstadoEnvio.ENVIADA }
                ResumenSesion(lista.size, enviadas, lista.size - enviadas)
            }
        }

    val state: StateFlow<ConfirmacionUiState> = combine(
        container.capturaRepository.observarCaptura(captureId), resumen, local, reintentando,
    ) { captura, resumen, (exportando, error), reint ->
        ConfirmacionUiState(captura, resumen, exportando, error, reint)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConfirmacionUiState())

    /** Exporta el JSON de la sesión; llama [onRuta] con la ruta si se generó un archivo. */
    fun exportar(onRuta: (String) -> Unit) {
        val sesionId = state.value.captura?.sesionId ?: return
        if (state.value.exportando) return
        local.value = Pair(true, null)
        viewModelScope.launch {
            try {
                val ruta = container.capturaRepository.exportarJson(sesionId)
                if (ruta.isBlank()) {
                    local.value = Pair(false, "No se generó ningún archivo para compartir.")
                } else {
                    local.value = Pair(false, null)
                    onRuta(ruta)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { Pair(false, "No se pudo exportar el JSON. Inténtalo de nuevo.") }
            }
        }
    }

    /** Reenvía la captura (estado ERROR o FALLIDA); ignora taps mientras hay un reintento en curso. */
    fun reintentarEnvio() {
        val captura = state.value.captura ?: return
        if (!reintentando.compareAndSet(expect = false, update = true)) return
        // EnvioScope sobrevive a la pantalla: el envío no se corta si el usuario sale.
        EnvioScope.scope.launch {
            try {
                container.capturaRepository.enviar(captura)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // El resultado queda en Room y la pantalla lo observa; no hay nada más que hacer.
            } finally {
                reintentando.value = false
            }
        }
    }
}
