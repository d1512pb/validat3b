package com.richi_mc.hackathon3b.feature.capture

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.richi_mc.hackathon3b.app.AppContainer
import com.richi_mc.hackathon3b.core.model.BorradorCaptura
import com.richi_mc.hackathon3b.core.model.AnalisisImagen
import com.richi_mc.hackathon3b.core.model.Captura
import com.richi_mc.hackathon3b.core.model.CodigoEscaneado
import com.richi_mc.hackathon3b.core.model.PrecioDetectado
import com.richi_mc.hackathon3b.core.model.Sesion
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RevisionUiState(
    val sesion: Sesion? = null,
    val borrador: BorradorCaptura = BorradorCaptura(),
    val confirmando: Boolean = false,
    val error: String? = null,
    /** Campos de "Producto esperado" (el texto vive en el ViewModel como estado de Compose). */
    val campos: CamposEsperado = CamposEsperado(),
) {
    /**
     * Motivos (en lenguaje claro) por los que no se puede confirmar; vacío = se puede.
     * Misma regla que la pantalla de producto ([motivosBloqueoBorrador]).
     */
    val motivosBloqueo: List<String>
        get() = buildList {
            if (sesion == null) add("No hay una sucursal activa.")
            addAll(motivosBloqueoBorrador(borrador))
            if (campos.descripcion.isBlank()) add("Escribe la descripción del producto.")
        }

    /** Avisos que no impiden confirmar. */
    val avisos: List<String>
        get() = buildList {
            if (campos.precio == null) add("Sin precio: el servidor verá que no hay etiqueta de precio.")
        }

    val puedeConfirmar get() = motivosBloqueo.isEmpty() && !confirmando
}

/** Entradas de la sugerencia: cuando cambian, se recalcula (solo los campos no editados). */
private data class InsumosSugerencia(
    val producto: AnalisisImagen?,
    val etiqueta: AnalisisImagen?,
    val precio: PrecioDetectado?,
    val codigoEscaneado: CodigoEscaneado?,
)

class RevisionViewModel(private val container: AppContainer) : ViewModel() {
    /** Campos de "Producto esperado": estado de Compose (síncrono) para que el cursor no salte. */
    var campos by mutableStateOf(CamposEsperado.desde(container.borrador.borrador.value.productoEsperado))
        private set

    init {
        viewModelScope.launch {
            container.borrador.borrador
                .map { InsumosSugerencia(it.producto, it.etiqueta, it.precio, it.codigoEscaneado) }
                .distinctUntilChanged()
                .collect { recalcularSugerencia() }
        }
    }

    /** Llama al servicio con el borrador actual y actualiza solo los campos que el usuario no editó. */
    private fun recalcularSugerencia() {
        val b = container.borrador.borrador.value
        val producto = b.producto ?: return
        val etiqueta = b.etiqueta ?: return
        val precio = b.precio ?: etiqueta.precio ?: container.priceParser.parse(etiqueta.lineasOcr)
        val sugerencia = container.suggestionService.sugerir(
            producto.lineasOcr, etiqueta.lineasOcr, producto.etiquetas, precio, codigoFinal(b),
        )
        campos = campos.conSugerencia(sugerencia)
        guardarEnBorrador()
    }

    private fun guardarEnBorrador() {
        val c = campos
        container.borrador.actualizar { b -> b.copy(productoEsperado = c.aProductoEsperado(b.productoEsperado)) }
    }

    fun cambiarDescripcion(t: String) { campos = campos.conDescripcion(t); guardarEnBorrador() }
    fun cambiarPrecio(t: String) { campos = campos.conPrecio(t); guardarEnBorrador() }
    fun restaurarDescripcion() { campos = campos.restaurarDescripcion(); guardarEnBorrador() }
    fun restaurarPrecio() { campos = campos.restaurarPrecio(); guardarEnBorrador() }

    fun cambiarCodigoCatalogo(t: String) {
        campos = campos.copy(codigoCatalogo = t.filter { it.isDigit() })
        guardarEnBorrador()
    }

    fun cambiarContratipo(es: Boolean) { campos = campos.copy(esContratipo = es); guardarEnBorrador() }

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
        val s = state.value.copy(campos = campos)
        if (!s.puedeConfirmar) return
        val sesion = s.sesion ?: return
        val producto = s.borrador.producto ?: return
        val etiqueta = s.borrador.etiqueta ?: return
        local.value = Pair(true, null)
        viewModelScope.launch {
            try {
                val repo = container.capturaRepository
                val (esperado, sufijo) = separarSufijoForzado(s.campos.aProductoEsperado(s.borrador.productoEsperado))
                val precio = s.borrador.precio ?: etiqueta.precio ?: container.priceParser.parse(etiqueta.lineasOcr)
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
                    codigoEscaneado = s.borrador.codigoEscaneado,
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
