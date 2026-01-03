package tv.anime.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme as ComposeMaterialTheme
import androidx.compose.material3.darkColorScheme as ComposeDarkColorScheme
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as TvDarkColorScheme

@Composable
fun AnimeTvTheme(content: @Composable () -> Unit) {
    // The project uses a mix of androidx.tv.material3 and androidx.compose.material3 components.
    // Wrap both themes so surfaces, text, and controls share the same dark/TV-first palette.
    val background = Color.Black
    val onBackground = Color.White
    val surface = Color.Black
    val onSurface = Color.White

    val composeScheme = ComposeDarkColorScheme(
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        primary = onSurface,
        onPrimary = background,
        secondary = onSurface,
        onSecondary = background
    )

    val tvScheme = TvDarkColorScheme(
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        primary = onSurface,
        onPrimary = background,
        secondary = onSurface,
        onSecondary = background
    )

    ComposeMaterialTheme(colorScheme = composeScheme) {
        TvMaterialTheme(colorScheme = tvScheme) {
            content()
        }
    }
}
