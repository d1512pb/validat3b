package com.richi_mc.hackathon3b.feature.session

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.richi_mc.hackathon3b.core.model.LocationProvider
import com.richi_mc.hackathon3b.core.model.Ubicacion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** [LocationProvider] sobre Fused Location (alta precisión, timeout ~10 s). */
class FusedLocationProvider(private val context: Context) : LocationProvider {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    private fun tienePermiso(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission")
    override suspend fun ubicacionActual(): Ubicacion? {
        if (!tienePermiso()) return null
        val cts = CancellationTokenSource()
        return try {
            val loc = withTimeoutOrNull(TIMEOUT_MS) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
            } ?: return null
            Ubicacion(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            cts.cancel()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
