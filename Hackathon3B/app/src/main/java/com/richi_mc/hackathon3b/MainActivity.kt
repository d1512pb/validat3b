package com.richi_mc.hackathon3b

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.richi_mc.hackathon3b.app.NavGraph
import com.richi_mc.hackathon3b.ui.theme.GlassRojoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as Validat3BApp).container
        setContent {
            GlassRojoTheme {
                NavGraph(container)
            }
        }
    }
}
