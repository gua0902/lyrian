package com.antigravity.lyrian.ui.screens.practice

/**
 * 未作答填空框的寬度。
 *
 * ## 為什麼需要一個「上限」
 *
 * 空框是 inline placeholder，寬度必須在**組字串之前**就決定（`Placeholder`
 * 只吃固定寬高）。舊版用「字數 × 常數」，於是：
 *
 * ```
 * 窄螢幕（320dp）＋ 長答案（convertible）
 *   → 空框比整行還寬 → 後面的字被推出畫面，看起來像破版
 * ```
 *
 * 這裡把寬度夾在「可用寬度 − 左右各 1em」，超過就縮到上限。
 * 純函式，可以直接在 JVM 上測（見 `BlankPlaceholderMetricsTest`）。
 */
internal object BlankPlaceholderMetrics {

    /** 一個字元的平均寬度（相對字級）——這支字型偏窄，0.62em 實測最接近。 */
    private const val CHAR_WIDTH_RATIO = 0.62f

    /** 框的左右留白（相對字級）。 */
    private const val PADDING_RATIO = 1.0f

    /** 兩側各留多少給其他文字（相對字級），避免框貼死在邊界上。 */
    private const val SIDE_MARGIN_RATIO = 1.0f

    /**
     * @param charCount 要顯示的字數（取表面形式與答案的較長者）
     * @param fontSizeSp 字級
     * @param availableWidthSp 這個文字節點可用的寬度
     * @return 空框寬度（sp），保證 > 0 且不超過可用寬度
     */
    fun widthSp(charCount: Int, fontSizeSp: Float, availableWidthSp: Float): Float {
        if (fontSizeSp <= 0f) return 1f
        if (availableWidthSp <= 0f) return naturalWidthSp(charCount, fontSizeSp)

        val natural = naturalWidthSp(charCount, fontSizeSp)
        val cap = (availableWidthSp - fontSizeSp * SIDE_MARGIN_RATIO * 2f)
            .coerceAtLeast(fontSizeSp * 2f)
        return natural.coerceAtMost(cap).coerceAtLeast(1f)
    }

    /** 未受限時的理想寬度。 */
    fun naturalWidthSp(charCount: Int, fontSizeSp: Float): Float =
        charCount.coerceAtLeast(1) * fontSizeSp * CHAR_WIDTH_RATIO + fontSizeSp * PADDING_RATIO
}
