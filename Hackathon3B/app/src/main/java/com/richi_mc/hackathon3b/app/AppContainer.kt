package com.richi_mc.hackathon3b.app

import android.content.Context
import com.richi_mc.hackathon3b.core.model.AjustesServidor
import com.richi_mc.hackathon3b.core.model.BorradorStore
import com.richi_mc.hackathon3b.core.model.CapturaRepository
import com.richi_mc.hackathon3b.core.model.ImageAnalyzer
import com.richi_mc.hackathon3b.core.model.LocationProvider
import com.richi_mc.hackathon3b.core.model.PriceParser
import com.richi_mc.hackathon3b.core.model.SessionStore
import com.richi_mc.hackathon3b.core.data.CapturaRepositoryImpl
import com.richi_mc.hackathon3b.core.data.Validat3BDatabase
import com.richi_mc.hackathon3b.feature.analysis.MlKitImageAnalyzer
import com.richi_mc.hackathon3b.feature.analysis.RegexPriceParser
import com.richi_mc.hackathon3b.feature.session.FusedLocationProvider
import com.richi_mc.hackathon3b.feature.session.SessionStoreImpl

/** Inyección manual (sin Hilt). Dueño: orquestador. */
interface AppContainer {
    val appContext: Context
    val imageAnalyzer: ImageAnalyzer
    val priceParser: PriceParser
    val capturaRepository: CapturaRepository
    val sessionStore: SessionStore
    val locationProvider: LocationProvider
    val ajustes: AjustesServidor
    val borrador: BorradorStore
}

class DefaultAppContainer(override val appContext: Context) : AppContainer {
    override val priceParser: PriceParser by lazy { RegexPriceParser() }
    override val imageAnalyzer: ImageAnalyzer by lazy { MlKitImageAnalyzer(appContext, priceParser) }
    override val capturaRepository: CapturaRepository by lazy {
        CapturaRepositoryImpl(appContext, Validat3BDatabase.obtener(appContext), sessionStore, ajustes)
    }
    override val sessionStore: SessionStore by lazy { SessionStoreImpl(appContext) }
    override val locationProvider: LocationProvider by lazy { FusedLocationProvider(appContext) }
    override val ajustes: AjustesServidor by lazy { PrefsAjustesServidor(appContext) }
    override val borrador: BorradorStore by lazy { InMemoryBorradorStore() }
}
