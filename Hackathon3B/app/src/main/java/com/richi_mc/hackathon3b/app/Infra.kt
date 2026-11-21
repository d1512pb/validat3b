package com.richi_mc.hackathon3b.app

import android.content.Context
import androidx.core.content.edit
import com.richi_mc.hackathon3b.BuildConfig
import com.richi_mc.hackathon3b.core.model.AjustesServidor
import com.richi_mc.hackathon3b.core.model.BorradorCaptura
import com.richi_mc.hackathon3b.core.model.BorradorStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class InMemoryBorradorStore : BorradorStore {
    private val _b = MutableStateFlow(BorradorCaptura())
    override val borrador: StateFlow<BorradorCaptura> = _b
    override fun actualizar(cambio: (BorradorCaptura) -> BorradorCaptura) = _b.update(cambio)
    override fun limpiar() { _b.value = BorradorCaptura() }
}

class PrefsAjustesServidor(context: Context) : AjustesServidor {
    private val prefs = context.getSharedPreferences("ajustes_servidor", Context.MODE_PRIVATE)
    private val _url = MutableStateFlow(prefs.getString("url", null) ?: BuildConfig.SERVIDOR_URL)
    private val _simulado = MutableStateFlow(prefs.getBoolean("simulado", BuildConfig.DEBUG)) // simulado en debug, real en release
    override val url: StateFlow<String> = _url
    override val modoSimulado: StateFlow<Boolean> = _simulado
    override fun setUrl(url: String) {
        val u = if (url.endsWith("/")) url else "$url/"
        _url.value = u; prefs.edit { putString("url", u) }
    }
    override fun setModoSimulado(simulado: Boolean) {
        _simulado.value = simulado; prefs.edit { putBoolean("simulado", simulado) }
    }
}
