package com.richi_mc.hackathon3b.app

import com.richi_mc.hackathon3b.core.model.AnalisisImagen
import com.richi_mc.hackathon3b.core.model.Captura
import com.richi_mc.hackathon3b.core.model.CapturaRepository
import com.richi_mc.hackathon3b.core.model.EstadoEnvio
import com.richi_mc.hackathon3b.core.model.ImageAnalyzer
import com.richi_mc.hackathon3b.core.model.LocationProvider
import com.richi_mc.hackathon3b.core.model.PrecioDetectado
import com.richi_mc.hackathon3b.core.model.PriceParser
import com.richi_mc.hackathon3b.core.model.Problema
import com.richi_mc.hackathon3b.core.model.ResultadoServidor
import com.richi_mc.hackathon3b.core.model.Sesion
import com.richi_mc.hackathon3b.core.model.SessionStore
import com.richi_mc.hackathon3b.core.model.TipoFoto
import com.richi_mc.hackathon3b.core.model.TipoPrecio
import com.richi_mc.hackathon3b.core.model.Ubicacion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Implementaciones falsas con datos fijos, para que cada worker pruebe su pantalla aislado. */

class FakePriceParser : PriceParser {
    override fun parse(textoOcr: String) = PrecioDetectado(
        valor = 24.90, tipo = TipoPrecio.REGULAR, tienePrecioUnitario = true,
        textoFuente = "\$24.90", precioUnitario = 24.90
    )
}

class FakeImageAnalyzer(private val priceParser: PriceParser) : ImageAnalyzer {
    override suspend fun analizar(path: String, tipo: TipoFoto): AnalisisImagen {
        delay(400)
        val texto = when (tipo) {
            TipoFoto.PRODUCTO -> "LECHE ENTERA 1 L"
            TipoFoto.ETIQUETA -> "LECHE ENTERA 1L \$24.90 PRECIO POR LITRO \$24.90"
            TipoFoto.FACHADA -> "TIENDAS 3B"
        }
        return AnalisisImagen(
            tipo = tipo, path = path, nitidez = 250.0, brillo = 130.0,
            textoOcr = texto, confianzaOcr = 0.9f,
            codigoBarras = if (tipo == TipoFoto.PRODUCTO) "7501055300075" else null,
            formatoCodigo = if (tipo == TipoFoto.PRODUCTO) "EAN_13" else null,
            areaCodigo = if (tipo == TipoFoto.PRODUCTO) 0.05 else null,
            problemas = emptyList<Problema>(),
        )
    }
}

class FakeSessionStore : SessionStore {
    private val _s = MutableStateFlow<Sesion?>(null)
    override val sesion: StateFlow<Sesion?> = _s
    override suspend fun iniciar(s: Sesion) { _s.value = s }
    override suspend fun cerrar() { _s.value = null }
}

class FakeLocationProvider : LocationProvider {
    override suspend fun ubicacionActual() = Ubicacion(19.5012, -99.1301, 12f)
}

class FakeCapturaRepository : CapturaRepository {
    private val capturas = MutableStateFlow<Map<String, Captura>>(emptyMap())
    private var consecutivo = 0

    override suspend fun guardar(c: Captura) = capturas.update { it + (c.captureId to c) }
    override suspend fun pendientes() = capturas.value.values.filter { it.estado != EstadoEnvio.ENVIADA }
    override suspend fun enviar(c: Captura): Result<ResultadoServidor> {
        delay(600)
        val r = ResultadoServidor(
            decision = "aprobada", confidenceScore = 0.92, reasonCode = null,
            explicacion = "Respuesta simulada (fake).", capaDecisiva = "simulado"
        )
        guardar(c.copy(estado = EstadoEnvio.ENVIADA, respuesta = r))
        return Result.success(r)
    }
    override suspend fun enviarPendientes(): Int {
        val p = pendientes(); p.forEach { enviar(it) }; return p.size
    }
    override fun observarPendientes(): Flow<Int> =
        capturas.map { m -> m.values.count { it.estado != EstadoEnvio.ENVIADA } }
    override fun observarCaptura(captureId: String): Flow<Captura?> = capturas.map { it[captureId] }
    override fun observarPorSesion(sesionId: String): Flow<List<Captura>> =
        capturas.map { m -> m.values.filter { it.sesionId == sesionId }.sortedBy { it.timestampMillis } }
    override suspend fun siguienteConsecutivo() = ++consecutivo
    override suspend fun exportarJson(sesionId: String) = ""
    override suspend fun probarServidor() = Result.success("Servidor simulado (fake)")
}
