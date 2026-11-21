package com.richi_mc.hackathon3b.feature.capture

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.richi_mc.hackathon3b.app.AppContainer
import com.richi_mc.hackathon3b.core.model.BorradorCaptura
import com.richi_mc.hackathon3b.core.model.CodigoEscaneado
import com.richi_mc.hackathon3b.core.model.Sesion
import com.richi_mc.hackathon3b.core.model.TipoFoto
import com.richi_mc.hackathon3b.feature.camera.ImageUtils
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
    /** Escáner de código de barras abierto a pantalla completa. */
    val escaner: Boolean = false,
    /** Tipo de foto que se está analizando (o importando); null = ninguno. */
    val analizando: TipoFoto? = null,
    val error: String? = null,
) {
    val numero get() = capturadas + 1

    /** El código está resuelto: escaneado válido o leído en la foto del producto. */
    val codigoListo: Boolean
        get() = borrador.codigoEscaneado?.checksumOk == true ||
            (borrador.producto != null && !borrador.requiereEscaneo)

    /** Pasos hechos de [TOTAL_PASOS] (la fachada ya existe al estar en esta pantalla). */
    val pasosHechos: Int
        get() = (if (sesion != null) 1 else 0) +
            (if (borrador.producto != null) 1 else 0) +
            (if (codigoListo) 1 else 0) +
            (if (borrador.etiqueta != null) 1 else 0)

    /** Motivos por los que aún no se puede revisar (vacío = se puede). */
    val motivosBloqueo: List<String> get() = motivosBloqueoBorrador(borrador)

    val puedeRevisar get() = motivosBloqueo.isEmpty() && analizando == null

    companion object {
        const val TOTAL_PASOS = 4
    }
}

/**
 * Regla única de bloqueo (Producto y Revisión): faltan fotos, hay problemas bloqueantes efectivos
 * (los de código de la foto del producto no cuentan con un escaneo válido) o falta el escaneo
 * obligatorio cuando la foto no mostró un código válido.
 */
internal fun motivosBloqueoBorrador(b: BorradorCaptura): List<String> = buildList {
    val producto = b.producto
    val etiqueta = b.etiqueta
    if (producto == null) add("Falta la foto del producto.")
    else {
        val bloqueantes = b.problemasProducto.filter { it.bloqueante }
        if (bloqueantes.isNotEmpty()) add(
            "La foto del producto tiene problemas (" + bloqueantes.joinToString { it.corto } + ")."
        )
        if (b.requiereEscaneo && b.codigoEscaneado?.checksumOk != true) add(
            if (b.codigoEscaneado == null) "Falta escanear el código de barras."
            else "El código escaneado no es válido: vuelve a escanearlo."
        )
    }
    if (etiqueta == null) add("Falta la foto de la etiqueta.")
    else if (etiqueta.tieneBloqueantes) add(
        "La foto de la etiqueta tiene problemas (" +
            etiqueta.problemas.filter { it.bloqueante }.joinToString { it.corto } + ")."
    )
}

private data class LocalState(
    val camara: TipoFoto? = null,
    val escaner: Boolean = false,
    val analizando: TipoFoto? = null,
    val error: String? = null,
)

class ProductoViewModel(private val container: AppContainer) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val capturadas = container.sessionStore.sesion.flatMapLatest { s ->
        if (s == null) flowOf(0) else container.capturaRepository.observarPorSesion(s.id).map { it.size }
    }

    val state: StateFlow<ProductoUiState> = combine(
        container.sessionStore.sesion, capturadas, container.borrador.borrador, local,
    ) { sesion, n, borrador, l ->
        ProductoUiState(sesion, n, borrador, l.camara, l.escaner, l.analizando, l.error)
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        ProductoUiState(sesion = container.sessionStore.sesion.value, borrador = container.borrador.borrador.value),
    )

    fun abrirCamara(tipo: TipoFoto) = local.update { it.copy(camara = tipo, escaner = false, error = null) }
    fun cerrarCamara() = local.update { it.copy(camara = null) }

    fun abrirEscaner() = local.update { it.copy(escaner = true, camara = null, error = null) }
    fun cerrarEscaner() = local.update { it.copy(escaner = false) }

    /** Guarda el código leído en vivo; con un código válido y sin etiqueta aún, sigue con la cámara de etiqueta. */
    fun onCodigoEscaneado(codigo: CodigoEscaneado) {
        container.borrador.actualizar { b -> b.copy(codigoEscaneado = codigo) }
        val b = container.borrador.borrador.value
        val siguiente = if (codigo.checksumOk && b.producto != null && b.etiqueta == null) TipoFoto.ETIQUETA else null
        local.update { it.copy(escaner = false, camara = siguiente) }
    }

    /** Analiza la foto recién tomada y la guarda en el borrador; reemplaza (y borra) la anterior del mismo tipo. */
    fun onFoto(path: String, tipo: TipoFoto) {
        if (tipo == TipoFoto.FACHADA) return
        local.update { it.copy(camara = null, escaner = false, analizando = tipo, error = null) }
        viewModelScope.launch { procesarFoto(path, tipo) }
    }

    /**
     * Importa una imagen de la galería y sigue el mismo camino que una foto de cámara.
     * Un fallo al importar deja un mensaje y no toca el borrador.
     */
    fun onImagenElegida(context: Context, uri: Uri, tipo: TipoFoto) {
        if (tipo == TipoFoto.FACHADA) return
        local.update { it.copy(camara = null, escaner = false, analizando = tipo, error = null) }
        val app = context.applicationContext
        viewModelScope.launch {
            val path = try {
                withContext(Dispatchers.IO) {
                    ImageUtils.importarDesdeUri(app, uri, File(app.filesDir, "capturas")).absolutePath
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(analizando = null, error = "No se pudo importar la imagen. Elige otra o toma la foto.") }
                return@launch
            }
            procesarFoto(path, tipo)
        }
    }

    private suspend fun procesarFoto(path: String, tipo: TipoFoto) {
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
            // Flujo rápido: producto sin código válido → escáner; producto sin problemas → etiqueta.
            val b = container.borrador.borrador.value
            var escaner = false
            var siguiente: TipoFoto? = null
            if (tipo == TipoFoto.PRODUCTO) {
                if (b.requiereEscaneo && b.codigoEscaneado?.checksumOk != true) escaner = true
                else if (b.problemasProducto.isEmpty() && b.etiqueta == null) siguiente = TipoFoto.ETIQUETA
            }
            local.update { it.copy(analizando = null, camara = siguiente, escaner = escaner) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            borrarArchivo(path)
            local.update {
                it.copy(analizando = null, error = "No se pudo analizar la foto. Vuelve a tomarla.")
            }
        }
    }

    private suspend fun borrarArchivo(path: String) = withContext(Dispatchers.IO) {
        runCatching { File(path).delete() }
    }
}
