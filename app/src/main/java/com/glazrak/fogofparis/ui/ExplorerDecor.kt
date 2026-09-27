package com.glazrak.fogofparis.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.glazrak.fogofparis.ui.theme.Night

// Décor « carte ancienne » des écrans de menu (thème exploration, validé par le
// propriétaire) : un fond bleu nuit, un quadrillage de méridiens très discret et
// une grande rose des vents qui déborde du coin. Tout reste en arrière-plan,
// assez pâle pour ne jamais gêner la lecture.
@Composable
fun Modifier.explorerBackground(): Modifier {
    // Lue ici : le dessin lui-même ne peut pas lire le thème.
    val palette = Night
    // Sur papier clair, l'or doit être un peu plus marqué pour se voir.
    val strength = if (palette.isDark) 1f else 1.6f
    return drawBehind { drawExplorerDecor(palette.Background, palette.Gold, strength) }
}

private fun DrawScope.drawExplorerDecor(background: Color, gold: Color, strength: Float) {
    drawRect(background)
    val gridColor = gold.copy(alpha = 0.045f * strength)
    val step = 56.dp.toPx()
    var x = step / 2
    while (x < size.width) {
        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = step / 2
    while (y < size.height) {
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
    drawCompassRose(
        center = Offset(size.width * 0.92f, size.height * 0.14f),
        radius = size.width * 0.42f,
        color = gold.copy(alpha = 0.07f * strength),
    )
}

// Rose des vents : deux cercles gradués et une étoile à 8 branches
// (4 longues pour les points cardinaux, 4 courtes pour les intermédiaires).
fun DrawScope.drawCompassRose(center: Offset, radius: Float, color: Color) {
    val stroke = 1.2.dp.toPx()
    drawCircle(color, radius, center, style = Stroke(stroke))
    drawCircle(color, radius * 0.86f, center, style = Stroke(stroke))
    // Graduations tous les 10°, plus longues tous les 45°.
    for (degrees in 0 until 360 step 10) {
        rotate(degrees.toFloat(), pivot = center) {
            val length = if (degrees % 45 == 0) radius * 0.1f else radius * 0.05f
            drawLine(color, Offset(center.x, center.y - radius), Offset(center.x, center.y - radius + length), stroke)
        }
    }
    for (branch in 0 until 8) {
        val long = branch % 2 == 0
        rotate(branch * 45f, pivot = center) {
            val tip = if (long) radius * 0.82f else radius * 0.5f
            val half = if (long) radius * 0.1f else radius * 0.07f
            val path = Path().apply {
                moveTo(center.x, center.y - tip)
                lineTo(center.x + half, center.y)
                lineTo(center.x, center.y + half * 0.3f)
                lineTo(center.x - half, center.y)
                close()
            }
            drawPath(path, color)
        }
    }
    drawCircle(color, radius * 0.05f, center)
}
