package com.antigravity.lyrian.domain.practice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [BlankCountPolicy] 的邊界。
 *
 * 這是擁有者 2026-10 核定的「保量」政策：
 * ```
 * 題數 = min(maxBlanks, max(20, round(可用句數 × 0.6)), 可用句數)
 * ```
 */
class BlankCountPolicyTest {

    @Test
    fun `沒有合格句就是 0 題`() {
        assertEquals(0, BlankCountPolicy.target(0, 40))
        assertEquals(0, BlankCountPolicy.target(-3, 40))
        assertEquals("上限 0 也不能出題", 0, BlankCountPolicy.target(30, 0))
    }

    @Test
    fun `句子不夠就全出`() {
        // 17 句 → 0.6 倍只有 10，但句子就這麼多
        assertEquals(17, BlankCountPolicy.target(17, 40))
        assertEquals(1, BlankCountPolicy.target(1, 40))
    }

    @Test
    fun `下限 20 優先於比例`() {
        // 22 句 × 0.6 = 13 → 補到 20
        assertEquals(20, BlankCountPolicy.target(22, 40))
        // 33 句 × 0.6 = 20（剛好）
        assertEquals(20, BlankCountPolicy.target(33, 40))
    }

    @Test
    fun `句子多時按比例拉高`() {
        assertEquals(24, BlankCountPolicy.target(40, 40))
        assertEquals(28, BlankCountPolicy.target(47, 40))
    }

    @Test
    fun `maxBlanks 是上限`() {
        // 100 句 × 0.6 = 60，但使用者只設 25
        assertEquals(25, BlankCountPolicy.target(100, 25))
        // 使用者設得比下限還低 → 以上限為準（不強迫 20）
        assertEquals(10, BlankCountPolicy.target(100, 10))
    }

    @Test
    fun `實際歌詞規模的樣貌`() {
        // 六首歌的合格句數（見 BlankReportTest）：目標固定 20 起跳，
        // 實際題數可能更少——那是 lemma 去重與補題品質底線造成的，不是政策。
        assertEquals(20, BlankCountPolicy.target(34, 40)) // Hello（實際 20）
        assertEquals(28, BlankCountPolicy.target(47, 40)) // expectations（實際 28）
        assertEquals(20, BlankCountPolicy.target(29, 40)) // Yellow（實際 17）
        assertEquals(23, BlankCountPolicy.target(39, 40)) // Someone Like You（實際 23）
        assertEquals(20, BlankCountPolicy.target(22, 40)) // The Scientist（實際 18）
        assertEquals(25, BlankCountPolicy.target(42, 40)) // drivers license（實際 25）
    }
}
