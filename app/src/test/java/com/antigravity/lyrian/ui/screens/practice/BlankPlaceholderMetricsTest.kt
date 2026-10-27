package com.antigravity.lyrian.ui.screens.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 未作答空框的寬度。
 *
 * 空框是 inline placeholder，寬度**必須在組字串之前**決定。舊版用定長公式，
 * 窄螢幕遇到長答案（`convertible`）時框會比整行還寬，後面的字被推出畫面——
 * 看起來像破版，實際上是寬度沒有上限。
 */
class BlankPlaceholderMetricsTest {

    @Test
    fun `一般情況用自然寬度`() {
        // 7 個字元、22sp、可用 300sp：自然寬度約 7*0.62*22+22 ≈ 117sp，遠小於上限
        val width = BlankPlaceholderMetrics.widthSp(
            charCount = 7,
            fontSizeSp = 22f,
            availableWidthSp = 300f,
        )

        assertEquals(BlankPlaceholderMetrics.naturalWidthSp(7, 22f), width, 0.01f)
    }

    @Test
    fun `長答案在窄螢幕上被夾住`() {
        // 320dp 寬、左右各 20dp → 約 280dp；換算成 sp 在字型縮放 1 倍時約 280sp
        val width = BlankPlaceholderMetrics.widthSp(
            charCount = 30,
            fontSizeSp = 16f,
            availableWidthSp = 280f,
        )

        assertTrue(
            "空框 $width sp 不得超過可用寬度 280sp",
            width <= 280f,
        )
        // 至少留得住 1 個字高，不會縮成 0
        assertTrue(width >= 16f)
    }

    @Test
    fun `超級短的字（1 個字母）仍有可點的大小`() {
        val width = BlankPlaceholderMetrics.widthSp(
            charCount = 1,
            fontSizeSp = 16f,
            availableWidthSp = 300f,
        )

        assertTrue("單字元的空框 $width sp 太小", width >= 16f * 1.4f)
    }

    @Test
    fun `可用寬度為 0 時退回自然寬度而不是 0`() {
        // BoxWithConstraints 在第一次量測前可能給 0；不能因此算出 0 寬的框
        val width = BlankPlaceholderMetrics.widthSp(
            charCount = 5,
            fontSizeSp = 16f,
            availableWidthSp = 0f,
        )

        assertTrue(width > 0f)
        assertEquals(BlankPlaceholderMetrics.naturalWidthSp(5, 16f), width, 0.01f)
    }

    @Test
    fun `字級為 0 時不會產生非法的寬度`() {
        assertTrue(BlankPlaceholderMetrics.widthSp(5, 0f, 300f) > 0f)
    }

    @Test
    fun `字數越多寬度越大`() {
        val short = BlankPlaceholderMetrics.widthSp(4, 16f, 1000f)
        val long = BlankPlaceholderMetrics.widthSp(12, 16f, 1000f)

        assertTrue(long > short)
    }
}
