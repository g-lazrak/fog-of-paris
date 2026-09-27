package com.glazrak.fogofparis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glazrak.fogofparis.R
import com.glazrak.fogofparis.domain.CollectionProgress
import com.glazrak.fogofparis.domain.CollectionSet
import com.glazrak.fogofparis.domain.LevelProgress
import com.glazrak.fogofparis.domain.Medal
import com.glazrak.fogofparis.tracking.collectionNameRes
import com.glazrak.fogofparis.domain.Quartier
import com.glazrak.fogofparis.domain.QuartierProgress
import com.glazrak.fogofparis.domain.WeekCount
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)

// Écran "Quartiers" : score, historique, et progression des 80 quartiers
// rangés par arrondissement. Un appui sur un quartier le montre sur la carte.
@Composable
fun QuartiersScreen(
    viewModel: MapViewModel,
    onBack: () -> Unit,
    onQuartierSelected: (Quartier) -> Unit,
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val progress by viewModel.quartierProgress.collectAsStateWithLifecycle()
    val history by viewModel.weeklyHistory.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val byArrondissement = progress.groupBy { it.quartier.arrondissement }.toSortedMap()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                }
                Text(stringResource(R.string.quartiers_title), style = MaterialTheme.typography.titleLarge)
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    summary?.let {
                        SummaryCard(it, progress)
                        LevelCard(it.level)
                    }
                    CollectionsSection(collections)
                    HistoryChart(history)
                }
                byArrondissement.forEach { (arrondissement, quartiers) ->
                    item(key = "header-$arrondissement") { ArrondissementHeader(arrondissement) }
                    items(quartiers, key = { it.quartier.id }) { quartierProgress ->
                        QuartierRow(quartierProgress, onClick = { onQuartierSelected(quartierProgress.quartier) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: GameSummary, progress: List<QuartierProgress>) {
    // Nombre de quartiers ayant atteint au moins chaque médaille.
    val medalCounts = Medal.entries.associateWith { medal -> progress.count { medal in it.medalDates } }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            stringResource(R.string.summary_percent, formatPercent(summary.percent)),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            stringResource(R.string.summary_points, formatPoints(summary.points)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Medal.entries.forEach { medal ->
                // Pastille teintée de la couleur de la médaille.
                Surface(shape = RoundedCornerShape(50), color = medal.color.copy(alpha = 0.18f)) {
                    Text(
                        "${medal.emoji} ${medalCounts[medal]}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

// Titre actuel et avancement vers le suivant.
@Composable
private fun LevelCard(progress: LevelProgress) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.level_number, progress.level.number),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(progress.level.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress.fractionToNext },
            color = LEVEL_COLOR,
            trackColor = LEVEL_COLOR.copy(alpha = 0.18f),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        progress.next?.let { next ->
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.level_next, formatPoints(next.minPoints - progress.points), next.title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CollectionsSection(collections: List<CollectionProgress>) {
    if (collections.isEmpty()) return
    var expanded by remember { mutableStateOf<CollectionSet?>(null) }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.collections_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        collections.forEach { collection ->
            val color = if (collection.isComplete) Medal.GOLD.color else COLLECTION_COLOR
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = if (expanded == collection.set) null else collection.set }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(collection.set.emoji, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        stringResource(collectionNameRes(collection.set)),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${collection.visitedCount} / ${collection.places.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { collection.visitedCount.toFloat() / collection.places.size },
                    color = color,
                    trackColor = color.copy(alpha = 0.18f),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
                if (expanded == collection.set) {
                    Spacer(Modifier.height(6.dp))
                    // Lieux visités d'abord, puis les autres par ordre alphabétique.
                    collection.places
                        .sortedWith(compareBy({ it.id !in collection.visitedIds }, { it.name }))
                        .forEach { place ->
                            val visited = place.id in collection.visitedIds
                            Text(
                                (if (visited) "✓  " else "○  ") + place.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (visited) color else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 28.dp, top = 2.dp, bottom = 2.dp),
                            )
                        }
                }
            }
        }
    }
}

private val LEVEL_COLOR = Color(0xFF3F51B5)
private val COLLECTION_COLOR = Color(0xFF26A69A)

// Petites barres : cellules révélées chacune des 8 dernières semaines.
@Composable
private fun HistoryChart(history: List<WeekCount>) {
    if (history.isEmpty()) return
    val max = history.maxOf { it.revealedCells }.coerceAtLeast(1)
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().height(96.dp),
        ) {
            history.forEach { week ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    Text("${week.revealedCells}", style = MaterialTheme.typography.labelSmall)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Hauteur minimale pour qu'une semaine vide reste visible.
                            .height((56 * week.revealedCells / max).coerceAtLeast(2).dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                    )
                    Text(week.weekStart.format(DAY_MONTH), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ArrondissementHeader(arrondissement: Int) {
    Column {
        HorizontalDivider()
        Text(
            arrondissementLabel(LocalContext.current, arrondissement),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun QuartierRow(progress: QuartierProgress, onClick: () -> Unit) {
    // La couleur de la barre dit la médaille ; gris tant qu'il n'y en a pas.
    val barColor = progress.medal?.color ?: MaterialTheme.colorScheme.outline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(progress.quartier.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.quartier_percent, formatPercent(progress.percent)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (progress.medal != null) barColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        // La barre est pleine à 75 % (médaille "Maîtrisé"), le maximum réaliste.
        LinearProgressIndicator(
            progress = { (progress.percent / Medal.MASTERED.thresholdPercent).toFloat().coerceIn(0f, 1f) },
            color = barColor,
            trackColor = barColor.copy(alpha = 0.18f),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
    }
}
