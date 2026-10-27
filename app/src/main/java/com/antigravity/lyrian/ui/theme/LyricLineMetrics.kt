package com.antigravity.lyrian.ui.theme

/**
 * 「露出一整行文字」時，行高該給多少才能保住 g / j / y 的尾巴。
 *
 * ## 為什麼這是一條規則，而不是散在各處的常數
 *
 * 內建字體（假粉圓體）宣告的 descender 只有 −0.149 em，實際字形卻到 −0.291 em；
 * Compose 量測 `Text` 時只認宣告值，沒有任何下伸緩衝。因此：
 *
 * ```
 * 盒子高度 = 字級 × (ascender + |descender| + lineGap)   ← 不會因 lineHeight 而變
 * 尾巴下緣 = 字級 × 0.291
 * ```
 *
 * 只要這個盒子高度不足以容納下伸部，尾巴就會被外層（滾動視窗、被夾限的
 * Constraints、固定高度的按鈕）削掉。擁有者回報的「`good` / `girl` 的 g 被切」
 * 與選項卡上的 `yacht` 被切，都是同一個原因。
 *
 * 這個物件把它變成可測的算式（見 `LyricLineHeightTest`），
 * 任何新的歌詞／選項／膠囊版面都應該用它，而不是自己猜行高。
 */
object LyricLineMetrics {

    /**
     * 字型的下伸比例（相對字級）。
     *
     * 來自字型 bbox `yMin = −298 / unitsPerEm = 1024`，比宣告的 `hhea.descender`
     * (−153/1024) 深得多。取整到 0.30 留一點餘裕。
     */
    const val GLYPH_DESCENT_RATIO = 0.30f

    /** 字型的上伸比例（`hhea.ascender = 871 / 1024`），取整到 0.86。 */
    const val GLYPH_ASCENT_RATIO = 0.86f

    /**
     * 一行文字**最少**要這麼高的行高（sp），才能保證下伸部不被切。
     *
     * @param fontSizeSp 字級（sp）
     */
    fun minLineHeightSp(fontSizeSp: Float): Float =
        fontSizeSp * (GLYPH_ASCENT_RATIO + GLYPH_DESCENT_RATIO)

    /**
     * 判斷某個行高是否足以容納一整行文字的下伸部。
     *
     * @param fontSizeSp 字級（sp）
     * @param lineHeightSp 目前設定的行高（sp）
     */
    fun keepsDescenders(fontSizeSp: Float, lineHeightSp: Float): Boolean =
        lineHeightSp >= minLineHeightSp(fontSizeSp)

    /**
     * 給定字級與**想要的**行距倍率，回傳實際可用的行高。
     *
     * 想要的倍率太小（會切字）時自動抬到安全下限——寧可行距鬆一點，
     * 也不要讓字被削掉。
     */
    fun safeLineHeightSp(fontSizeSp: Float, desiredFactor: Float): Float =
        (fontSizeSp * desiredFactor).coerceAtLeast(minLineHeightSp(fontSizeSp))
}
