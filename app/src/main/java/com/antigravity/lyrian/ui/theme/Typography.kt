package com.antigravity.lyrian.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Lyrian 的字級。
 *
 * MD3 的 type scale 已由 [Typography] 提供完整定義，這裡只覆寫這個 App 特有的需求：
 * **歌詞行**。參考圖要求歌詞是大字（比內文明顯大），且行高要寬鬆以便閱讀。
 *
 * 其餘（titleMedium 用於歌名、bodyMedium 用於說明、labelMedium 用於標籤）
 * 直接沿用 MD3 預設，不另行覆寫——避免偏離規格。
 */
val LyrianTypography = Typography().let { base ->
    base.copy(
        // 當前句：參考圖約 22sp
        headlineSmall = base.headlineSmall.copy(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 30.sp,
        ),
    )
}

/**
 * 歌詞行的樣式。
 *
 * 刻意**不放在 Typography 裡**——MD3 的 type scale 沒有「歌詞」這個角色，
 * 硬塞進某個 scale 只會誤導。這是 Lyrian 自訂的顯示樣式。
 */
object LyricTextStyles {
    /** 當前句：最大、最重 */
    val Current = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 32.sp,
    )

    /** 上下文（上一句／下一句）：縮小並降低對比 */
    val Context = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
}
