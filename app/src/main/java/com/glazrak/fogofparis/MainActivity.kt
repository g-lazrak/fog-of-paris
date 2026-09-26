package com.glazrak.fogofparis

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glazrak.fogofparis.ui.MapScreen
import com.glazrak.fogofparis.ui.MapViewModel
import com.glazrak.fogofparis.ui.QuartiersScreen
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
                val viewModel: MapViewModel = viewModel()
                var showQuartiers by rememberSaveable { mutableStateOf(false) }
                // Icônes des barres système : claires sur la carte sombre, foncées
                // sur l'écran des quartiers quand il est clair (thème clair du téléphone).
                val darkTheme = isSystemInDarkTheme()
                LaunchedEffect(showQuartiers, darkTheme) {
                    val lightBackground = showQuartiers && !darkTheme
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = lightBackground
                        isAppearanceLightNavigationBars = lightBackground
                    }
                }
                // Pas de Scaffold : son padding rétrécissait la carte.
                Box {
                    // La carte reste chargée sous l'écran des quartiers : le retour
                    // est instantané et garde la position de la carte.
                    MapScreen(viewModel = viewModel, onOpenQuartiers = { showQuartiers = true })
                    if (showQuartiers) {
                        BackHandler { showQuartiers = false }
                        QuartiersScreen(
                            viewModel = viewModel,
                            onBack = { showQuartiers = false },
                            onQuartierSelected = { quartier ->
                                viewModel.showQuartier(quartier)
                                showQuartiers = false
                            },
                        )
                    }
                }
            }
        }
    }
}
