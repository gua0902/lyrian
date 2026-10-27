package com.antigravity.lyrian.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * 全局字體與字重（M3E 的情感化排版）。
 *
 * ## 自訂字體：假粉圓體（FakePearl）
 *
 * 擁有者把字型放在 `res/font/fake_pearl_semibold.ttf`。**Android 資源名稱
 * 只能有小寫字母、數字與底線**，所以檔名必須是 `fake_pearl_semibold.ttf`。
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
 * 依擁有者指定：歌詞字級與行距加大（1.8 倍），讓同一行出現兩個空時仍好讀。
 *
 * ## ⚠️ `includeFontPadding = true` 是「g / y / j 尾巴不被切」的關鍵
 *
 * Compose 從 1.4 起預設 **`includeFontPadding = false`**：行盒只算
 * 「宣告的 ascender + descender + lineGap」，不含字型真正的墨跡範圍。
 *
 * 假粉圓體宣告的 `descender = −153/1024 = −0.149 em`，但它真正的字形
 * （`g` 的尾巴、`y`、`j`、`,`）低到 **`yMin = −298/1024 = −0.291 em`**
 * （`winDescent = 300/1024` 才是對的量）。少掉的 0.142 em，
 * 在 16sp 時就是 2.3sp——剛好是尾巴的寬度，所以**每一行都會被切**。
 *
 * 打開 `includeFontPadding` 之後，行盒改用字型的 top/bottom（≈ ±0.29 em），
 * 尾巴就落在盒子裡。搭配 `LineHeightStyle(Trim.None)` 避免行首行尾再被裁一次。
 */
object LyricTextStyles {
    /**
     * 行距倍率：字級 × 1.8。
     *
     * 見 [LyricLineMetrics] 與 `LyricLineHeightTest`：這是「整行文字都畫得進去」
     * 的安全倍率。
     */
    const val LINE_HEIGHT_FACTOR = 1.8f

    /**
     * 讓行盒包含字型的完整墨跡（含 `g` 的下伸）。
     *
     * 三個歌詞相關樣式**都必須**帶上這個值；少一個，那個地方的字就會被切。
     */
    private val INCLUDE_FONT_PADDING = PlatformTextStyle(includeFontPadding = true)

    /** 行高置中、不裁掉任何一端。 */
    private val LINE_HEIGHT_STYLE = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

    /** 當前句：最大、最粗。 */
    val Current = TextStyle(
        fontFamily = LyrianFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        lineHeight = 22.sp * LINE_HEIGHT_FACTOR,
        platformStyle = INCLUDE_FONT_PADDING,
        lineHeightStyle = LINE_HEIGHT_STYLE,
    )

    /** 上下文（上一句／下一句）：縮小並降低對比。 */
    val Context = TextStyle(
        fontFamily = LyrianFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 16.sp * LINE_HEIGHT_FACTOR,
        platformStyle = INCLUDE_FONT_PADDING,
        lineHeightStyle = LINE_HEIGHT_STYLE,
    )

    /**
     * 選項卡上的字（`yacht` / `convertible`）：字型與本行歌詞相同，行高同樣要給足，
     * 否則按鈕高度一收緊就會削掉尾巴。
     */
    val Option = TextStyle(
        fontFamily = LyrianFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 16.sp * LINE_HEIGHT_FACTOR,
        platformStyle = INCLUDE_FONT_PADDING,
        lineHeightStyle = LINE_HEIGHT_STYLE,
    )
}
