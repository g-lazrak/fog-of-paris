package com.glazrak.fogofparis.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glazrak.fogofparis.R
import com.glazrak.fogofparis.ui.theme.Night

// Onglets du bas, dans l'ordre de la maquette.
enum class AppTab(@StringRes val label: Int, @DrawableRes val icon: Int) {
    MAP(R.string.tab_map, R.drawable.ic_tab_map),
    PROGRESS(R.string.tab_progress, R.drawable.ic_tab_progress),
    QUARTIERS(R.string.tab_quartiers, R.drawable.ic_tab_quartiers),
    COLLECTIONS(R.string.tab_collections, R.drawable.ic_tab_collections),
}

// Hauteur de la barre d'onglets hors barre de navigation du système : la carte
// s'en sert pour garder ses boutons au-dessus.
val TAB_BAR_HEIGHT: Dp = 68.dp

@Composable
fun TabBar(selected: AppTab, onSelect: (AppTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Night.NavBar)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(TAB_BAR_HEIGHT),
    ) {
        AppTab.entries.forEach { tab ->
            val color = if (tab == selected) Night.Gold else Night.TextMuted
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(TAB_BAR_HEIGHT)
                    .clickable { onSelect(tab) },
            ) {
                Icon(painterResource(tab.icon), contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                Text(
                    stringResource(tab.label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (tab == selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = color,
                )
            }
        }
    }
}

// Carte sombre à bords arrondis, le bloc de base de tous les écrans.
@Composable
fun NightCard(
    modifier: Modifier = Modifier,
    highlight: Color? = null,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    // Une carte « mise en avant » (lieu, série complète) a un fond teinté et un bord coloré.
    val background = if (highlight != null) {
        Brush.linearGradient(listOf(highlight.copy(alpha = 0.16f), Night.Surface))
    } else {
        Brush.linearGradient(listOf(Night.Surface, Night.Surface))
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(1.dp, highlight?.copy(alpha = 0.6f) ?: Night.Border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

// Barre de progression arrondie ; la piste reprend la couleur en transparence.
@Composable
fun NightProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(color.copy(alpha = 0.18f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(color),
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Night.TextMuted,
        modifier = modifier,
    )
}

// Pastille d'icône carrée arrondie, teintée de sa couleur.
@Composable
fun IconBadge(@DrawableRes icon: Int, color: Color, size: Dp = 42.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(color.copy(alpha = 0.15f)),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(size * 0.57f))
    }
}

// En-tête d'écran : grand titre Fraunces et sous-titre gris.
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(verticalAlignment = Alignment.Bottom, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            if (kicker != null) Kicker(kicker)
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Night.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Night.TextMuted)
        }
        trailing()
    }
}

// Petit surtitre doré façon carnet de voyage (« ✦ CARNET D'EXPLORATEUR »).
@Composable
fun Kicker(text: String) {
    Text(
        "✦ " + text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Night.Gold,
        letterSpacing = 1.5.sp,
    )
}

// Petit carré de couleur + libellé, pour les légendes.
@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Night.TextSoft, modifier = Modifier.padding(start = 6.dp))
    }
}
