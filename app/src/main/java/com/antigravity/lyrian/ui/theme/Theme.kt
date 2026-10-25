package com.antigravity.lyrian.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Lyrian 的 Material 3 主題。
 *
 * **配色策略（依擁有者裁決）**
 * 1. **優先使用動態配色**（Material You）——Android 12+ 從桌布生成整組色票。
 *    這是 M3 的「Personal」原則，也是擁有者明確要求的行為。
 * 2. 舊版 Android 或動態配色不可用時，退回**單色系 fallback**：
 *    Primary / Secondary / Tertiary **全部同色相 240°**，只用色度與亮度分層。
 *    （擁有者要求「確保是同一色系，不要跳到其他顏色的」——M3 標準的 Tertiary 會偏 60°，
 *    這裡刻意不偏。）
 *
 * fallback 色票由 seed `#4A4ACF` 生成，完整值見 `docs/mockups/mono-scheme.kt`。
 */
private val LyrianLightScheme = lightColorScheme(
    primary = Color(0xFF4B4BD5),
    onPrimary = Color(0xFFFEFEFF),
    primaryContainer = Color(0xFFE1E1F8),
    onPrimaryContainer = Color(0xFF13134F),
    secondary = Color(0xFF5656A1),
    onSecondary = Color(0xFFFEFEFF),
    secondaryContainer = Color(0xFFE1E1EF),
    onSecondaryContainer = Color(0xFF1A1A2F),
    tertiary = Color(0xFF5151BC),
    onTertiary = Color(0xFFFEFEFF),
    tertiaryContainer = Color(0xFFE1E1F3),
    onTertiaryContainer = Color(0xFF17173C),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE2E2EA),
    onSurfaceVariant = Color(0xFF454558),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F4F6),
    surfaceContainer = Color(0xFFEDEDF1),
    surfaceContainerHigh = Color(0xFFE8E8EC),
    surfaceContainerHighest = Color(0xFFE3E3E8),
    outline = Color(0xFF747492),
    outlineVariant = Color(0xFFC5C5D0),
    inverseSurface = Color(0xFF2F2F33),
    inverseOnSurface = Color(0xFFF1F1F4),
    inversePrimary = Color(0xFFC3C3F1),
)

private val LyrianDarkScheme = darkColorScheme(
    primary = Color(0xFFC3C3F1),
    onPrimary = Color(0xFF1F1F86),
    primaryContainer = Color(0xFF35359C),
    onPrimaryContainer = Color(0xFFE1E1F8),
    secondary = Color(0xFFC4C4E0),
    onSecondary = Color(0xFF2E2E50),
    secondaryContainer = Color(0xFF44446B),
    onSecondaryContainer = Color(0xFFE1E1EF),
    tertiary = Color(0xFFBFBFEE),
    onTertiary = Color(0xFF2A2A70),
    tertiaryContainer = Color(0xFF41418E),
    onTertiaryContainer = Color(0xFFE1E1F3),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    surface = Color(0xFF141416),
    onSurface = Color(0xFFE2E2E5),
    surfaceVariant = Color(0xFF454558),
    onSurfaceVariant = Color(0xFFC5C5D4),
    surfaceContainerLowest = Color(0xFF0E0E10),
    surfaceContainerLow = Color(0xFF1A1A1D),
    surfaceContainer = Color(0xFF1E1E21),
    surfaceContainerHigh = Color(0xFF28282C),
    surfaceContainerHighest = Color(0xFF333338),
    outline = Color(0xFF8F8FA8),
    outlineVariant = Color(0xFF454558),
    inverseSurface = Color(0xFFE2E2E5),
    inverseOnSurface = Color(0xFF2F2F33),
    inversePrimary = Color(0xFF4B4BD5),
)

/**
 * @param darkTheme 是否使用深色主題；預設跟隨系統
 * @param dynamicColor 是否使用動態配色；預設開啟（Android 12+ 生效）
 */
@Composable
fun LyrianTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> LyrianDarkScheme
        else -> LyrianLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LyrianTypography,
        // 圓角級距：卡片 12、對話框與彈窗 28、按鈕全圓。
        // 詳細的 token 與「為什麼只有五級」見 `LyrianShapes.kt`。
        shapes = LyrianShapes,
        content = content,
    )
}
