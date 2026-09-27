package com.glazrak.fogofparis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glazrak.fogofparis.R
import com.glazrak.fogofparis.domain.CollectionProgress
import com.glazrak.fogofparis.domain.CollectionSet
import com.glazrak.fogofparis.domain.POINTS_PER_COMPLETED_SET
import com.glazrak.fogofparis.domain.Place
import com.glazrak.fogofparis.tracking.collectionNameRes
import com.glazrak.fogofparis.ui.theme.Night
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)

// Écran « Collections » : les séries de lieux à compléter.
@Composable
fun CollectionsScreen(viewModel: MapViewModel, onShowPlace: (Place) -> Unit, onHuntTreasure: (Place) -> Unit) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val recent by viewModel.recentDiscoveries.collectAsStateWithLifecycle()
    var openSet by rememberSaveable { mutableStateOf<CollectionSet?>(null) }
    if (collections.isEmpty()) return

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .explorerBackground()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
    ) {
        val visited = collections.sumOf { it.visitedCount }
        val complete = collections.count { it.isComplete }
        ScreenHeader(
            kicker = stringResource(R.string.kicker_collections),
            title = stringResource(R.string.collections_title),
            subtitle = pluralStringResource(R.plurals.collections_places, visited, visited, collections.sumOf { it.places.size }) +
                " · " + pluralStringResource(R.plurals.collections_complete_sets, complete, complete),
        )
        // Grille de 2 colonnes, construite à la main pour rester dans le défilement de l'écran.
        collections.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { collection ->
                    SetCard(
                        collection,
                        isOpen = openSet == collection.set,
                        onClick = { openSet = if (openSet == collection.set) null else collection.set },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            pair.firstOrNull { it.set == openSet }?.let { PlaceList(it, onShowPlace, onHuntTreasure) }
        }
        if (recent.isNotEmpty()) {
            SectionLabel(stringResource(R.string.recent_discoveries))
            Column {
                recent.forEach { (place, time) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clickable { onShowPlace(place) },
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(place.set.color))
                        Text(place.name, style = MaterialTheme.typography.bodyLarge, color = Night.Text, modifier = Modifier.weight(1f))
                        Text(relativeDay(time), style = MaterialTheme.typography.labelMedium, color = Night.TextMuted)
                    }
                    HorizontalDivider(color = Night.Border)
                }
            }
        }
    }
}

@Composable
private fun SetCard(collection: CollectionProgress, isOpen: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val set = collection.set
    // Série complète : tout passe à l'or, comme dans la maquette.
    val color = if (collection.isComplete) Night.Gold else set.color
    NightCard(
        modifier = modifier,
        highlight = if (collection.isComplete || isOpen) color else null,
        onClick = onClick,
        padding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(set.icon, color)
            Spacer(Modifier.weight(1f))
            Text(
                "${collection.visitedCount} / ${collection.places.size}",
                style = MaterialTheme.typography.labelLarge,
                color = color,
            )
        }
        Text(stringResource(collectionNameRes(set)), style = MaterialTheme.typography.titleMedium, color = Night.Text)
        if (collection.isComplete && !set.hidden) {
            Text(
                stringResource(R.string.set_complete_badge, POINTS_PER_COMPLETED_SET),
                style = MaterialTheme.typography.labelMedium,
                color = Night.Gold,
            )
        } else {
            NightProgressBar(collection.visitedCount.toFloat() / collection.places.size, color)
        }
    }
}

// Lieux de la série ouverte : visités d'abord, puis les autres ; un appui les montre sur la carte.
@Composable
private fun PlaceList(collection: CollectionProgress, onShowPlace: (Place) -> Unit, onHuntTreasure: (Place) -> Unit) {
    val color = if (collection.isComplete) Night.Gold else collection.set.color
    NightCard(padding = 8.dp) {
        collection.places
            .sortedWith(compareBy({ it.id !in collection.visitedIds }, { it.name }))
            .forEach { place ->
                val visited = place.id in collection.visitedIds
                // Trésor pas encore trouvé : ni nom ni position, seulement l'indice.
                val secret = place.set.hidden && !visited
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        // Trésor caché : un appui lance la chasse (si on est dans son quartier).
                        .clickable { if (secret) onHuntTreasure(place) else onShowPlace(place) }
                        .padding(horizontal = 8.dp, vertical = if (secret) 6.dp else 0.dp),
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (visited) color else Night.Started),
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            if (secret) stringResource(R.string.treasure_unknown) else place.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (visited) Night.Text else Night.TextMuted,
                        )
                        if (secret && place.hint != null) {
                            Text(place.hint, style = MaterialTheme.typography.bodySmall, color = Night.TextSoft)
                        }
                    }
                }
            }
    }
}

@Composable
private fun relativeDay(time: Long): String {
    val day = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (day) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> day.format(DAY_MONTH)
    }
}
