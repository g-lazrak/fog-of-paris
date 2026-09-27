package com.glazrak.fogofparis.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.glazrak.fogofparis.R

// Polices livrées avec l'app (licence SIL OFL, textes dans assets/licenses) :
// Fraunces pour les titres (serif « affiche parisienne »), DM Sans pour le texte.
// Ce sont des polices variables : un seul fichier par famille, graisse réglée ici.
private fun variable(res: Int, weight: Int) = Font(
    resId = res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Fraunces = FontFamily(
    variable(R.font.fraunces, 500),
    variable(R.font.fraunces, 700),
)

val DmSans = FontFamily(
    variable(R.font.dm_sans, 400),
    variable(R.font.dm_sans, 500),
    variable(R.font.dm_sans, 600),
    variable(R.font.dm_sans, 700),
)

private val base = Typography()

val Typography = Typography(
    displaySmall = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = base.titleSmall.copy(fontFamily = DmSans, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = DmSans),
    bodyMedium = base.bodyMedium.copy(fontFamily = DmSans),
    bodySmall = base.bodySmall.copy(fontFamily = DmSans),
    labelLarge = base.labelLarge.copy(fontFamily = DmSans, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = DmSans),
    labelSmall = base.labelSmall.copy(fontFamily = DmSans),
)
