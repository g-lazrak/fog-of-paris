package com.glazrak.fogofparis

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.glazrak.fogofparis.ui.MapScreen
import com.glazrak.fogofparis.ui.theme.FogOfParisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // La carte s'affiche sous les barres système. Le brouillard étant sombre,
        // on force des icônes claires dans ces barres, quel que soit le thème.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            FogOfParisTheme {
                // Pas de Scaffold ici : son padding rétrécissait la carte.
                MapScreen()
            }
        }
    }
}
