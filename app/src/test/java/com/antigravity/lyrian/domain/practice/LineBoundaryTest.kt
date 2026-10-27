package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.domain.model.ChoiceRecord
import com.antigravity.lyrian.domain.model.FillChoiceQuestion
import com.antigravity.lyrian.domain.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // ---------- 「這一行還有哪一題沒答」 ----------

    private fun question(blankIndex: Int, lineIndex: Int) = FillChoiceQuestion(
        blankIndex = blankIndex,
        lineIndex = lineIndex,
        prompt = "____",
        answer = "word",
        surface = "word",
        startMs = 0L,
    )

    private fun answered(vararg blankIndexes: Int): Map<Int, ChoiceRecord> =
        blankIndexes.associateWith { ChoiceRecord("x", true) }

    @Test
    fun `沒有題目的行回傳 null`() {
        assertNull(
            LineBoundary.firstUnansweredOf(2, listOf(question(0, 0)), emptyMap()),
        )
    }

    @Test
    fun `同一句兩個空：答了第一個就要回傳第二個`() {
        // 擁有者回報的「填完第一個空就卡住」就是這裡：舊版回傳的是這一行
        // 第一題（已作答），於是輪詢每 50ms 把焦點拉回去。
        val questions = listOf(question(0, 2), question(1, 2), question(2, 5))

        val next = LineBoundary.firstUnansweredOf(
            lineIndex = 2,
            questions = questions,
            selections = answered(0),
        )

        assertEquals(1, next?.blankIndex)
    }

    @Test
    fun `整句都答完時回傳 null`() {
        // null 的語意是「不要動焦點」：呼叫端不得把已作答的題目重新端上來。
        val questions = listOf(question(0, 2), question(1, 2))

        assertNull(
            LineBoundary.firstUnansweredOf(2, questions, answered(0, 1)),
        )
    }

    @Test
    fun `別的行作答不影響這一行的判定`() {
        val questions = listOf(question(4, 2), question(3, 7))

        // 只有 line 7 的 blank 3 被答過 → line 2 仍有未答的 blank 4
        assertEquals(
            4,
            LineBoundary.firstUnansweredOf(2, questions, answered(3))?.blankIndex,
        )
        // blankIndex 是**全曲序號**，不是行內序號：line 2 的題目是 4 不是 3
        assertNull(
            LineBoundary.firstUnansweredOf(7, questions, answered(3)),
        )
    }

    @Test
    fun `答錯也算已作答`() {
        // 擁有者要求「答了就可以繼續」，對錯不影響暫停判斷
        val questions = listOf(question(0, 0))

        assertNull(
            LineBoundary.firstUnansweredOf(0, questions, mapOf(0 to ChoiceRecord("wrong", false))),
        )
    }
}
