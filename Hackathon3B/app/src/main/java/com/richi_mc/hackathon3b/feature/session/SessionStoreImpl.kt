package com.richi_mc.hackathon3b.feature.session

import android.content.Context
import androidx.core.content.edit
import com.richi_mc.hackathon3b.core.model.AnalisisImagen
import com.richi_mc.hackathon3b.core.model.Cadena
import com.richi_mc.hackathon3b.core.model.Problema
import com.richi_mc.hackathon3b.core.model.Sesion
import com.richi_mc.hackathon3b.core.model.SessionStore
import com.richi_mc.hackathon3b.core.model.TipoFoto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * [SessionStore] en memoria (StateFlow) que además persiste la sesión activa en
 * SharedPreferences para sobrevivir a reinicios del proceso.
 */
class SessionStoreImpl(context: Context) : SessionStore {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _sesion = MutableStateFlow(leer())
    override val sesion: StateFlow<Sesion?> = _sesion

    override suspend fun iniciar(s: Sesion) {
        _sesion.value = s
        prefs.edit { putString(KEY, aJson(s).toString()) }
    }

    override suspend fun cerrar() {
        _sesion.value = null
        prefs.edit { remove(KEY) }
    }

    private fun leer(): Sesion? {
        val raw = prefs.getString(KEY, null) ?: return null
        return try {
            deJson(JSONObject(raw))
        } catch (e: Exception) {
            prefs.edit { remove(KEY) }
            null
        }
    }

    private companion object {
        const val PREFS = "sesion_activa"
        const val KEY = "sesion"

        fun aJson(s: Sesion) = JSONObject().apply {
            put("id", s.id)
            put("cadena", s.cadena.name)
            put("tienda", s.tienda)
            s.lat?.let { put("lat", it) }
            s.lon?.let { put("lon", it) }
            s.precisionM?.let { put("precisionM", it.toDouble()) }
            put("fachadaPath", s.fachadaPath)
            put("inicioMillis", s.inicioMillis)
            s.fachadaAnalisis?.let { put("fachadaAnalisis", aJson(it)) }
        }

        fun aJson(a: AnalisisImagen) = JSONObject().apply {
            put("tipo", a.tipo.name)
            put("path", a.path)
            put("nitidez", a.nitidez)
            put("brillo", a.brillo)
            put("textoOcr", a.textoOcr)
            a.confianzaOcr?.let { put("confianzaOcr", it.toDouble()) }
            a.codigoBarras?.let { put("codigoBarras", it) }
            a.formatoCodigo?.let { put("formatoCodigo", it) }
            a.areaCodigo?.let { put("areaCodigo", it) }
            put("problemas", JSONArray(a.problemas.map { it.name }))
        }

        fun deJson(o: JSONObject) = Sesion(
            id = o.getString("id"),
            cadena = Cadena.valueOf(o.getString("cadena")),
            tienda = o.getString("tienda"),
            lat = if (o.has("lat")) o.getDouble("lat") else null,
            lon = if (o.has("lon")) o.getDouble("lon") else null,
            precisionM = if (o.has("precisionM")) o.getDouble("precisionM").toFloat() else null,
            fachadaPath = o.getString("fachadaPath"),
            inicioMillis = o.getLong("inicioMillis"),
            fachadaAnalisis = o.optJSONObject("fachadaAnalisis")?.let { analisisDeJson(it) },
        )

        fun analisisDeJson(o: JSONObject): AnalisisImagen {
            val arr = o.optJSONArray("problemas") ?: JSONArray()
            val problemas = (0 until arr.length()).mapNotNull { i ->
                runCatching { Problema.valueOf(arr.getString(i)) }.getOrNull()
            }
            return AnalisisImagen(
                tipo = TipoFoto.valueOf(o.getString("tipo")),
                path = o.getString("path"),
                nitidez = o.getDouble("nitidez"),
                brillo = o.getDouble("brillo"),
                textoOcr = o.optString("textoOcr", ""),
                confianzaOcr = if (o.has("confianzaOcr")) o.getDouble("confianzaOcr").toFloat() else null,
                codigoBarras = if (o.has("codigoBarras")) o.getString("codigoBarras") else null,
                formatoCodigo = if (o.has("formatoCodigo")) o.getString("formatoCodigo") else null,
                areaCodigo = if (o.has("areaCodigo")) o.getDouble("areaCodigo") else null,
                problemas = problemas,
            )
        }
    }
}
