package app.goloom.client.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Material3 colorScheme в терминах Goloom-токенов. Только тёмная тема —
 * светлой не предусмотрено дизайном.
 *
 * Material3-роли мапятся как (см. design/Tokens.kt):
 *   primary       → ring (белый, акцент)
 *   onPrimary     → bg (контраст для primary)
 *   background    → bg
 *   onBackground  → text
 *   surface       → bgElev1 (карточки)
 *   onSurface     → text
 *   surfaceVariant→ bgElev2 (subtle-кнопки)
 *   error         → err
 */
private val GoloomDarkScheme = darkColorScheme(
    primary = G.ring,
    onPrimary = G.bg,
    secondary = G.text,
    onSecondary = G.bg,
    background = G.bg,
    onBackground = G.text,
    surface = G.bgElev1,
    onSurface = G.text,
    surfaceVariant = G.bgElev2,
    onSurfaceVariant = G.textDim,
    surfaceContainerHigh = G.bgElev2,
    surfaceContainerHighest = G.bgElev3,
    error = G.err,
    onError = G.bg,
    outline = G.border,
    outlineVariant = G.borderStrong,
)

@Composable
fun GoloomTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            window.statusBarColor = G.bg.toArgb()
            window.navigationBarColor = G.bg.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = GoloomDarkScheme,
        typography = GoloomTypography,
        content = content,
    )
}
