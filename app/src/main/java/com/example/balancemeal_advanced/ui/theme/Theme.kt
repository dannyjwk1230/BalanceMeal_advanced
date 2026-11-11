package com.example.balancemeal_advanced.ui.theme

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val StitchLightColors = lightColorScheme(
    primary = BalanceColors.Mint, onPrimary = Color.White,
    secondary = BalanceColors.Protein, tertiary = BalanceColors.Fat,
    background = BalanceColors.Background, onBackground = BalanceColors.Ink,
    surface = BalanceColors.Background, onSurface = BalanceColors.Ink,
    surfaceVariant = BalanceColors.SurfaceLow, onSurfaceVariant = BalanceColors.SecondaryText,
    outline = BalanceColors.Outline,
)

@Composable
fun BalanceMeal_advancedTheme(content: @Composable () -> Unit) {
    // Only the supplied light design is represented; dynamic colors must not change nutrient meanings.
    MaterialTheme(
        colorScheme = StitchLightColors, typography = Typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(24.dp),
        ),
    ) {
        // Avoid stretching a textured layer containing clipped glass cards and charts.
        // Normal scrolling/flinging remains enabled, including lists, rails and dialogs.
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                content = content,
            )
        }
    }
}
