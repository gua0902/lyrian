package com.antigravity.lyrian.ui.theme

import kotlin.math.pow

/**
 * 封面取色之後的「可讀性」處理。
 *
 * ## 為什麼需要
 *
 * 專輯封面是任意圖片，取出來的主色可能非常亮（例如近乎白色的風景照）。
 * 把那個顏色直接當按鈕底色，上面的**白色**播放圖示就會看不見。
 *
 * 擁有者指定的規則是「與白色對比度不足時（luminance > 0.6）自動降低亮度」；
 * 這裡把它量化成**可驗證**的門檻：
 *
 * ```
 * contrast(white, color) >= 3.0     // WCAG AA 對圖示／大字的要求
 * ```
 *
 * 實務上這比 0.6 嚴格（亮度約 0.30 才達標）。0.6 只是「大概偏亮」的直覺值，
 * 拿它當門檻的話，亮度 0.5 的顏色仍然只有 2.3:1，圖示一樣難讀。
 * 因此：**以 0.6 為概念起點，實際夾到 3:1**。
 *
 * ## 為什麼是純 Kotlin 而不是 ColorUtils
 *
 * 這裡一個 Android API 都沒用（只用 Int 位元運算），所以可以直接在 JVM 上
 * 單元測試（`AccentColorTest`），不必開 Robolectric。
 */
object AccentColor {

    /** 白色圖示需要的最低對比（WCAG AA：圖示／大文字 3:1）。 */
    const val MIN_ICON_CONTRAST = 3.0

    /**
     * 主色若亮到白色圖示看不清，就把整體亮度壓到剛好達標。
     *
     * @param argb 來源顏色（alpha 會被強制設為不透明）
     * @return 保證與白色對比 >= [MIN_ICON_CONTRAST] 的顏色
     */
    fun readable(argb: Int): Int {
        if (contrastWithWhite(argb) >= MIN_ICON_CONTRAST) return argb or OPAQUE

        val r = channel(argb, 16)
        val g = channel(argb, 8)
        val b = channel(argb, 0)

        // 亮度對「等比縮放 RGB」是單調的，因此可以二分搜尋最大的縮放比例。
        var low = 0.0
        var high = 1.0
        repeat(BINARY_STEPS) {
            val mid = (low + high) / 2.0
            val candidate = pack(r * mid, g * mid, b * mid)
            if (contrastWithWhite(candidate) >= MIN_ICON_CONTRAST) {
                low = mid
            } else {
                high = mid
            }
        }

        return pack(r * low, g * low, b * low)
    }

    /** WCAG 相對亮度（0 = 黑、1 = 白）。 */
    fun luminance(argb: Int): Double {
        fun linear(value: Int): Double {
            val c = (value and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }

        return 0.2126 * linear(argb shr 16) +
            0.7152 * linear(argb shr 8) +
            0.0722 * linear(argb)
    }

    /** 與白色（#FFFFFF）的對比度；1.0 = 同色、21.0 = 黑底白字。 */
    fun contrastWithWhite(argb: Int): Double = 1.05 / (luminance(argb) + 0.05)

    // ---------- 內部 ----------

    private const val OPAQUE = 0xFF shl 24

    /** 二分搜尋次數：20 次足以把縮放比例收斂到 1/1,000,000。 */
    private const val BINARY_STEPS = 20

    private fun channel(argb: Int, shift: Int): Double =
        ((argb shr shift) and 0xFF) / 255.0

    private fun pack(r: Double, g: Double, b: Double): Int {
        val ri = (r.coerceIn(0.0, 1.0) * 255.0).toInt()
        val gi = (g.coerceIn(0.0, 1.0) * 255.0).toInt()
        val bi = (b.coerceIn(0.0, 1.0) * 255.0).toInt()
        return OPAQUE or (ri shl 16) or (gi shl 8) or bi
    }
}
