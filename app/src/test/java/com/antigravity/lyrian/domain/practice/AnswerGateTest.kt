package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.domain.practice.AnswerGate.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 作答守門。
 *
 * 這個測試擋的是擁有者回報的「連點兩下會答錯下一題」——
 * 那不是「重複點擊」而已：第一下之後畫面立刻換成下一題的選項，
 * 第二下會打在新題目的格子上，被記成一個**錯答**（寫進 attempt、
 * 拉低正確率、甚至讓下一題直接算答完）。
 */
class AnswerGateTest {

    private fun evaluate(
        target: Int,
        active: Int,
        answered: Set<Int> = emptySet(),
        nowMs: Long = 10_000L,
        lastAnswerAtMs: Long = 0L,
    ) = AnswerGate.evaluate(
        target = target,
        active = active,
        answered = answered,
        nowMs = nowMs,
        lastAnswerAtMs = lastAnswerAtMs,
    )

    @Test
    fun `正常作答被接受`() {
        assertEquals(Decision.Accept, evaluate(target = 5, active = 5))
    }

    @Test
    fun `同一題再點一次只算一次`() {
        // 真正的重複點擊：這一題已經在 selections 裡
        assertEquals(
            Decision.AlreadyAnswered,
            evaluate(target = 5, active = 5, answered = setOf(5)),
        )
    }

    @Test
    fun `換題後極短時間內的點擊是殘點`() {
        // 150ms 前剛答完 blank 5，現在焦點是 blank 6，手指還停在同一個格子
        assertEquals(
            Decision.TooSoon,
            evaluate(
                target = 6,
                active = 6,
                answered = setOf(5),
                nowMs = 10_150L,
                lastAnswerAtMs = 10_000L,
            ),
        )
    }

    @Test
    fun `超過防抖時間就可以作答`() {
        assertEquals(
            Decision.Accept,
            evaluate(
                target = 6,
                active = 6,
                answered = setOf(5),
                nowMs = 10_000L + AnswerGate.DEBOUNCE_MS,
                lastAnswerAtMs = 10_000L,
            ),
        )
    }

    @Test
    fun `舊格子的點擊一定被拒（不論時間）`() {
        // 交叉淡入期間舊格還在畫面上；它的 blankIndex 已經不是現在的題目。
        // 時間給得很寬鬆，證明擋住它的是 StaleGrid 而不是防抖。
        assertEquals(
            Decision.StaleGrid,
            evaluate(
                target = 4,
                active = 5,
                answered = emptySet(),
                nowMs = 99_000L,
                lastAnswerAtMs = 0L,
            ),
        )
        // 舊格子若已經答過，理由會是 AlreadyAnswered —— 一樣拒收，只是原因不同。
        assertEquals(
            Decision.AlreadyAnswered,
            evaluate(
                target = 4,
                active = 5,
                answered = setOf(4),
                nowMs = 99_000L,
                lastAnswerAtMs = 10_000L,
            ),
        )
    }

    @Test
    fun `還沒作答過時不受防抖影響`() {
        assertEquals(
            Decision.Accept,
            evaluate(target = 0, active = 0, nowMs = 1L, lastAnswerAtMs = 0L),
        )
    }

    @Test
    fun `已作答的判定優先於殘點判定`() {
        // 同一題被連點兩下：第二下要回 AlreadyAnswered（不會寫 log、不會重複 persist）
        assertEquals(
            Decision.AlreadyAnswered,
            evaluate(
                target = 5,
                active = 5,
                answered = setOf(5),
                nowMs = 10_001L,
                lastAnswerAtMs = 10_000L,
            ),
        )
    }

    @Test
    fun `防抖視窗的邊界是左閉右開`() {
        val justBefore = 10_000L + AnswerGate.DEBOUNCE_MS - 1
        val atBoundary = 10_000L + AnswerGate.DEBOUNCE_MS

        assertEquals(
            Decision.TooSoon,
            evaluate(target = 6, active = 6, nowMs = justBefore, lastAnswerAtMs = 10_000L),
        )
        assertEquals(
            Decision.Accept,
            evaluate(target = 6, active = 6, nowMs = atBoundary, lastAnswerAtMs = 10_000L),
        )
    }
}
