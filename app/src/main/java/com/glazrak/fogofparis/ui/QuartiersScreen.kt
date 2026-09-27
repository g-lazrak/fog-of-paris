package com.glazrak.fogofparis.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glazrak.fogofparis.R
import com.glazrak.fogofparis.domain.ArrondissementBadge
import com.glazrak.fogofparis.domain.CollectionSet
import com.glazrak.fogofparis.domain.GeoPosition
import com.glazrak.fogofparis.domain.arrondissementBadges
import com.glazrak.fogofparis.domain.Medal
import com.glazrak.fogofparis.domain.Place
import com.glazrak.fogofparis.domain.Quartier
import com.glazrak.fogofparis.domain.QuartierProgress
import com.glazrak.fogofparis.domain.cellsNeededFor
import com.glazrak.fogofparis.ui.theme.Night
import com.glazrak.fogofparis.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos

// Écran « Quartiers » : la mosaïque des 80 quartiers, colorés par médaille.
@Composable
fun QuartiersScreen(viewModel: MapViewModel, onShowQuartier: (Quartier) -> Unit, onHuntTreasure: (Place) -> Unit) {
    val progress by viewModel.quartierProgress.collectAsStateWithLifecycle()
    val treasures by viewModel.treasureByQuartier.collectAsStateWithLifecycle()
    if (progress.isEmpty()) return
    // Par défaut, le quartier le plus avancé : c'est lui qu'on a envie de regarder.
    var selectedId by rememberSaveable { mutableStateOf<Int?>(null) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val selected = progress.firstOrNull { it.quartier.id == selectedId } ?: progress.maxBy { it.percent }
    val medalCounts = Medal.entries.associateWith { medal -> progress.count { it.medal == medal } }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .explorerBackground()
            .verticalScroll(scrollState)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
    ) {
        ScreenHeader(
            kicker = stringResource(R.string.kicker_quartiers),
            title = stringResource(R.string.quartiers_title),
            subtitle = progress.count { it.medal != null }.let { medalled ->
                pluralStringResource(R.plurals.quartiers_subtitle, medalled, medalled, progress.size)
            },
        )
        NightCard(padding = 12.dp) {
            Mosaic(progress, selected.quartier.id, onSelect = { selectedId = it.id })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val context = LocalContext.current
                Medal.entries.forEach { medal -> LegendItem(medal.color, "${medal.label(context)} ${medalCounts[medal]}") }
                LegendItem(Night.Started, stringResource(R.string.legend_started))
            }
        }
        SelectedQuartierCard(
            selected, treasures[selected.quartier.id],
            onShow = { onShowQuartier(selected.quartier) },
            onHuntTreasure = onHuntTreasure,
        )
        val badges = remember(progress) { arrondissementBadges(progress).associateBy { it.arrondissement } }
        Column {
            SectionLabel(stringResource(R.string.badges_title))
            Text(
                stringResource(R.string.badges_subtitle, badges.values.count { it.earned }),
                style = MaterialTheme.typography.bodySmall,
                color = Night.TextMuted,
            )
        }
        progress.groupBy { it.quartier.arrondissement }.toSortedMap().forEach { (arrondissement, quartiers) ->
            ArrondissementRow(
                arrondissement, quartiers.sortedBy { it.quartier.id }, selected.quartier.id,
                badge = badges[arrondissement],
                // Remonte à la mosaïque : sinon le quartier choisi est hors de vue, tout en haut.
                onSelect = {
                    selectedId = it
                    scope.launch { scrollState.animateScrollTo(0) }
                },
            )
        }
    }
}

fun fillFor(progress: QuartierProgress, palette: Palette): Color =
    progress.medal?.colorIn(palette) ?: if (progress.revealedCells > 0) palette.Started else palette.Unexplored

// Dessin des quartiers dans leurs vraies formes. Un appui choisit le quartier touché.
@Composable
private fun Mosaic(progress: List<QuartierProgress>, selectedId: Int, onSelect: (Quartier) -> Unit) {
    val quartiers = progress.map { it.quartier }
    val bounds = remember(quartiers) { MosaicBounds.of(quartiers) }
    val description = stringResource(R.string.mosaic_description)
    val palette = Night
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(bounds.aspectRatio)
            .semantics { contentDescription = description }
            .pointerInput(quartiers) {
                detectTapGestures { tap ->
                    val position = bounds.toGeo(tap, size.width.toFloat())
                    quartiers.firstOrNull { it.boundary.contains(position) }?.let(onSelect)
                }
            },
    ) {
        val width = size.width
        // Le quartier choisi est dessiné en dernier, pour que son contour blanc passe dessus.
        val ordered = progress.sortedBy { it.quartier.id == selectedId }
        ordered.forEach { item ->
            val path = Path()
            item.quartier.boundary.rings.forEach { ring ->
                ring.points.forEachIndexed { i, point ->
                    val offset = bounds.toScreen(point, width)
                    if (i == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
                }
                path.close()
            }
            drawPath(path, fillFor(item, palette), style = Fill)
            val isSelected = item.quartier.id == selectedId
            drawPath(
                path,
                if (isSelected) palette.Text else palette.Background,
                style = Stroke(width = if (isSelected) 2.dp.toPx() else 0.8.dp.toPx()),
            )
        }
    }
}

// Projection équirectangulaire des quartiers dans un rectangle d'écran.
private class MosaicBounds(val west: Double, val east: Double, val south: Double, val north: Double) {
    private val cosLat = cos((south + north) / 2 * PI / 180)
    val aspectRatio: Float get() = ((east - west) * cosLat / (north - south)).toFloat()

    fun toScreen(point: GeoPosition, width: Float): Offset {
        val scale = width / ((east - west) * cosLat)
        return Offset(
            x = ((point.lon - west) * cosLat * scale).toFloat(),
            y = ((north - point.lat) * scale).toFloat(),
        )
    }

    fun toGeo(offset: Offset, width: Float): GeoPosition {
        val scale = width / ((east - west) * cosLat)
        return GeoPosition(lat = north - offset.y / scale, lon = west + offset.x / scale / cosLat)
    }

    companion object {
        fun of(quartiers: List<Quartier>): MosaicBounds {
            val points = quartiers.flatMap { q -> q.boundary.rings.flatMap { it.points } }
            return MosaicBounds(
                west = points.minOf { it.lon }, east = points.maxOf { it.lon },
                south = points.minOf { it.lat }, north = points.maxOf { it.lat },
            )
        }
    }
}

@Composable
private fun SelectedQuartierCard(
    progress: QuartierProgress,
    treasure: PlaceMarker?,
    onShow: () -> Unit,
    onHuntTreasure: (Place) -> Unit,
) {
    val context = LocalContext.current
    val medal = progress.medal
    val accent = medal?.color ?: Night.TextMuted
    val next = Medal.entries.firstOrNull { it !in progress.medalDates }
    NightCard(highlight = medal?.color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                SectionLabel(arrondissementLabel(context, progress.quartier.arrondissement))
                Text(progress.quartier.name, style = MaterialTheme.typography.headlineSmall, color = Night.Text)
            }
            if (medal != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(medal.color),
                ) {
                    Icon(painterResource(R.drawable.ic_medal), contentDescription = medal.label(context), tint = Night.GoldInk)
                }
            }
        }
        Row {
            Text(
                when {
                    next == null -> stringResource(R.string.quartier_all_medals)
                    medal == null -> stringResource(
                        R.string.quartier_to_first_medal,
                        cellsNeededFor(next, progress.totalCells) - progress.revealedCells, next.label(context),
                    )
                    else -> stringResource(
                        R.string.quartier_to_next_medal, medal.label(context),
                        cellsNeededFor(next, progress.totalCells) - progress.revealedCells, next.label(context),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = Night.TextSoft,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.quartier_percent, formatPercent(progress.percent)),
                style = MaterialTheme.typography.labelLarge,
                color = if (medal != null) accent else Night.Text,
            )
        }
        // Barre pleine à 75 % : la médaille « Maîtrisé », le maximum réaliste.
        NightProgressBar((progress.percent / Medal.MASTERED.thresholdPercent).toFloat(), medal?.color ?: Night.Started, height = 8.dp)
        treasure?.let { TreasureLine(it, onHunt = { onHuntTreasure(it.place) }) }
        Button(
            onClick = onShow,
            colors = ButtonDefaults.buttonColors(containerColor = Night.Text, contentColor = Night.Background),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        ) {
            Icon(painterResource(R.drawable.ic_tab_map), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.show_on_map), modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}

// Le trésor du quartier : son indice tant qu'il est caché, son nom une fois trouvé.
@Composable
private fun TreasureLine(treasure: PlaceMarker, onHunt: () -> Unit) {
    val color = CollectionSet.TREASURES.color
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        // Trésor pas encore trouvé : un appui lance la chasse « chaud / froid ».
        modifier = if (treasure.visited) Modifier else Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onHunt),
    ) {
        IconBadge(R.drawable.ic_treasure, if (treasure.visited) color else Night.TextMuted, size = 36.dp)
        Column(modifier = Modifier.weight(1f)) {
            SectionLabel(stringResource(R.string.treasure_hint_label))
            if (treasure.visited) {
                Text(treasure.place.name, style = MaterialTheme.typography.titleMedium, color = color)
            } else {
                Text(treasure.place.hint.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Night.TextSoft)
                Text(stringResource(R.string.hunt_start_hint), style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
    }
}

// Une ligne par arrondissement : ses 4 quartiers en petites barres colorées.
@Composable
private fun ArrondissementRow(
    arrondissement: Int,
    quartiers: List<QuartierProgress>,
    selectedId: Int,
    badge: ArrondissementBadge?,
    onSelect: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (arrondissement == 1) "1er" else "${arrondissement}e",
            style = MaterialTheme.typography.titleMedium,
            color = Night.Text,
            modifier = Modifier.width(40.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            quartiers.forEach { quartier ->
                val isSelected = quartier.quartier.id == selectedId
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clickable { onSelect(quartier.quartier.id) },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (isSelected) 12.dp else 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(fillFor(quartier, Night)),
                    )
                }
            }
        }
        // Badge : doré une fois gagné, sinon discret avec le nombre de Bronze obtenus.
        if (badge != null) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                Icon(
                    painterResource(R.drawable.ic_badge),
                    contentDescription = stringResource(R.string.badge_progress, badge.bronzeQuartiers, badge.quartierCount),
                    tint = if (badge.earned) Night.Gold else Night.Started,
                    modifier = Modifier.size(if (badge.earned) 28.dp else 24.dp),
                )
            }
        }
    }
}
