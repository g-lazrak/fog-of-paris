package com.glazrak.fogofparis

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glazrak.fogofparis.ui.AppTab
import com.glazrak.fogofparis.ui.CelebrationScreen
import com.glazrak.fogofparis.ui.CollectionsScreen
import com.glazrak.fogofparis.ui.MapScreen
import com.glazrak.fogofparis.ui.MapViewModel
import com.glazrak.fogofparis.ui.ProgressScreen
import com.glazrak.fogofparis.ui.QuartiersScreen
import com.glazrak.fogofparis.ui.SettingsScreen
import com.glazrak.fogofparis.ui.TAB_BAR_HEIGHT
import com.glazrak.fogofparis.ui.TabBar
import com.glazrak.fogofparis.ui.theme.FogOfParisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Tout est sombre (carte, brouillard, menus) : icônes claires dans les
        // barres système, en permanence.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            FogOfParisTheme {
                val viewModel: MapViewModel = viewModel()
                var tab by rememberSaveable { mutableStateOf(AppTab.MAP) }
                var showSettings by rememberSaveable { mutableStateOf(false) }
                val levelToCelebrate by viewModel.levelToCelebrate.collectAsStateWithLifecycle()

                // Pas de Scaffold : son padding rétrécissait la carte.
                Box(modifier = Modifier.fillMaxSize()) {
                    // La carte reste chargée sous les autres onglets : y revenir est
                    // instantané et garde sa position.
                    MapScreen(
                        viewModel = viewModel,
                        onOpenProgress = { tab = AppTab.PROGRESS },
                        bottomBarHeight = TAB_BAR_HEIGHT,
                    )
                    if (tab != AppTab.MAP) {
                        // « Retour » depuis un onglet ramène à la carte, comme dans la plupart des apps.
                        BackHandler { tab = AppTab.MAP }
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) {
                                when (tab) {
                                    AppTab.PROGRESS -> ProgressScreen(
                                        viewModel = viewModel,
                                        onOpenSettings = { showSettings = true },
                                        onShowDay = { day ->
                                            viewModel.showDay(day)
                                            tab = AppTab.MAP
                                        },
                                        onShowPlace = { direction ->
                                            viewModel.showPlace(direction.place)
                                            tab = AppTab.MAP
                                        },
                                    )
                                    AppTab.QUARTIERS -> QuartiersScreen(
                                        viewModel = viewModel,
                                        onShowQuartier = { quartier ->
                                            viewModel.showQuartier(quartier)
                                            tab = AppTab.MAP
                                        },
                                    )
                                    AppTab.COLLECTIONS -> CollectionsScreen(
                                        viewModel = viewModel,
                                        onShowPlace = { place ->
                                            viewModel.showPlace(place)
                                            tab = AppTab.MAP
                                        },
                                    )
                                    AppTab.MAP -> Unit
                                }
                            }
                            // Réserve la place de la barre d'onglets sous l'écran.
                            TabBarSpacer()
                        }
                    }
                    TabBar(
                        selected = tab,
                        onSelect = { tab = it },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                    if (showSettings) {
                        SettingsScreen(viewModel = viewModel, onClose = { showSettings = false })
                    }
                    levelToCelebrate?.let { level ->
                        CelebrationScreen(level = level, onContinue = { viewModel.dismissCelebration(level) })
                    }
                }
            }
        }
    }
}

// Espace vide de la hauteur de la barre d'onglets (barre système comprise),
// pour que le bas des écrans ne passe pas dessous.
@Composable
private fun TabBarSpacer() {
    Spacer(Modifier.fillMaxWidth().height(TAB_BAR_HEIGHT))
    Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
}
