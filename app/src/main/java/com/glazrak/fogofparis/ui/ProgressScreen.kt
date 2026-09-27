package com.glazrak.fogofparis.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glazrak.fogofparis.R
import com.glazrak.fogofparis.domain.LevelProgress
import com.glazrak.fogofparis.domain.PlaceDirection
import com.glazrak.fogofparis.domain.DayCount
import com.glazrak.fogofparis.tracking.collectionNameRes
import com.glazrak.fogofparis.ui.theme.Night
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Écran « Progrès » : le tableau de bord du joueur.
@Composable
fun ProgressScreen(
    viewModel: MapViewModel,
    onOpenSettings: () -> Unit,
    onShowPlace: (PlaceDirection) -> Unit,
    onShowDay: (LocalDate) -> Unit,
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val history by viewModel.weeklyHistory.collectAsStateWithLifecycle()
    val nearest by viewModel.nearestPlace.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .explorerBackground()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
    ) {
        val current = summary
        Greeting(title = current?.level?.level?.title, onOpenSettings = onOpenSettings)
        if (current != null) {
            HeroCard(current)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(R.drawable.ic_medal, Night.Gold, "${current.medalledQuartiers}", null, stringResource(R.string.stat_medalled), Modifier.weight(1f))
                StatTile(R.drawable.ic_pin, Night.Teal, "${current.visitedPlaces}", "/${current.totalPlaces}", stringResource(R.string.stat_places), Modifier.weight(1f))
                StatTile(R.drawable.ic_steps, Night.Periwinkle, "+${history.lastOrNull()?.revealedCells ?: 0}", null, stringResource(R.string.stat_week), Modifier.weight(1f))
            }
        }
        nearest?.let { direction ->
            val set = collections.firstOrNull { it.set == direction.place.set }
            CompassCard(direction, set?.visitedCount, set?.places?.size, onClick = { onShowPlace(direction) })
        }
        val days by viewModel.dailyHistory.collectAsStateWithLifecycle()
        if (days.isNotEmpty()) JournalCard(days, onShowDay)
    }
}

@Composable
private fun Greeting(title: String?, onOpenSettings: () -> Unit) {
    val now = LocalDateTime.now()
    val day = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRANCE).replaceFirstChar { it.uppercase() }
    val (moment, hello) = when (now.hour) {
        in 5..11 -> R.string.moment_morning to R.string.hello_day
        in 12..17 -> R.string.moment_afternoon to R.string.hello_day
        else -> R.string.moment_evening to R.string.hello_evening
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Kicker(stringResource(R.string.kicker_progress))
            SectionLabel("$day ${stringResource(moment)}")
            Text(
                if (title != null) stringResource(R.string.hello_with_title, stringResource(hello), title.replaceFirstChar { it.lowercase() })
                else stringResource(hello),
                style = MaterialTheme.typography.headlineSmall,
                color = Night.Text,
            )
        }
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Night.Surface)
                .border(1.dp, Night.Border, CircleShape),
        ) {
            Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_title), tint = Night.Text)
        }
    }
}

@Composable
private fun HeroCard(summary: GameSummary) {
    val level = summary.level
    NightCard(padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            LevelRing(level)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${formatPercent(summary.percent)} %", style = MaterialTheme.typography.displaySmall, color = Night.Text)
                Text(stringResource(R.string.hero_revealed), style = MaterialTheme.typography.bodyMedium, color = Night.TextSoft)
                val next = level.next
                Text(
                    buildAnnotatedString {
                        append(stringResource(R.string.hero_points, formatPoints(summary.points)))
                        if (next != null) {
                            append(stringResource(R.string.hero_next_prefix, formatPoints(next.minPoints - summary.points)))
                            withStyle(SpanStyle(color = Night.Text, fontWeight = FontWeight.SemiBold)) { append(next.title) }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Night.TextMuted,
                )
            }
        }
    }
}

// Anneau qui se remplit vers le titre suivant, le titre actuel au centre.
@Composable
private fun LevelRing(level: LevelProgress) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(128.dp)) {
        Canvas(modifier = Modifier.size(128.dp)) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(Night.Border, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            drawArc(
                Night.Gold, -90f, 360f * level.fractionToNext, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.level_number, level.level.number), style = MaterialTheme.typography.labelMedium, color = Night.TextMuted)
            Text(
                level.level.title,
                style = MaterialTheme.typography.titleLarge,
                fontSize = if (level.level.title.length > 11) 15.sp else 20.sp,
                color = Night.Text,
            )
        }
    }
}

@Composable
private fun StatTile(@DrawableRes icon: Int, color: Color, value: String, suffix: String?, label: String, modifier: Modifier) {
    NightCard(modifier = modifier, padding = 12.dp) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(
            buildAnnotatedString {
                append(value)
                if (suffix != null) withStyle(SpanStyle(fontSize = 13.sp, color = Night.TextMuted)) { append(suffix) }
            },
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = MaterialTheme.typography.bodyLarge.fontFamily),
            color = Night.Text,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = Night.TextMuted)
    }
}

// Carte « Prochain lieu le plus proche » : une destination toujours à portée.
@Composable
private fun CompassCard(direction: PlaceDirection, visitedInSet: Int?, setSize: Int?, onClick: () -> Unit) {
    val context = LocalContext.current
    val set = direction.place.set
    NightCard(highlight = Night.BorderStrong, onClick = onClick) {
        SectionLabel(stringResource(R.string.compass_title))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(set.icon, set.color, size = 52.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(direction.place.name, style = MaterialTheme.typography.titleMedium, color = Night.Text)
                Text(
                    if (visitedInSet != null && setSize != null) "${stringResource(collectionNameRes(set))} · $visitedInSet / $setSize"
                    else stringResource(collectionNameRes(set)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Night.TextSoft,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                // Flèche orientée comme sur une carte, nord en haut.
                Icon(
                    painterResource(R.drawable.ic_arrow_up),
                    contentDescription = direction.cardinal.label(context),
                    tint = Night.Gold,
                    modifier = Modifier.size(22.dp).rotate(direction.bearingDegrees.toFloat()),
                )
                Text(formatDistance(direction.distanceMeters), style = MaterialTheme.typography.labelLarge, color = Night.Text)
                Text(direction.cardinal.label(context), style = MaterialTheme.typography.labelSmall, color = Night.TextMuted)
            }
        }
    }
}

private val WEEKDAY_INITIAL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEEE", Locale.FRANCE)

// Journal de marche : une barre par jour (nouveaux carrés) ; un appui montre ce jour sur la carte.
@Composable
private fun JournalCard(days: List<DayCount>, onDay: (LocalDate) -> Unit) {
    val max = days.maxOf { it.revealedCells }.coerceAtLeast(1)
    NightCard {
        Text(stringResource(R.string.journal_title), style = MaterialTheme.typography.titleSmall, color = Night.Text)
        Text(stringResource(R.string.journal_hint), style = MaterialTheme.typography.labelMedium, color = Night.TextMuted)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().height(96.dp),
        ) {
            days.forEach { day ->
                // Toute la colonne est touchable, pas seulement la barre (souvent minuscule).
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onDay(day.date) },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight((day.revealedCells.toFloat() / max * 0.8f).coerceAtLeast(0.04f))
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (day.revealedCells > 0) Night.Gold else Night.Border),
                    )
                    Text(
                        day.date.format(WEEKDAY_INITIAL).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Night.TextMuted,
                    )
                }
            }
        }
    }
}
