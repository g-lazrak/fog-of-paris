package com.glazrak.fogofparis.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glazrak.fogofparis.R
import com.glazrak.fogofparis.ui.theme.Night

@Composable
fun SettingsScreen(viewModel: MapViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val nearbyAlerts by viewModel.nearbyAlerts.collectAsStateWithLifecycle()
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(Night.Background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back), tint = Night.Text)
            }
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, color = Night.Text)
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(start = 12.dp)) {
            // Toute la carte est cliquable, pas seulement l'interrupteur (plus facile au doigt).
            NightCard(
                modifier = Modifier.toggleable(
                    value = nearbyAlerts,
                    role = Role.Switch,
                    onValueChange = viewModel::setNearbyAlerts,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(R.drawable.ic_pin, Night.Teal)
                    Text(
                        stringResource(R.string.nearby_alerts_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Night.Text,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    Switch(
                        checked = nearbyAlerts,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(checkedTrackColor = Night.Gold, checkedThumbColor = Night.GoldInk),
                    )
                }
                Text(stringResource(R.string.nearby_alerts_text), style = MaterialTheme.typography.bodyMedium, color = Night.TextSoft)
            }
            SectionLabel(stringResource(R.string.about_title))
            Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodySmall, color = Night.TextMuted)
        }
    }
}
