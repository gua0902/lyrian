package com.antigravity.lyrian.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 歌詞行高與字型下伸的關係。
 *
 * ## 這個測試擋的是什麼
 *
 * 擁有者回報 `good` / `girl` / `yacht` 的尾巴被切。原因不是配色、不是排版失誤，
 * 而是**內建字型宣告的 descender 比它真正的字形淺**：
 *
 * ```
 * fake_pearl_semibold.ttf
 *   hhea.ascender  =  871/1024 = 0.8506
 *   hhea.descender = -153/1024 = -0.1494   ← Compose 量測只認這個
 *   font bbox yMin = -298/1024 = -0.2910   ← g 的尾巴實際伸到這裡
 * ```
 *
 * Compose 量測 `Text` 的上下界時用「ascender + |descender| + lineGap」，
 * **不含**任何下伸緩衝。所以只要行高或容器高度剛好等於那個盒子，
 * 尾巴就會被外層（滑動視窗、被夾限的 Constraints、固定高度的按鈕）削掉。
 *
 * 這些測試把「行高必須留得住下伸部」變成可執行的規則——之後任何人調行高、
 * 改字級、換字型，只要破壞了這個不變式就會在這裡失敗，而不是在實機上被使用者抓到。
 */
class LyricLineHeightTest {

    private val fontSizeSp = 22f

    @Test
    fun `字型下伸比宣告值深`() {
        // 這一條說明了整個問題的來源；數字變動就代表換了字型，必須重新確認所有行高
        assertTrue(
            "字形下伸（0.291 em）必須大於宣告的 descender（0.149 em）才算「有緩衝需求」",
            LyricLineMetrics.GLYPH_DESCENT_RATIO > 0.1494f,
        )
    }

    @Test
    fun `22sp 的當前歌詞行高足以容住下伸部`() {
        val style = LyricTextStyles.Current
        val lineHeight = style.lineHeight.value
        val size = style.fontSize.value

        assertTrue(
            "行高 ${lineHeight}sp 不足以容納 ${size}sp 字級的下伸部" +
                "（需要 >= ${LyricLineMetrics.minLineHeightSp(size)}sp）",
            LyricLineMetrics.keepsDescenders(size, lineHeight),
        )
    }

    @Test
    fun `16sp 的上下文歌詞行高足以容住下伸部`() {
        val style = LyricTextStyles.Context
        val lineHeight = style.lineHeight.value
        val size = style.fontSize.value

        assertTrue(
            "行高 ${lineHeight}sp 不足以容納 ${size}sp 字級的下伸部",
            LyricLineMetrics.keepsDescenders(size, lineHeight),
        )
    }

    @Test
    fun `選項卡的字行高足以容住下伸部`() {
        // 選項卡（yacht / convertible）也是同一個字型，超過 1 行的話尾巴會被削
        val option = LyricTextStyles.Option
        assertTrue(
            LyricLineMetrics.keepsDescenders(option.fontSize.value, option.lineHeight.value),
        )
    }

    @Test
    fun `安全行高不會低於下限`() {
        // 想要的行距太鬆 → 照給
        assertTrue(
            LyricLineMetrics.safeLineHeightSp(fontSizeSp, desiredFactor = 2.0f) >= fontSizeSp * 2f,
        )
        // 想要的行距太緊 → 抬到下限，而不是照給（照給就會切字）
        val tight = LyricLineMetrics.safeLineHeightSp(fontSizeSp, desiredFactor = 1.0f)
        assertTrue(
            "太緊的行距必須被抬到下限 ${LyricLineMetrics.minLineHeightSp(fontSizeSp)}sp，實際 $tight",
            tight >= LyricLineMetrics.minLineHeightSp(fontSizeSp),
        )
    }

    @Test
    fun `下限的算法對任何字級都成立`() {
        listOf(12f, 16f, 20f, 22f, 32f).forEach { size ->
            val min = LyricLineMetrics.minLineHeightSp(size)
            assertTrue("字級 $size：下限 $min 必須 >= 字身（含下伸）", min >= size * 1.16f)
        }
    }

    @Test
    fun `歌詞相關樣式都打開 includeFontPadding`() {
        // 這是「g / y / j 尾巴不被切」的**主防線**。
        // Compose 預設 includeFontPadding = false，行盒只認宣告的 descender
        // （−0.149 em），但這支字型的墨跡到 −0.291 em——尾巴一定會被切。
        // 任何人把這一行拿掉，這個測試就會失敗，而不是等使用者再回報一次。
        //
        // `includeFontPadding` 是 PlatformTextStyle 的 internal 屬性，測試讀不到，
        // 因此改驗它的字串表示（Compose 的 toString 會帶出這個欄位）。
        listOf(
            "Current" to LyricTextStyles.Current,
            "Context" to LyricTextStyles.Context,
            "Option" to LyricTextStyles.Option,
        ).forEach { (name, style) ->
            println("$name platformStyle = ${style.platformStyle}")
            assertTrue(
                "$name 必須開啟 includeFontPadding，否則下伸部（g / y / j）會被裁掉",
                style.platformStyle?.toString()?.contains("includeFontPadding=true") == true,
            )
        }
    }

    @Test
    fun `歌詞相關樣式的行高不裁切任何一端`() {
        listOf(LyricTextStyles.Current, LyricTextStyles.Context, LyricTextStyles.Option).forEach {
            assertEquals(
                androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
                it.lineHeightStyle?.trim,
            )
        }
    }
}
