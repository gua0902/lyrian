package com.antigravity.lyrian.ui.screens.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「第一句不得提前亮起」的回歸測試。
 *
 * 這是擁有者回報的 bug：歌曲開頭三點與第一句一起亮。
 * 原因不是三點的條件寫錯，而是**捲動與高亮共用同一個值**：
 * ```
 * targetLineIndex = singingLineIndex ?: 第一句   → 第一句被當成「正在唱」
 * ```
 * 拆成 [LyricViewportTargets] 之後，這條規則可以被單元測試守住。
 */
class LyricViewportTargetsTest {

    @Test
    fun `音訊還沒開始時第一句不得亮起`() {
        val targets = LyricViewportTargets.of(
            singingLineIndex = null,
            firstLineIndex = 0,
        )

        assertEquals("畫面仍要對到第一句（三點在它上方）", 0, targets.scrollTo)
        assertNull("但不可以高亮任何一行", targets.highlight)
    }

    @Test
    fun `音訊進到第一句後才亮起`() {
        val targets = LyricViewportTargets.of(
            singingLineIndex = 0,
            firstLineIndex = 0,
        )

        assertEquals(0, targets.scrollTo)
        assertEquals(0, targets.highlight)
    }

    @Test
    fun `有前奏時捲到第一句但維持不亮`() {
        val targets = LyricViewportTargets.of(
            singingLineIndex = null,
            firstLineIndex = 3,
        )

        assertEquals(3, targets.scrollTo)
        assertNull(targets.highlight)
    }

    @Test
    fun `後續換行時捲動與高亮一起前進`() {
        val targets = LyricViewportTargets.of(
            singingLineIndex = 7,
            firstLineIndex = 0,
        )

        assertEquals(7, targets.scrollTo)
        assertEquals(7, targets.highlight)
    }

    @Test
    fun `沒有任何歌詞時退回 fallback 且不亮`() {
        val targets = LyricViewportTargets.of(
            singingLineIndex = null,
            firstLineIndex = null,
            fallbackLineIndex = 0,
        )

        assertEquals(0, targets.scrollTo)
        assertNull(targets.highlight)
    }

    @Test
    fun `沒有時間軸的歌詞由作答進度決定高亮`() {
        // 純文字歌詞沒有 currentTime 可用，只能跟著作答進度走；
        // 這種情況的 fallbackHighlight 由呼叫端傳入（有時間軸時必須傳 null）
        val targets = LyricViewportTargets.of(
            singingLineIndex = null,
            firstLineIndex = 0,
            fallbackHighlight = 4,
        )

        assertEquals(4, targets.scrollTo)
        assertEquals(4, targets.highlight)
    }

    @Test
    fun `有時間軸時 fallbackHighlight 為 null 就不亮`() {
        val targets = LyricViewportTargets.of(
            singingLineIndex = null,
            firstLineIndex = 0,
            fallbackHighlight = null,
        )

        assertEquals(0, targets.scrollTo)
        assertNull(targets.highlight)
    }
}
