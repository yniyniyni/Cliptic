@file:OptIn(ExperimentalTextApi::class)

package art.yniyniyni.cliptic.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import art.yniyniyni.cliptic.R

// Bundled Space Grotesk variable font (wght axis 300–700) — pinned per weight via
// FontVariation so the typeface ships in the APK instead of relying on the runtime
// Google Fonts downloadable provider (which silently fell back to Roboto on-device).
private fun spaceGrotesk(weight: Int) = Font(
    resId = R.font.space_grotesk_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.coerceAtMost(700)))
)

val SpaceGroteskFamily = FontFamily(
    spaceGrotesk(400),
    spaceGrotesk(500),
    spaceGrotesk(600),
    spaceGrotesk(700),
    // Space Grotesk's axis tops out at 700; map ExtraBold (used by the display hero) to it.
    Font(
        resId = R.font.space_grotesk_variable,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    ),
)

// Start from the Material 3 defaults so every one of the 15 text styles is covered,
// then re-home them all on Space Grotesk and override the ones the Cliptic spec tunes.
// (Anything left on the default Typography would silently render in Roboto.)
private val Default = Typography()

private fun TextStyle.spaceGrotesk() = copy(fontFamily = SpaceGroteskFamily)

val ClipticTypography = Typography(
    displayLarge = Default.displayLarge.copy(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.8).sp
    ),
    displayMedium = Default.displayMedium.spaceGrotesk(),
    displaySmall = Default.displaySmall.spaceGrotesk(),
    headlineLarge = Default.headlineLarge.copy(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.28).sp
    ),
    headlineMedium = Default.headlineMedium.copy(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.24).sp
    ),
    headlineSmall = Default.headlineSmall.spaceGrotesk(),
    titleLarge = Default.titleLarge.copy(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = Default.titleMedium.copy(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = Default.titleSmall.copy(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = Default.bodyLarge.spaceGrotesk(),
    bodyMedium = Default.bodyMedium.copy(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = Default.bodySmall.copy(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = Default.labelLarge.spaceGrotesk(),
    labelMedium = Default.labelMedium.spaceGrotesk(),
    labelSmall = Default.labelSmall.copy(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp
    )
)
