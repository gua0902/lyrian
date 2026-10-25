package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.domain.model.CefrLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [WordValue] 的單元測試。
 *
 * 這些數字直接決定「哪一個字被挖」，所以每一項都要能被驗證：
 * 等級契合、稀缺、詞性、詞形、扣分、夾制。
 */
class WordValueTest {

    @Test
    fun `等級契合以難度目標為中心`() {
        // ADVANCED 的目標是 B2
        assertEquals(30, WordValue.levelFit(CefrLevel.B2, BlankDifficulty.ADVANCED))
        assertEquals(20, WordValue.levelFit(CefrLevel.B1, BlankDifficulty.ADVANCED))
        assertEquals(20, WordValue.levelFit(CefrLevel.C1, BlankDifficulty.ADVANCED))
        assertEquals(0, WordValue.levelFit(CefrLevel.A1, BlankDifficulty.ADVANCED))

        // BASIC 的目標是 A2
        assertEquals(30, WordValue.levelFit(CefrLevel.A2, BlankDifficulty.BASIC))
        assertEquals(10, WordValue.levelFit(CefrLevel.B2, BlankDifficulty.BASIC))
    }

    @Test
    fun `未知等級落在中段而不是最高`() {
        // 擁有者要求：查不到等級「不得一律視為最高難度」
        assertEquals(WordValue.UNKNOWN_LEVEL_FIT, WordValue.levelFit(null, BlankDifficulty.ADVANCED))
        assertTrue(
            "未知等級的分數必須低於目標等級",
            WordValue.levelFit(null, BlankDifficulty.ADVANCED) <
                WordValue.levelFit(CefrLevel.B2, BlankDifficulty.ADVANCED),
        )
    }

    @Test
    fun `稀缺度隨排名遞增且未知為中段`() {
        assertEquals(WordValue.UNKNOWN_RARITY, WordValue.rarity(null))
        assertEquals(WordValue.UNKNOWN_RARITY, WordValue.rarity(0))
        assertTrue(WordValue.rarity(5000) < WordValue.rarity(20000))
        assertTrue(WordValue.rarity(20000) < WordValue.rarity(29000))
        assertTrue("上限 25", WordValue.rarity(99_999) <= 25)
    }

    @Test
    fun `實詞分數高於虛詞`() {
        assertEquals(20, WordValue.posValue("n."))
        assertEquals(20, WordValue.posValue("v."))
        assertEquals(20, WordValue.posValue("a."))
        assertEquals(20, WordValue.posValue("ad."))
        assertEquals(6, WordValue.posValue("prep."))
        assertEquals(6, WordValue.posValue("pron."))
        assertEquals("詞性未知 → 中性", 10, WordValue.posValue(null))
    }

    @Test
    fun `原形比派生與屈折更值得學`() {
        val base = WordValue.formValue("silence", "silence")
        val derived = WordValue.formValue("consideration", "consideration")
        val inflected = WordValue.formValue("convinced", "convince")

        assertEquals(15, base)
        assertTrue("派生詞應該在原形之下", derived < base)
        assertTrue("屈折變化最低", inflected < derived)
    }

    @Test
    fun `扣分只針對特定情況`() {
        assertEquals(0, WordValue.penalty(9000, capitalizedOnly = false, occurrences = 2))
        assertEquals(15, WordValue.penalty(9000, capitalizedOnly = true, occurrences = 2))
        assertEquals(10, WordValue.penalty(1500, capitalizedOnly = false, occurrences = 1))
        assertEquals(5, WordValue.penalty(9000, capitalizedOnly = false, occurrences = 4))
    }

    @Test
    fun `分數不會超出範圍`() {
        val best = WordValue.score(
            level = CefrLevel.B2,
            rank = 29_000,
            partOfSpeech = "n.",
            surface = "silence",
            lemma = "silence",
            difficulty = BlankDifficulty.ADVANCED,
        )
        assertTrue("best=$best", best in 0..WordValue.MAX)

        val worst = WordValue.score(
            level = CefrLevel.A1,
            rank = 600,
            partOfSpeech = "pron.",
            surface = "Highs",
            lemma = "high",
            difficulty = BlankDifficulty.ADVANCED,
            capitalizedOnly = true,
            occurrences = 9,
        )
        assertTrue("worst=$worst", worst >= 0)
        assertTrue("A1 的常見字應該很低", worst < 30)
    }
}
