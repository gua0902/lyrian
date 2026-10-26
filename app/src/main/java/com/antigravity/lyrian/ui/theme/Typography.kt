package com.antigravity.lyrian.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 全局字體與字重（M3E 的情感化排版）。
 *
 * ## 自訂字體：假粉圓體（FakePearl）
 *
 * 擁有者把字型放在 `res/font/fake_pearl_semibold.ttf`。**Android 資源名稱
 * 只能有小寫字母、數字與底線**，所以檔名必須是 `fake_pearl_semibold.ttf`；
 * 目前 repo 裡的 `res/font/FakePearl-1.1` 不是合法資源（含大寫、連字號、點），
 * `R.font` 也無法引用，因此 [LyrianFontFamily] 先退回系統字體。
 *
 * ### 要真正套用，只做兩件事
 *
 * 1. 把字型檔改名放到 `app/src/main/res/font/fake_pearl_semibold.ttf`
 * 2. 把 [USE_CUSTOM_FONT] 改成 true
 *
 * 所有文字都走 `MaterialTheme.typography`，所以不必改任何畫面程式碼。
 *
 * ## 字重規則
 *
 * | 角色 | 字重 |
 * |---|---|
 * | Display / Headline / Title | **Bold**（標題要「站出來」） |
 * | Body | **Medium**（比 Regular 有存在感，仍可長時間閱讀） |
 * | Label（按鈕、膠囊） | **Medium** |
 */
private const val USE_CUSTOM_FONT = true

/**
 * 全局字體。切換點只有這一個常數。
 *
 * 資源到位後，把 [USE_CUSTOM_FONT] 改成 true 並取消下面三行的註解即可，
 * **不需要動任何畫面**：
 * `
 * val LyrianFontFamily = FontFamily(
 *     Font(R.font.fake_pearl_semibold, FontWeight.SemiBold),
 * )
 * `
 */
val LyrianFontFamily: FontFamily = FontFamily(
    androidx.compose.ui.text.font.Font(resId = com.antigravity.lyrian.R.font.fake_pearl_semibold, weight = FontWeight.SemiBold),
)

/**
 * 字級：沿用 M3 的 scale，但**字體與字重**統一改寫。
 *
 * 歌詞行另外有 [LyricTextStyles]（M3 沒有「歌詞」這個角色）。
 */
val LyrianTypography = Typography().let { base ->
    fun TextStyle.bold() = copy(fontFamily = LyrianFontFamily, fontWeight = FontWeight.Bold)
    fun TextStyle.medium() = copy(fontFamily = LyrianFontFamily, fontWeight = FontWeight.Medium)

    base.copy(
        displayLarge = base.displayLarge.bold(),
        displayMedium = base.displayMedium.bold(),
        displaySmall = base.displaySmall.bold(),
        headlineLarge = base.headlineLarge.bold(),
        headlineMedium = base.headlineMedium.bold(),
        headlineSmall = base.headlineSmall.bold(),
        titleLarge = base.titleLarge.bold(),
        titleMedium = base.titleMedium.bold(),
        titleSmall = base.titleSmall.bold(),
        bodyLarge = base.bodyLarge.medium(),
        bodyMedium = base.bodyMedium.medium(),
        bodySmall = base.bodySmall.medium(),
        labelLarge = base.labelLarge.medium(),
        labelMedium = base.labelMedium.medium(),
        labelSmall = base.labelSmall.medium(),
    )
}

/**
 * 歌詞行的樣式。
 *
 * 依擁有者指定：歌詞字級與行距加大（1.6 倍），讓同一行出現兩個空時仍好讀。
 */
object LyricTextStyles {
    /** 當前句：最大、Bold。 */
    val Current = TextStyle(
        fontFamily = LyrianFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        lineHeight = 36.sp,
    )

    /** 上下文（上一句／下一句）：縮小並降低對比。 */
    val Context = TextStyle(
        fontFamily = LyrianFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 27.sp,
    )
}
