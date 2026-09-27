package com.glazrak.fogofparis.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.glazrak.fogofparis.R
import com.glazrak.fogofparis.domain.LEVELS
import com.glazrak.fogofparis.domain.Level
import com.glazrak.fogofparis.ui.theme.DarkPalette
import com.glazrak.fogofparis.ui.theme.LocalPalette
import com.glazrak.fogofparis.ui.theme.Night
import kotlin.random.Random

// Écran de fête quand un nouveau titre est atteint (affiché à l'ouverture de l'app).
@Composable
fun CelebrationScreen(level: Level, onContinue: () -> Unit) {
    // Toujours de nuit, même avec les menus clairs : la médaille dorée brille mieux sur fond sombre.
    CompositionLocalProvider(LocalPalette provides DarkPalette) { Celebration(level, onContinue) }
}

@Composable
private fun Celebration(level: Level, onContinue: () -> Unit) {
    BackHandler(onBack = onContinue)
    val next = LEVELS.getOrNull(level.number)
    // Le médaillon arrive en « rebondissant » pour marquer le moment.
    val medallion = remember { Animatable(0.4f) }
    LaunchedEffect(level) {
        medallion.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A14)),
    ) {
        Confetti()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(28.dp),
        ) {
            SectionLabel(stringResource(R.string.celebration_label))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(190.dp)
                    .scale(medallion.value)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFFF7D987), Night.Gold, Color(0xFFA87A1D)))),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .size(150.dp)
                        .border(2.dp, Night.GoldInk.copy(alpha = 0.35f), CircleShape),
                ) {
                    Icon(painterResource(R.drawable.ic_steps), contentDescription = null, tint = Night.GoldInk, modifier = Modifier.size(44.dp))
                    Text(
                        stringResource(R.string.level_number, level.number).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = Night.GoldInk,
                    )
                }
            }
            Text(level.title, style = MaterialTheme.typography.displaySmall, color = Night.Text, textAlign = TextAlign.Center)
            Text(
                if (next != null) stringResource(R.string.celebration_text, formatPoints(level.minPoints), next.title, formatPoints(next.minPoints))
                else stringResource(R.string.celebration_last, formatPoints(level.minPoints)),
                style = MaterialTheme.typography.bodyLarge,
                color = Night.TextSoft,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = Night.Gold, contentColor = Night.GoldInk),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.celebration_continue), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// Confettis qui tombent doucement en boucle derrière le médaillon.
@Composable
private fun Confetti() {
    val gold = Night.Gold
    val colors = listOf(Night.Gold, Night.Periwinkle, Night.Teal, Night.Coral, Night.Orchid, Night.Text)
    val pieces = remember { List(28) { ConfettiPiece(Random.nextFloat(), Random.nextFloat(), colors.random(), Random.nextFloat() * 360f) } }
    val fall by rememberInfiniteTransition(label = "confetti").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Restart),
        label = "fall",
    )
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            Brush.radialGradient(listOf(gold.copy(alpha = 0.25f), Color.Transparent), radius = size.width * 0.7f),
            radius = size.width * 0.7f,
            center = Offset(size.width / 2, size.height * 0.4f),
        )
        pieces.forEach { piece ->
            val y = ((piece.y + fall) % 1f) * size.height
            val x = piece.x * size.width
            rotate(piece.angle + fall * 360f, pivot = Offset(x, y)) {
                drawRect(piece.color, topLeft = Offset(x, y), size = Size(8.dp.toPx(), 14.dp.toPx()))
            }
        }
    }
}

private class ConfettiPiece(val x: Float, val y: Float, val color: Color, val angle: Float)
