package com.glazrak.fogofparis.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glazrak.fogofparis.R
import com.glazrak.fogofparis.data.MenuTheme
import com.glazrak.fogofparis.ui.theme.Night

@Composable
fun SettingsScreen(viewModel: MapViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val nearbyAlerts by viewModel.nearbyAlerts.collectAsStateWithLifecycle()
    val mapLook by viewModel.mapLook.collectAsStateWithLifecycle()
    val isTracking by viewModel.isTracking.collectAsStateWithLifecycle()
    val menuTheme by viewModel.menuTheme.collectAsStateWithLifecycle()
    val onStartTracking = rememberTrackingStarter(viewModel)
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .explorerBackground()
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
            SettingSwitch(
                icon = R.drawable.ic_steps,
                color = Night.Gold,
                title = stringResource(R.string.tracking_setting_title),
                text = stringResource(R.string.tracking_setting_text),
                checked = isTracking,
                onChange = { on -> if (on) onStartTracking() else viewModel.stopTracking() },
            )
            SettingChoice(
                icon = R.drawable.ic_tab_map,
                color = Night.Teal,
                title = stringResource(R.string.map_style_title),
                options = MapLook.entries,
                selected = mapLook,
                label = { it.label },
                onSelect = viewModel::setMapLook,
            )
            SettingChoice(
                icon = R.drawable.ic_contrast,
                color = Night.Orchid,
                title = stringResource(R.string.menu_theme_title),
                options = MenuTheme.entries,
                selected = menuTheme,
                label = { it.label },
                onSelect = viewModel::setMenuTheme,
            )
            SettingSwitch(
                icon = R.drawable.ic_pin,
                color = Night.Teal,
                title = stringResource(R.string.nearby_alerts_title),
                text = stringResource(R.string.nearby_alerts_text),
                checked = nearbyAlerts,
                onChange = viewModel::setNearbyAlerts,
            )
            SectionLabel(stringResource(R.string.about_title))
            Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodySmall, color = Night.TextMuted)
        }
    }
}

// Un choix entre quelques options : boutons côte à côte, celui choisi en or.
@Composable
private fun <T> SettingChoice(
    @DrawableRes icon: Int,
    color: Color,
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    NightCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, color)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Night.Text,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val isSelected = option == selected
                val shape = RoundedCornerShape(12.dp)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(shape)
                        .background(if (isSelected) Night.Gold else Night.SurfaceHigh)
                        .border(1.dp, if (isSelected) Night.Gold else Night.Border, shape)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                ) {
                    Text(
                        stringResource(label(option)),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) Night.GoldInk else Night.TextSoft,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private val MapLook.label: Int
    get() = when (this) {
        MapLook.EXPLORER -> R.string.map_style_explorer
        MapLook.AERIAL -> R.string.map_style_aerial
    }

private val MenuTheme.label: Int
    get() = when (this) {
        MenuTheme.DARK -> R.string.menu_theme_dark
        MenuTheme.LIGHT -> R.string.menu_theme_light
        MenuTheme.SYSTEM -> R.string.menu_theme_system
    }

// Un réglage marche / arrêt. Toute la carte est cliquable, pas seulement
// l'interrupteur (plus facile au doigt).
@Composable
private fun SettingSwitch(
    @DrawableRes icon: Int,
    color: Color,
    title: String,
    text: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    NightCard(modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, color)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Night.Text,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = Night.Gold, checkedThumbColor = Night.GoldInk),
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Night.TextSoft)
    }
}
