package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.data.lang.UNLISTED_SOURCE
import com.antigravity.lyrian.data.lang.WordLevel
import com.antigravity.lyrian.data.lang.WordLevelSource
import com.antigravity.lyrian.domain.model.FillChoiceQuestion
import com.antigravity.lyrian.domain.model.CefrLevel
import com.antigravity.lyrian.domain.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * [FillChoiceBuilder] 的單元測試。
 *
 * 選詞規則本身在 `BlankSelectorTest`；這裡只驗「把選好的字變成題目」這件事：
 * 不變式（prefix + answer + suffix 能還原原句）、選項、評分。
 */
class FillChoiceBuilderTest {

    private class FakeSource(private val levels: Map<String, CefrLevel>) : WordLevelSource {
        override fun lookup(word: String): WordLevel {
            val key = word.lowercase()
            return WordLevel(key, key, levels[key], if (key in levels) "CEFR-J" else UNLISTED_SOURCE, null)
        }

        override fun isHighFrequency(word: String) = false
    }

    private val source = FakeSource(
        mapOf(
            "silhouette" to CefrLevel.C1,
            "velvet" to CefrLevel.B2,
            "whisper" to CefrLevel.B1,
        ),
    )

    private fun line(index: Int, text: String) = LyricLine(index = index, text = text, startMs = index * 1000L)

    private val sampleLines = listOf(
        line(0, "Whispering silhouettes cross the velvet sky"),
        line(1, "I keep a lantern burning"),
        line(2, "Oh oh oh"),
    )

    private fun dataset(lines: List<LyricLine> = sampleLines) = FillChoiceBuilder.buildDataset(
        lines = lines,
        difficulty = BlankDifficulty.BASIC,
        maxBlanks = BlankSelector.MAX_BLANKS,
        levelSource = source,
    )

    // ---------- 不變式 ----------

    @Test
    fun `題目能還原原始歌詞行`() {
        val result = dataset()
        assertTrue(result.questions.isNotEmpty())

        result.questions.forEach { question ->
            assertEquals(sampleLines[question.lineIndex].text, question.originalLine)
        }
    }

    @Test
    fun `每一行最多一題`() {
        val result = dataset()
        val perLine = result.questions.groupingBy { it.lineIndex }.eachCount()
        assertTrue(perLine.values.all { it == 1 })
    }

    @Test
    fun `空白位置與答案對得上歌詞`() {
        val result = dataset()
        result.questions.forEach { question ->
            val original = sampleLines[question.lineIndex].text
            assertEquals(question.answer, original.substring(question.prefix.length, original.length - question.suffix.length))
        }
    }

    @Test
    fun `被挖空的行會標上 blankStart 與 answer`() {
        val result = dataset()
        result.questions.forEach { question ->
            val blanked = result.lines[question.lineIndex]
            assertTrue("第 ${question.lineIndex} 行應該標記挖空", blanked.hasBlank)
            assertEquals(question.answer, blanked.answer)
            assertEquals(question.prefix, blanked.prefix)
        }
    }

    @Test
    fun `沒有實詞的行不會產生題目`() {
        val result = dataset()
        assertTrue(result.questions.none { it.lineIndex == 2 })
        assertEquals(sampleLines[2].text, result.lines[2].text)
    }

    @Test
    fun `偵錯記錄選中的詞與依據`() {
        val result = dataset()
        val decision = result.debug.first { it.chosenWord != null }

        assertNotNull(decision.chosenLemma)
        assertNotNull(decision.chosenLevel)
        assertNotNull(decision.basis)
    }

    @Test
    fun `沒有候選的行會記錄被過濾的字`() {
        val result = FillChoiceBuilder.buildDataset(
            lines = listOf(line(0, "The and you")),
            difficulty = BlankDifficulty.BASIC,
            maxBlanks = BlankSelector.MAX_BLANKS,
            levelSource = source,
        )

        assertTrue(result.questions.isEmpty())
        assertTrue(result.debug.single().rejected.isNotEmpty())
    }

    // ---------- 選項 ----------

    @Test
    fun `每題都有四個不重複的選項且包含正解`() {
        val result = dataset()
        val answers = result.questions.map { it.answer }

        result.questions.forEach { question ->
            val options = FillChoiceBuilder.buildOptions(question, answers, Random(question.blankIndex.toLong()))
            assertEquals(FillChoiceBuilder.OPTION_COUNT, options.size)
            assertEquals(options.size, options.distinct().size)
            assertTrue(options.any { it.equals(question.answer, ignoreCase = true) })
        }
    }

    @Test
    fun `選項不足時用備援字庫補齊`() {
        val question = FillChoiceQuestion(
            blankIndex = 0,
            lineIndex = 0,
            prefix = "",
            answer = "silhouette",
            suffix = "",
            startMs = 0,
        )
        val options = FillChoiceBuilder.buildOptions(question, emptyList(), Random(1))
        assertEquals(4, options.size)
    }

    // ---------- 評分 ----------

    @Test
    fun `答案比對不分大小寫與前後空白`() {
        val question = FillChoiceQuestion(0, 0, "", "Velvet", "", 0)
        assertTrue(FillChoiceBuilder.grade(question, "  velvet ").isCorrect)
        assertTrue(!FillChoiceBuilder.grade(question, "silhouette").isCorrect)
    }

    @Test
    fun `答錯才會有字庫候選`() {
        val question = FillChoiceQuestion(0, 0, "", "velvet", "", 0)
        val wrong = FillChoiceBuilder.grade(question, "silk")
        val right = FillChoiceBuilder.grade(question, "velvet")

        assertNotNull(FillChoiceBuilder.vocabCandidateFrom(question, wrong))
        assertEquals(null, FillChoiceBuilder.vocabCandidateFrom(question, right))
    }

    // ---------- 計數 ----------

    @Test
    fun `可挖行數與實際題數一致`() {
        val count = FillChoiceBuilder.countBlankableLines(
            lines = sampleLines,
            difficulty = BlankDifficulty.BASIC,
            maxBlanks = BlankSelector.MAX_BLANKS,
            levelSource = source,
        )
        assertEquals(dataset().questions.size, count)
    }

    @Test
    fun `只有五句時題數上限是五`() {
        // 每句一個不同的字，否則跨句去重會讓後面的句子沒得挖
        val words = listOf(
            "amber", "blaze", "cider", "dune", "ember", "frost", "glow", "haze",
            "iris", "jade", "karma", "lunar", "mist", "nova", "onyx", "prism",
            "quartz", "raven", "sable", "tide",
        )
        val manySource = FakeSource(words.associateWith { CefrLevel.B2 })
        val many = words.mapIndexed { index, word -> line(index, "$word here") }

        val result = FillChoiceBuilder.buildDataset(
            lines = many,
            difficulty = BlankDifficulty.BASIC,
            maxBlanks = 5,
            levelSource = manySource,
        )
        assertEquals(5, result.questions.size)
    }
}
