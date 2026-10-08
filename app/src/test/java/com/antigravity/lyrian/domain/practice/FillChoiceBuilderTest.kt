package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.domain.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * [FillChoiceBuilder] 的單元測試。
 *
 * 隨機行為一律注入固定 seed 的 [Random]，確保結果可重現。
 */
class FillChoiceBuilderTest {

    private fun line(index: Int, text: String, startMs: Long = index * 1_000L, endMs: Long? = null) =
        LyricLine(index = index, text = text, startMs = startMs, endMs = endMs)

    /** 參考圖的四行歌詞。 */
    private val sampleLines = listOf(
        line(0, "The morning paints the sky in gold"),
        line(1, "A little love is ours to hold"),
        line(2, "I keep a little hope inside my heart"),
        line(3, "A brand-new day, a brand-new start"),
    )

    // ---------- findBlankCandidates ----------

    @Test
    fun `預設找出長度大於等於 4 的純字母單字`() {
        val words = FillChoiceBuilder
            .findBlankCandidates("A little love is ours to hold")
            .map { it.word }

        assertEquals(listOf("little", "love", "ours", "hold"), words)
    }

    @Test
    fun `回報正確的起訖位置`() {
        val text = "I keep a little hope inside"
        val candidate = FillChoiceBuilder.findBlankCandidates(text, minWordLength = 6).first()

        assertEquals("little", candidate.word)
        assertEquals("little", text.substring(candidate.start, candidate.end))
    }

    @Test
    fun `minWordLength 可調高以限制候選`() {
        val words = FillChoiceBuilder
            .findBlankCandidates("A little love is ours to hold", minWordLength = 6)
            .map { it.word }

        assertEquals(listOf("little"), words)
    }

    @Test
    fun `忽略縮寫造成的短片段`() {
        assertEquals(emptyList<FillChoiceBuilder.BlankCandidate>(),
            FillChoiceBuilder.findBlankCandidates("it's a don't"))
    }

    @Test
    fun `數字與標點不算單字`() {
        assertEquals(emptyList<FillChoiceBuilder.BlankCandidate>(),
            FillChoiceBuilder.findBlankCandidates("12345 --- 6789 !!"))
    }

    @Test
    fun `沒有合格單字時回傳空清單`() {
        assertEquals(emptyList<FillChoiceBuilder.BlankCandidate>(),
            FillChoiceBuilder.findBlankCandidates("a b c"))
        assertEquals(emptyList<FillChoiceBuilder.BlankCandidate>(),
            FillChoiceBuilder.findBlankCandidates(""))
    }

    // ---------- pickEvenlySpaced ----------

    @Test
    fun `取樣數量大於等於長度時回傳全部`() {
        assertEquals(listOf(1, 2, 3), FillChoiceBuilder.pickEvenlySpaced(listOf(1, 2, 3), 5))
        assertEquals(listOf(1, 2, 3), FillChoiceBuilder.pickEvenlySpaced(listOf(1, 2, 3), 3))
    }

    @Test
    fun `取樣 0 或空清單回傳空清單`() {
        assertEquals(
            emptyList<Int>(),
            FillChoiceBuilder.pickEvenlySpaced(listOf(1, 2, 3), 0),
        )
        assertEquals(
            emptyList<Int>(),
            FillChoiceBuilder.pickEvenlySpaced(emptyList<Int>(), 3),
        )
    }

    @Test
    fun `取樣 1 個時取第一個`() {
        assertEquals(listOf(1), FillChoiceBuilder.pickEvenlySpaced(listOf(1, 2, 3, 4), 1))
    }

    @Test
    fun `涵蓋頭尾且數量正確`() {
        val picked = FillChoiceBuilder.pickEvenlySpaced((0..9).toList(), 5)
        assertEquals(5, picked.size)
        assertEquals(0, picked.first())
        assertEquals(9, picked.last())
    }

    @Test
    fun `結果遞增且不重複`() {
        val picked = FillChoiceBuilder.pickEvenlySpaced((0 until 48).toList(), 10)
        assertEquals(picked.sorted(), picked)
        assertEquals(picked.size, picked.toSet().size)
    }

    // ---------- buildDataset ----------

    @Test
    fun `每行最多挖一個空`() {
        val dataset = FillChoiceBuilder.buildDataset(sampleLines, Random(1))
        val perLine = dataset.questions.groupingBy { it.lineIndex }.eachCount()
        assertTrue(perLine.values.all { it == 1 })
    }

    @Test
    fun `blankIndex 從 0 連續遞增`() {
        val questions = FillChoiceBuilder.buildDataset(sampleLines, Random(3)).questions
        assertEquals(questions.indices.toList(), questions.map { it.blankIndex })
    }

    @Test
    fun `prefix 加 answer 加 suffix 可還原原句`() {
        // 不變式：對多個 seed 都必須成立
        for (seed in 1..20) {
            val dataset = FillChoiceBuilder.buildDataset(sampleLines, Random(seed))
            for (q in dataset.questions) {
                assertEquals(sampleLines[q.lineIndex].text, q.originalLine)
            }
        }
    }

    @Test
    fun `答案長度符合門檻`() {
        val questions = FillChoiceBuilder.buildDataset(sampleLines, Random(5)).questions
        assertTrue(questions.all { it.answer.length >= FillChoiceBuilder.BLANK_MIN_WORD_LENGTH })
    }

    @Test
    fun `沒有合格單字的行不產生題目但仍在 lines 中`() {
        val dataset = FillChoiceBuilder.buildDataset(
            listOf(line(0, "a b c"), line(1, "wonderful")),
            Random(1),
        )

        assertEquals(2, dataset.lines.size)
        assertEquals(1, dataset.questions.size)
        assertEquals(1, dataset.questions[0].lineIndex)
    }

    @Test
    fun `保留該行的時間軸`() {
        val questions = FillChoiceBuilder
            .buildDataset(listOf(line(0, "wonderful night", 5_000L, 9_000L)), Random(1))
            .questions

        assertEquals(5_000L, questions[0].startMs)
        assertEquals(9_000L, questions[0].endMs)
    }

    @Test
    fun `預設上限為 10 題`() {
        val manyLines = (0 until 48).map { line(it, "wonderful morning number $it") }
        val questions = FillChoiceBuilder.buildDataset(manyLines, Random(1)).questions

        assertEquals(FillChoiceBuilder.DEFAULT_MAX_BLANKS, questions.size)
        assertEquals(10, questions.size)
    }

    @Test
    fun `maxBlanks 可覆寫`() {
        val manyLines = (0 until 48).map { line(it, "wonderful morning number $it") }
        val questions = FillChoiceBuilder.buildDataset(manyLines, Random(1), maxBlanks = 3).questions

        assertEquals(3, questions.size)
    }

    @Test
    fun `可挖空的行少於上限時全部挖空`() {
        val few = listOf(line(0, "wonderful morning"), line(1, "another lovely day"))
        assertEquals(2, FillChoiceBuilder.buildDataset(few, Random(1)).questions.size)
    }

    @Test
    fun `取樣涵蓋整首歌而不是只挖前半段`() {
        val manyLines = (0 until 48).map { line(it, "wonderful morning number $it") }
        val questions = FillChoiceBuilder.buildDataset(manyLines, Random(1)).questions

        assertEquals(0, questions.first().lineIndex)
        assertEquals(47, questions.last().lineIndex)
    }

    // ---------- countBlankableLines ----------

    @Test
    fun `countBlankableLines 受上限限制`() {
        val manyLines = (0 until 48).map { line(it, "wonderful morning number $it") }
        assertEquals(10, FillChoiceBuilder.countBlankableLines(manyLines))
        assertEquals(3, FillChoiceBuilder.countBlankableLines(manyLines, maxBlanks = 3))
    }

    @Test
    fun `countBlankableLines 沒有可挖空的行時為 0`() {
        assertEquals(0, FillChoiceBuilder.countBlankableLines(listOf(line(0, "a b c"))))
    }

    // ---------- buildOptions ----------

    private val sampleQuestion = FillChoiceBuilder
        .buildDataset(sampleLines, Random(1)).questions.first()

    @Test
    fun `永遠產生 4 個選項`() {
        assertEquals(4, FillChoiceBuilder.buildOptions(sampleQuestion, emptyList(), Random(1)).size)
    }

    @Test
    fun `正解恰好出現一次`() {
        val options = FillChoiceBuilder.buildOptions(sampleQuestion, listOf("love", "heart", "hello"), Random(2))
        val normalized = options.map { FillChoiceBuilder.normalizeAnswer(it) }
        assertEquals(1, normalized.count { it == FillChoiceBuilder.normalizeAnswer(sampleQuestion.answer) })
    }

    @Test
    fun `選項彼此相異`() {
        val options = FillChoiceBuilder.buildOptions(
            sampleQuestion,
            listOf("love", "LOVE", "heart", "hello"),
            Random(4),
        )
        val normalized = options.map { FillChoiceBuilder.normalizeAnswer(it) }
        assertEquals(normalized.size, normalized.toSet().size)
    }

    @Test
    fun `同歌答案不足時由備援字庫補滿`() {
        val options = FillChoiceBuilder.buildOptions(sampleQuestion, listOf("love"), Random(8))
        assertEquals(4, options.size)
        assertTrue(options.contains("love"))
        assertTrue(options.any { FillChoiceBuilder.normalizeAnswer(it) == FillChoiceBuilder.normalizeAnswer(sampleQuestion.answer) })
    }

    @Test
    fun `相同 seed 產生相同選項`() {
        val a = FillChoiceBuilder.buildOptions(sampleQuestion, emptyList(), Random(11))
        val b = FillChoiceBuilder.buildOptions(sampleQuestion, emptyList(), Random(11))
        assertEquals(a, b)
    }

    // ---------- grade ----------

    @Test
    fun `答對時 score 為 1`() {
        val attempt = FillChoiceBuilder.grade(sampleQuestion, sampleQuestion.answer)
        assertTrue(attempt.isCorrect)
        assertEquals(1f, attempt.score, 0f)
    }

    @Test
    fun `比對時忽略大小寫與空白`() {
        val attempt = FillChoiceBuilder.grade(sampleQuestion, "  ${sampleQuestion.answer.uppercase()}  ")
        assertTrue(attempt.isCorrect)
    }

    @Test
    fun `答錯時 score 為 0 並保留正確答案`() {
        val attempt = FillChoiceBuilder.grade(sampleQuestion, "zzzz")
        assertEquals(false, attempt.isCorrect)
        assertEquals(0f, attempt.score, 0f)
        assertEquals(sampleQuestion.answer, attempt.correctAnswer)
    }

    @Test
    fun `prompt 保留空格前後文`() {
        val attempt = FillChoiceBuilder.grade(sampleQuestion, "zzzz")
        assertEquals("${sampleQuestion.prefix}____${sampleQuestion.suffix}", attempt.prompt)
    }

    // ---------- vocabCandidateFrom ----------

    @Test
    fun `答對時不收藏`() {
        val question = sampleQuestion
        val attempt = FillChoiceBuilder.grade(question, question.answer)
        assertNull(FillChoiceBuilder.vocabCandidateFrom(question, attempt))
    }

    @Test
    fun `答錯時回傳收藏目標`() {
        val question = sampleQuestion
        val attempt = FillChoiceBuilder.grade(question, "zzzz")
        val candidate = FillChoiceBuilder.vocabCandidateFrom(question, attempt, songId = 42L)

        assertNotNull(candidate)
        assertEquals(question.answer.lowercase(), candidate!!.lemma)
        assertEquals(question.answer, candidate.surfaceForm)
        assertEquals(question.originalLine, candidate.contextLine)
        assertEquals(42L, candidate.songId)
    }

    // ---------- Progress ----------

    @Test
    fun `Progress 計算百分比`() {
        assertEquals(20, com.antigravity.lyrian.domain.model.Progress(correct = 2, total = 10).percent)
        assertEquals(100, com.antigravity.lyrian.domain.model.Progress(correct = 3, total = 3).percent)
        assertEquals(0, com.antigravity.lyrian.domain.model.Progress(correct = 0, total = 0).percent)
    }
}
