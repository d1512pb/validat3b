package com.richi_mc.hackathon3b.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.richi_mc.hackathon3b.app.AppContainer
import com.richi_mc.hackathon3b.core.model.BorradorCaptura
import com.richi_mc.hackathon3b.core.model.Captura
import com.richi_mc.hackathon3b.core.model.Sesion
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RevisionUiState(
    val sesion: Sesion? = null,
    val borrador: BorradorCaptura = BorradorCaptura(),
    val confirmando: Boolean = false,
    val error: String? = null,
) {
    /** Motivos (en lenguaje claro) por los que no se puede confirmar; vacío = se puede. */
    val motivosBloqueo: List<String>
        get() = buildList {
            if (sesion == null) add("No hay una sucursal activa.")
            if (borrador.producto == null) add("Falta la foto del producto.")
            else if (borrador.producto.tieneBloqueantes) add(
                "La foto del producto tiene problemas (" +
                    borrador.producto.problemas.filter { it.bloqueante }.joinToString { it.corto } + ")."
            )
            if (borrador.etiqueta == null) add("Falta la foto de la etiqueta.")
            else if (borrador.etiqueta.tieneBloqueantes) add(
                "La foto de la etiqueta tiene problemas (" +
                    borrador.etiqueta.problemas.filter { it.bloqueante }.joinToString { it.corto } + ")."
            )
        }

    val puedeConfirmar get() = motivosBloqueo.isEmpty() && !confirmando
}

class RevisionViewModel(private val container: AppContainer) : ViewModel() {
    private val local = MutableStateFlow(Pair(false, null as String?))

    val state: StateFlow<RevisionUiState> = combine(
        container.sessionStore.sesion, container.borrador.borrador, local,
    ) { sesion, borrador, (confirmando, error) ->
        RevisionUiState(sesion, borrador, confirmando, error)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        RevisionUiState(sesion = container.sessionStore.sesion.value, borrador = container.borrador.borrador.value),
    )

    /**
     * Guarda la captura, lanza el envío en [EnvioScope] (sobrevive a la navegación) y navega de inmediato.
     * El resultado del envío se refleja en Confirmación vía `observarCaptura`.
     */
    fun confirmar(onConfirmada: (captureId: String) -> Unit) {
        val s = state.value
        if (!s.puedeConfirmar) return
        val sesion = s.sesion ?: return
        val producto = s.borrador.producto ?: return
        val etiqueta = s.borrador.etiqueta ?: return
        local.value = Pair(true, null)
        viewModelScope.launch {
            try {
                val repo = container.capturaRepository
                val (esperado, sufijo) = separarSufijoForzado(s.borrador.productoEsperado)
                val precio = s.borrador.precio ?: container.priceParser.parse(etiqueta.textoOcr)
                val fecha = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                val consecutivo = String.format("%04d", repo.siguienteConsecutivo())
                val id = "CAP-${sesion.cadena.codigo}-$fecha-$consecutivo${sufijo.orEmpty()}"
                val captura = Captura(
                    captureId = id,
                    sesionId = sesion.id,
                    productoEsperado = esperado,
                    producto = producto,
                    etiqueta = etiqueta,
                    precio = precio,
                    timestampMillis = System.currentTimeMillis(),
                )
                repo.guardar(captura)
                lanzarEnvio(captura)
                local.value = Pair(false, null)
                onConfirmada(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.value = Pair(false, "No se pudo guardar la captura. Inténtalo de nuevo.")
            }
        }
    }

    private fun lanzarEnvio(captura: Captura) {
        val repo = container.capturaRepository
        EnvioScope.scope.launch {
            // El repositorio actualiza estado/respuesta/error en la base; aquí solo evitamos que una excepción escape.
            try {
                repo.enviar(captura)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        /**
         * Demo del modo simulado: si la descripción esperada termina en " -APR", " -REC" o " -REV",
         * se quita del texto y se devuelve como sufijo del capture_id (el servidor fuerza la decisión).
         */
        internal fun separarSufijoForzado(e: com.richi_mc.hackathon3b.core.model.ProductoEsperado):
            Pair<com.richi_mc.hackathon3b.core.model.ProductoEsperado, String?> {
            val d = e.descripcion?.trimEnd() ?: return e to null
            val suf = listOf("-APR", "-REC", "-REV").firstOrNull { d.endsWith(" $it") || d == it } ?: return e to null
            val limpia = d.removeSuffix(suf).trim().ifBlank { null }
            return e.copy(descripcion = limpia) to suf
        }
    }
}
