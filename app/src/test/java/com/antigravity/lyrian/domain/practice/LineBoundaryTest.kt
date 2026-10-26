package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.domain.model.ChoiceRecord
import com.antigravity.lyrian.domain.model.FillChoiceQuestion
import com.antigravity.lyrian.domain.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LineBoundary] 的單元測試。
 *
 * 這是「暫停時機」的唯一真相：交界要算在**前一句的結束時間**。
 * 算錯就會退回舊行為（使用者還沒聽到就被要求作答）。
 */
class LineBoundaryTest {

    private fun line(index: Int, startMs: Long, endMs: Long? = null) =
        LyricLine(index = index, text = "line $index", startMs = startMs, endMs = endMs)

    @Test
    fun `有 endMs 時直接採用`() {
        val lines = listOf(line(0, 1_000, endMs = 3_500), line(1, 4_000))

        assertEquals(3_500L, LineBoundary.endMsOf(lines, 0, durationMs = 200_000))
    }

    @Test
    fun `沒有 endMs 時用下一行的開始時間`() {
        val lines = listOf(line(0, 1_000), line(1, 4_200))

        assertEquals(4_200L, LineBoundary.endMsOf(lines, 0, durationMs = 200_000))
    }

    @Test
    fun `最後一行用曲目總長`() {
        val lines = listOf(line(0, 1_000), line(1, 4_200))

        assertEquals(180_000L, LineBoundary.endMsOf(lines, 1, durationMs = 180_000))
    }

    @Test
    fun `最後一行且總長未知時用估計窗`() {
        val lines = listOf(line(0, 1_000))

        assertEquals(5_000L, LineBoundary.endMsOf(lines, 0, durationMs = null))
    }

    @Test
    fun `總長不合理時（比開始時間還早）退回估計窗`() {
        val lines = listOf(line(0, 100_000))

        assertEquals(
            100_000L + LineBoundary.LAST_LINE_WINDOW_MS,
            LineBoundary.endMsOf(lines, 0, durationMs = 90_000),
        )
    }

    @Test
    fun `找不到行時回傳 null`() {
        assertNull(LineBoundary.endMsOf(listOf(line(0, 0)), 7, durationMs = null))
        assertNull(LineBoundary.endMsOf(emptyList(), 0, durationMs = null))
    }

    @Test
    fun `行索引照 LyricLine_index 找，不是陣列位置`() {
        // 從 Room 回復時用 entity.lineIndex 重建，索引可能不連續
        val lines = listOf(line(3, 1_000), line(7, 5_000))

        assertEquals(5_000L, LineBoundary.endMsOf(lines, 3, durationMs = 100_000))
    }

    // ---------- 已作答判定 ----------

    private fun question(blankIndex: Int, lineIndex: Int) = FillChoiceQuestion(
        blankIndex = blankIndex,
        lineIndex = lineIndex,
        prompt = "____",
        answer = "word",
        surface = "word",
        startMs = 0L,
    )

    @Test
    fun `沒有題目的行視為通過`() {
        assertTrue(LineBoundary.isAnswered(null, emptyMap()))
        assertNull(LineBoundary.questionOf(2, listOf(question(0, 0))))
    }

    @Test
    fun `有題目且有作答才算通過`() {
        val q = question(blankIndex = 4, lineIndex = 2)

        assertFalse(LineBoundary.isAnswered(q, emptyMap()))
        assertFalse(
            "答了別的題不算",
            LineBoundary.isAnswered(q, mapOf(3 to ChoiceRecord("x", false))),
        )
        assertTrue(
            LineBoundary.isAnswered(q, mapOf(4 to ChoiceRecord("x", false))),
        )
    }

    @Test
    fun `答錯也算已作答`() {
        val q = question(blankIndex = 0, lineIndex = 0)

        // 擁有者要求「答了就可以繼續」，對錯不影響暫停判斷
        assertTrue(LineBoundary.isAnswered(q, mapOf(0 to ChoiceRecord("wrong", false))))
    }
}
