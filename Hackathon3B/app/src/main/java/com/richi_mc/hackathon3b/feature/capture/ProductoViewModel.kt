package com.richi_mc.hackathon3b.feature.capture

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.richi_mc.hackathon3b.app.AppContainer
import com.richi_mc.hackathon3b.core.model.BorradorCaptura
import com.richi_mc.hackathon3b.core.model.Sesion
import com.richi_mc.hackathon3b.core.model.TipoFoto
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProductoUiState(
    val sesion: Sesion? = null,
    /** Capturas ya hechas en la sesión (el encabezado muestra `capturadas + 1`). */
    val capturadas: Int = 0,
    val borrador: BorradorCaptura = BorradorCaptura(),
    /** Tipo de foto cuya cámara está abierta a pantalla completa; null = cerrada. */
    val camara: TipoFoto? = null,
    /** Tipo de foto que se está analizando; null = ninguno. */
    val analizando: TipoFoto? = null,
    val error: String? = null,
) {
    val numero get() = capturadas + 1

    /** Fotos tomadas de 3 (la fachada ya existe al estar en esta pantalla). */
    val pasosHechos: Int
        get() = (if (sesion != null) 1 else 0) +
            (if (borrador.producto != null) 1 else 0) +
            (if (borrador.etiqueta != null) 1 else 0)

    val puedeRevisar get() = borrador.producto != null && borrador.etiqueta != null && analizando == null
}

private data class LocalState(
    val camara: TipoFoto? = null,
    val analizando: TipoFoto? = null,
    val error: String? = null,
)

class ProductoViewModel(private val container: AppContainer) : ViewModel() {

    // Texto de los campos: estado de Compose (síncrono) para que el cursor no salte; se refleja en el borrador.
    var descripcion by mutableStateOf(container.borrador.borrador.value.productoEsperado.descripcion.orEmpty())
        private set
    var codigoBarras by mutableStateOf(container.borrador.borrador.value.productoEsperado.codigoBarras.orEmpty())
        private set

    private val local = MutableStateFlow(LocalState())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val capturadas = container.sessionStore.sesion.flatMapLatest { s ->
        if (s == null) flowOf(0) else container.capturaRepository.observarPorSesion(s.id).map { it.size }
    }

    val state: StateFlow<ProductoUiState> = combine(
        container.sessionStore.sesion, capturadas, container.borrador.borrador, local,
    ) { sesion, n, borrador, l ->
        ProductoUiState(sesion, n, borrador, l.camara, l.analizando, l.error)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        ProductoUiState(sesion = container.sessionStore.sesion.value, borrador = container.borrador.borrador.value),
    )

    fun cambiarDescripcion(t: String) {
        descripcion = t
        container.borrador.actualizar { b -> b.copy(productoEsperado = b.productoEsperado.copy(descripcion = t.ifBlank { null })) }
    }

    fun cambiarCodigoBarras(t: String) {
        val limpio = t.filter { it.isDigit() }
        codigoBarras = limpio
        container.borrador.actualizar { b -> b.copy(productoEsperado = b.productoEsperado.copy(codigoBarras = limpio.ifBlank { null })) }
    }

    fun cambiarContratipo(es: Boolean) {
        container.borrador.actualizar { b -> b.copy(productoEsperado = b.productoEsperado.copy(esContratipo = es)) }
    }

    fun abrirCamara(tipo: TipoFoto) = local.update { it.copy(camara = tipo, error = null) }
    fun cerrarCamara() = local.update { it.copy(camara = null) }

    /** Analiza la foto recién tomada y la guarda en el borrador; reemplaza (y borra) la anterior del mismo tipo. */
    fun onFoto(path: String, tipo: TipoFoto) {
        if (tipo == TipoFoto.FACHADA) return
        local.update { it.copy(camara = null, analizando = tipo, error = null) }
        viewModelScope.launch {
            try {
                val analisis = container.imageAnalyzer.analizar(path, tipo)
                val precio = if (tipo == TipoFoto.ETIQUETA) {
                    withContext(Dispatchers.Default) { container.priceParser.parse(analisis.textoOcr) }
                } else null
                val previo = container.borrador.borrador.value.let {
                    if (tipo == TipoFoto.PRODUCTO) it.producto else it.etiqueta
                }
                container.borrador.actualizar { b ->
                    if (tipo == TipoFoto.PRODUCTO) b.copy(producto = analisis)
                    else b.copy(etiqueta = analisis, precio = precio)
                }
                previo?.path?.takeIf { it != path }?.let { borrarArchivo(it) }
                // Flujo rápido: tras un producto válido, directo a la etiqueta si aún no hay.
                val siguiente = if (tipo == TipoFoto.PRODUCTO && analisis.esValida &&
                    container.borrador.borrador.value.etiqueta == null
                ) TipoFoto.ETIQUETA else null
                local.update { it.copy(analizando = null, camara = siguiente) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                borrarArchivo(path)
                local.update {
                    it.copy(analizando = null, error = "No se pudo analizar la foto. Vuelve a tomarla.")
                }
            }
        }
    }

    private suspend fun borrarArchivo(path: String) = withContext(Dispatchers.IO) {
        runCatching { File(path).delete() }
    }
}
