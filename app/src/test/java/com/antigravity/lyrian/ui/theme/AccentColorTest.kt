package com.antigravity.lyrian.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AccentColor] 的單元測試。
 *
 * 這裡守的是「白色圖示讀得出來」這件事：封面取色可能抓到近乎白色的主色，
 * 直接拿去當按鈕底色會讓播放鍵消失。
 */
class AccentColorTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    @Test
    fun `純白與純黑的亮度`() {
        assertEquals(1.0, AccentColor.luminance(white), 0.001)
        assertEquals(0.0, AccentColor.luminance(black), 0.001)
    }

    @Test
    fun `深色原封不動`() {
        val deepBlue = 0xFF1A237E.toInt()

        assertEquals(deepBlue, AccentColor.readable(deepBlue))
    }

    @Test
    fun `白色會被壓暗到白色圖示讀得出來`() {
        val adjusted = AccentColor.readable(white)

        assertTrue(
            "對比 ${AccentColor.contrastWithWhite(adjusted)} 應 >= ${AccentColor.MIN_ICON_CONTRAST}",
            AccentColor.contrastWithWhite(adjusted) >= AccentColor.MIN_ICON_CONTRAST,
        )
        assertTrue("亮度應被壓低", AccentColor.luminance(adjusted) < AccentColor.luminance(white))
    }

    @Test
    fun `中間調的顏色也會被處理`() {
        // 0xFF808080：亮度約 0.22，對比約 3.9 → 這一個其實已經合格
        val grey = 0xFF808080.toInt()
        assertEquals(grey, AccentColor.readable(grey))

        // 0xFFCCCCCC：亮度約 0.60 → 必須壓暗
        val light = 0xFFCCCCCC.toInt()
        val adjusted = AccentColor.readable(light)
        assertTrue(AccentColor.contrastWithWhite(adjusted) >= AccentColor.MIN_ICON_CONTRAST)
    }

    @Test
    fun `回傳值一律不透明`() {
        val transparent = 0x00223344

        assertEquals(0xFF, (AccentColor.readable(transparent) ushr 24) and 0xFF)
    }

    @Test
    fun `亮度單調：越亮的顏色對比越低`() {
        assertTrue(
            AccentColor.contrastWithWhite(0xFF333333.toInt()) >
                AccentColor.contrastWithWhite(0xFFAAAAAA.toInt()),
        )
    }
}
