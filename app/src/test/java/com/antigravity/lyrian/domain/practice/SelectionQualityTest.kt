package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.data.lang.UNLISTED_SOURCE
import com.antigravity.lyrian.data.lang.WordLevel
import com.antigravity.lyrian.data.lang.WordLevelSource
import com.antigravity.lyrian.domain.model.CefrLevel
import com.antigravity.lyrian.domain.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 新選詞系統的驗收測試。
 *
 * 守的是擁有者這一輪的要求：
 * ```
 * 1. 歌名本身（Hello / Yellow）不挖
 * 2. 縮寫（time's）不挖
 * 3. 不值得學的字（A1 常見字）不會優先於 B1/B2 的實詞
 * 4. 題數不足時仍保證至少 MIN_BLANKS（真的沒字才少）
 * 5. 分段取最佳：每一段挑分數最高的那一句（分散度不變）
 * ```
 */
class SelectionQualityTest {

    /** 用手寫的等級表驅動，才能精確組合分數。 */
    private class FakeSource(private val table: Map<String, WordLevel>) : WordLevelSource {
        override fun lookup(word: String): WordLevel {
            val key = word.lowercase()
            return table[key] ?: WordLevel(key, key, null, UNLISTED_SOURCE)
        }

        override fun isHighFrequency(word: String): Boolean = false
    }

    private fun level(
        word: String,
        level: CefrLevel,
        rank: Int,
        pos: String,
        lemma: String = word,
    ) = WordLevel(
        surface = word,
        lemma = lemma,
        level = level,
        source = "CEFR-J",
        frequencyRank = rank,
        surfaceRank = rank,
        partOfSpeech = pos,
    )

    private fun line(index: Int, text: String) = LyricLine(index = index, text = text, startMs = index * 10_000L)

    private fun source(vararg words: WordLevel) = FakeSource(words.associateBy { it.surface })

    // ---------- 過濾 ----------

    @Test
    fun `歌名本身就是歌詞裡的字時不挖`() {
        val lines = listOf(line(0, "hello from the other side"))
        val lang = source(
            level("hello", CefrLevel.A1, 2319, "int."),
            level("other", CefrLevel.A1, 300, "a."),
            level("side", CefrLevel.A2, 1200, "n."),
        )

        val selections = BlankSelector.select(lines, BlankDifficulty.ADVANCED, 5, lang, songTitle = "Hello")

        val chosen = selections.mapNotNull { it.chosen?.word }
        assertFalse("hello 是歌名，不該挖：$chosen", chosen.any { it.equals("hello", true) })
        val rejected = selections.flatMap { it.rejected }.map { it.word to it.levelSource }
        assertTrue(
            "hello 要被標成標題詞：$rejected",
            rejected.any { it.first.equals("hello", true) && it.second == "歌詞標題詞" },
        )
    }

    @Test
    fun `縮寫不挖`() {
        val lines = listOf(line(0, "time's up and moving on"))
        val lang = source(
            level("time's", CefrLevel.A1, 654, "n."),
            level("moving", CefrLevel.A2, 1500, "a.", lemma = "move"),
        )

        val selections = BlankSelector.select(lines, BlankDifficulty.ADVANCED, 5, lang)
        val chosen = selections.mapNotNull { it.chosen?.word }

        assertTrue("縮寫不該被挖：$chosen", chosen.none { it.contains("'") })
        assertTrue(
            "縮寫要被標成原因",
            selections.flatMap { it.rejected }.any { it.levelSource == "縮寫" },
        )
    }

    // ---------- 分數決定選哪一句 ----------

    @Test
    fun `高分句子優先於低分句子`() {
        val lines = listOf(
            line(0, "sorry sorry sorry sorry sorry"),   // A1、常見、重複 → 低分
            line(1, "desperate measures indeed"),        // B1 實詞 → 高分
        )
        val lang = source(
            level("sorry", CefrLevel.A1, 870, "a."),
            level("desperate", CefrLevel.B1, 2935, "a."),
            level("measures", CefrLevel.B2, 3000, "n.", lemma = "measure"),
        )

        val selections = BlankSelector.select(lines, BlankDifficulty.ADVANCED, 1, lang)
        val chosen = selections.mapNotNull { it.chosen?.word }

        assertEquals(1, chosen.size)
        assertTrue("要選高分那一句而不是 sorry：$chosen", chosen.first() != "sorry")
    }

    @Test
    fun `每句最多一個空且跨句原形不重複`() {
        val lines = listOf(
            line(0, "silence falls"),
            line(1, "silence again"),
        )
        val lang = source(level("silence", CefrLevel.B2, 8000, "n."))

        val selections = BlankSelector.select(lines, BlankDifficulty.ADVANCED, 5, lang)
        val chosen = selections.mapNotNull { it.chosen?.word }

        assertEquals("同一個原形全曲只挖一次", 1, chosen.count { it.equals("silence", true) })
    }

    // ---------- 題數與地板 ----------

    @Test
    fun `地板太高時仍至少出五題`() {
        // 全部都是 A1 常見字 → 分數低於地板；系統要逐段降地板以湊到 5 題
        // 每句一個不同的 A1 常見字（同一個原形會被跨句去重）
        val words = listOf("sorry", "happy", "angry", "funny", "lucky", "sunny", "early", "quiet")
        val lines = words.mapIndexed { index, word -> line(index, word) }
        val lang = source(*words.map { level(it, CefrLevel.A1, 870, "a.") }.toTypedArray())

        val selections = BlankSelector.select(lines, BlankDifficulty.CHALLENGE, 40, lang)
        val count = selections.count { it.chosen != null }

        assertTrue(
            "地板要降到至少湊出 MIN_BLANKS，實際=$count",
            count >= BlankSelector.MIN_BLANKS && count <= words.size,
        )
    }

    @Test
    fun `候選不足時不會硬湊題數`() {
        val lines = listOf(
            line(0, "the and of"),           // 全是功能詞
            line(1, "a an the"),             // 全是冠詞
        )
        val lang = source()

        val selections = BlankSelector.select(lines, BlankDifficulty.ADVANCED, 40, lang)

        assertEquals(0, selections.count { it.chosen != null })
    }

    // ---------- 分段取最佳 ----------

    @Test
    fun `分段取分數最高的一句`() {
        val indices = listOf(0, 1, 2, 3)
        val scores = mapOf(0 to 10, 1 to 50, 2 to 20, 3 to 40)

        val picked = BlankSelector.pickBySegment(indices, scores, 2)

        assertEquals(setOf(1, 3), picked)
    }

    @Test
    fun `分段數量大於句子數時全取`() {
        val picked = BlankSelector.pickBySegment(listOf(0, 1), mapOf(0 to 1, 1 to 2), 10)

        assertEquals(setOf(0, 1), picked)
    }

    @Test
    fun `選擇結果保持歌詞順序`() {
        val words = listOf("silence", "lantern", "velvet", "harbor", "thunder", "meadow")
        val lines = words.mapIndexed { index, word -> line(index, word) }
        val lang = source(*words.map { level(it, CefrLevel.B2, 8000, "n.") }.toTypedArray())

        val selections = BlankSelector.select(lines, BlankDifficulty.ADVANCED, 3, lang)
        val lineIndexes = selections.filter { it.chosen != null }.map { it.line.index }

        assertEquals(lineIndexes.sorted(), lineIndexes)
        assertTrue("題目要分散而不是擠在一起：$lineIndexes", lineIndexes.last() - lineIndexes.first() >= 3)
    }
}
