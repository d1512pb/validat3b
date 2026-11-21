package com.richi_mc.hackathon3b.feature.capture

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Scope de aplicación para los envíos al servidor.
 *
 * Decisión: `CoroutineScope(SupervisorJob() + Dispatchers.Default)` propio en lugar de `GlobalScope`
 * (que exige @OptIn y no tiene Job) o `MainScope` (que corre en Main). El envío debe sobrevivir a la
 * navegación Revisión → Confirmación, donde el ViewModel de Revisión se destruye; con SupervisorJob un
 * fallo de un envío no cancela los siguientes. Vive tanto como el proceso: si el proceso muere, la
 * captura ya está guardada como PENDIENTE y se reenvía desde `enviarPendientes()`.
 */
internal object EnvioScope {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
