package com.richi_mc.hackathon3b.app

import android.content.Context
import com.richi_mc.hackathon3b.core.model.AjustesServidor
import com.richi_mc.hackathon3b.core.model.BorradorStore
import com.richi_mc.hackathon3b.core.model.CapturaRepository
import com.richi_mc.hackathon3b.core.model.ImageAnalyzer
import com.richi_mc.hackathon3b.core.model.ImageLabelerService
import com.richi_mc.hackathon3b.core.model.SuggestionService
import com.richi_mc.hackathon3b.core.model.LocationProvider
import com.richi_mc.hackathon3b.core.model.PriceParser
import com.richi_mc.hackathon3b.core.model.OcrEnhancer
import com.richi_mc.hackathon3b.core.model.SessionStore
import com.richi_mc.hackathon3b.core.data.CapturaRepositoryImpl
import com.richi_mc.hackathon3b.core.data.Validat3BDatabase
import com.richi_mc.hackathon3b.feature.analysis.MlKitImageAnalyzer
import com.richi_mc.hackathon3b.feature.analysis.enhance.MlKitOcrEnhancer
import com.richi_mc.hackathon3b.feature.analysis.price.ScoringPriceParser
import com.richi_mc.hackathon3b.feature.labeling.MlKitLabeler
import com.richi_mc.hackathon3b.feature.labeling.TraduccionEtiquetas
import com.richi_mc.hackathon3b.feature.suggestion.ReglasSuggestionService
import com.richi_mc.hackathon3b.feature.session.FusedLocationProvider
import com.richi_mc.hackathon3b.feature.session.SessionStoreImpl

/** Inyección manual (sin Hilt). Dueño: orquestador. */
interface AppContainer {
    val appContext: Context
    val imageAnalyzer: ImageAnalyzer
    val imageLabeler: ImageLabelerService
    val suggestionService: SuggestionService
    val priceParser: PriceParser
    /** Re-OCR del recorte de la zona del precio (PLAN3 §5). */
    val ocrEnhancer: OcrEnhancer
    val capturaRepository: CapturaRepository
    val sessionStore: SessionStore
    val locationProvider: LocationProvider
    val ajustes: AjustesServidor
    val borrador: BorradorStore
}

class DefaultAppContainer(override val appContext: Context) : AppContainer {
    override val priceParser: PriceParser by lazy { ScoringPriceParser() }
    override val ocrEnhancer: OcrEnhancer by lazy { MlKitOcrEnhancer(appContext) }
    private val mlKitLabeler by lazy { MlKitLabeler(appContext) }
    override val imageLabeler: ImageLabelerService get() = mlKitLabeler
    override val imageAnalyzer: ImageAnalyzer by lazy { MlKitImageAnalyzer(appContext, priceParser, mlKitLabeler, ocrEnhancer) }
    override val suggestionService: SuggestionService by lazy { ReglasSuggestionService(traducir = TraduccionEtiquetas::es) }
    override val capturaRepository: CapturaRepository by lazy {
        CapturaRepositoryImpl(appContext, Validat3BDatabase.obtener(appContext), sessionStore, ajustes)
    }
    override val sessionStore: SessionStore by lazy { SessionStoreImpl(appContext) }
    override val locationProvider: LocationProvider by lazy { FusedLocationProvider(appContext) }
    override val ajustes: AjustesServidor by lazy { PrefsAjustesServidor(appContext) }
    override val borrador: BorradorStore by lazy { InMemoryBorradorStore() }
}
