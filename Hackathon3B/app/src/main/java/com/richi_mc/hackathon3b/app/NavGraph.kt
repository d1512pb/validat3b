package com.richi_mc.hackathon3b.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.richi_mc.hackathon3b.feature.capture.ConfirmacionScreen
import com.richi_mc.hackathon3b.feature.capture.ProductoScreen
import com.richi_mc.hackathon3b.feature.capture.RevisionScreen
import com.richi_mc.hackathon3b.feature.session.InicioScreen
import com.richi_mc.hackathon3b.feature.session.SesionScreen
import kotlinx.coroutines.launch

object Rutas {
    const val INICIO = "inicio"
    const val SESION = "sesion"
    const val PRODUCTO = "producto"
    const val REVISION = "revision"
    const val CONFIRMACION = "confirmacion/{captureId}"
    fun confirmacion(id: String) = "confirmacion/$id"
}

/** Flujo: inicio → sesion → producto → revision → confirmacion → (producto | sesion | inicio). */
@Composable
fun NavGraph(container: AppContainer, nav: NavHostController = rememberNavController()) {
    val scope = rememberCoroutineScope()
    NavHost(nav, startDestination = Rutas.INICIO) {
        composable(Rutas.INICIO) {
            InicioScreen(
                container = container,
                onIniciarCaptura = { nav.navigate(Rutas.SESION) },
                onContinuar = {
                    container.borrador.limpiar()
                    nav.navigate(Rutas.PRODUCTO)
                },
            )
        }
        composable(Rutas.SESION) {
            SesionScreen(
                container = container,
                onSesionLista = {
                    container.borrador.limpiar()
                    nav.navigate(Rutas.PRODUCTO) { popUpTo(Rutas.INICIO) }
                },
                onAtras = { nav.popBackStack() },
            )
        }
        composable(Rutas.PRODUCTO) {
            ProductoScreen(
                container = container,
                onRevisar = { nav.navigate(Rutas.REVISION) },
                onAtras = { nav.popBackStack(Rutas.INICIO, inclusive = false) },
            )
        }
        composable(Rutas.REVISION) {
            RevisionScreen(
                container = container,
                onConfirmada = { id ->
                    container.borrador.limpiar()
                    nav.navigate(Rutas.confirmacion(id)) { popUpTo(Rutas.INICIO) }
                },
                onRetomar = { nav.popBackStack() },
            )
        }
        composable(
            Rutas.CONFIRMACION,
            arguments = listOf(navArgument("captureId") { type = NavType.StringType }),
        ) { entry ->
            ConfirmacionScreen(
                container = container,
                captureId = entry.arguments?.getString("captureId").orEmpty(),
                onSiguienteProducto = {
                    container.borrador.limpiar()
                    nav.navigate(Rutas.PRODUCTO) { popUpTo(Rutas.INICIO) }
                },
                onCambiarSucursal = {
                    scope.launch { container.sessionStore.cerrar() }
                    nav.navigate(Rutas.SESION) { popUpTo(Rutas.INICIO) }
                },
                onInicio = { nav.popBackStack(Rutas.INICIO, inclusive = false) },
            )
        }
    }
}
