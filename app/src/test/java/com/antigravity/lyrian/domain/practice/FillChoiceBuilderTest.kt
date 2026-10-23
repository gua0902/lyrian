package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.data.lang.UNLISTED_SOURCE
import com.antigravity.lyrian.domain.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * [FillChoiceBuilder] 的單元測試。
 *
 * 隨機行為一律注入固定 seed 的 [Random]，確保結果可重現。
 *
 * ## 測試用歌詞為什麼用「罕見詞」
 *
 * 挖空現在會先過濾 **Top 1000 高頻詞**（見 [CommonWordFilter]），
 * 所以 `love` / `hope` / `night` 這類字已經不能挖。測試字串因此改用
 * 榜外的字，才驗證得到「可挖空」那條路徑。
 */
class FillChoiceBuilderTest {

    private fun line(index: Int, text: String, startMs: Long = index * 1_000L, endMs: Long? = null) =
        LyricLine(index = index, text = text, startMs = startMs, endMs = endMs)

    /** 四行歌詞，每行都含至少一個榜外的字（可挖空）。 */
    private val sampleLines = listOf(
        line(0, "Whispering silhouettes cross the velvet sky"),
        line(1, "A fragile melody begins to tremble"),
        line(2, "I keep a lantern burning in the hollow"),
        line(3, "Crimson echoes linger on the carousel"),
    )

    // ---------- findBlankCandidates ----------

    @Test
    fun `找出長度足夠且不是高頻詞的單字`() {
        val words = FillChoiceBuilder
            .findBlankCandidates("The whispering silhouettes and you")
            .map { it.word }

        // "The" 冠詞、"and" 高頻詞、"you" 代名詞 → 全部過濾
        assertEquals(listOf("whispering", "silhouettes"), words)
    }

    @Test
    fun `回報正確的起訖位置`() {
        val text = "I keep a lantern burning"
        val candidate = FillChoiceBuilder.findBlankCandidates(text, minWordLength = 6).first()

        assertEquals("lantern", candidate.word)
        assertEquals("lantern", text.substring(candidate.start, candidate.end))
    }

    @Test
    fun `minWordLength 可調高以限制候選`() {
        val words = FillChoiceBuilder
            .findBlankCandidates("Whispering silhouettes cross the velvet sky", minWordLength = 10)
            .map { it.word }

        assertEquals(listOf("Whispering", "silhouettes"), words)
    }

    @Test
    fun `縮寫展開後照樣被過濾`() {
        // don't → do（be/do/have）、it's → it（代名詞）→ 都不是候選
        assertEquals(
            emptyList<FillChoiceBuilder.BlankCandidate>(),
            FillChoiceBuilder.findBlankCandidates("don't it's"),
        )
    }

    @Test
    fun `數字與標點不算單字`() {
        assertEquals(
            emptyList<FillChoiceBuilder.BlankCandidate>(),
            FillChoiceBuilder.findBlankCandidates("12345 --- 6789 !!"),
        )
    }

    @Test
    fun `沒有合格單字時回傳空清單`() {
        assertEquals(
            emptyList<FillChoiceBuilder.BlankCandidate>(),
            FillChoiceBuilder.findBlankCandidates("a b c"),
        )
        assertEquals(
            emptyList<FillChoiceBuilder.BlankCandidate>(),
            FillChoiceBuilder.findBlankCandidates(""),
        )
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
            listOf(line(0, "a b c"), line(1, "silhouette")),
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
    fun `可挖空的行少於上限時全部挖空`() {
        val few = listOf(line(0, "wonderful midnight"), line(1, "another silhouette"))
        assertEquals(2, FillChoiceBuilder.buildDataset(few, Random(1)).questions.size)
    }

    @Test
    fun `每一句都挖一個空（不設上限、不跳著挖）`() {
        // 擁有者指定：每句都挖，除非整句沒有可挖的字。
        val manyLines = (0 until 48).map { line(it, "silhouette number $it") }
        val questions = FillChoiceBuilder.buildDataset(manyLines, Random(1)).questions

        assertEquals(48, questions.size)
        assertEquals(manyLines.indices.toList(), questions.map { it.lineIndex })
    }

    @Test
    fun `題數等於可挖空的行數`() {
        val lines = listOf(
            line(0, "Whispering silhouettes cross the sky"),
            line(1, "oh oh oh"),          // 全部 <= 2 字母 → 跳過
            line(2, "A fragile melody"),
        )
        assertEquals(2, FillChoiceBuilder.buildDataset(lines, Random(1)).questions.size)
    }

    @Test
    fun `整句都是短字時不挖空`() {
        val lines = listOf(
            line(0, "Oh oh oh"),
            line(1, "I am"),
            line(2, "na na na"),
        )
        assertEquals(0, FillChoiceBuilder.buildDataset(lines, Random(1)).questions.size)
    }

    @Test
    fun `整句都是高頻詞時不挖空`() {
        val lines = listOf(line(0, "I really love you tonight"))

        assertEquals(0, FillChoiceBuilder.buildDataset(lines, Random(1)).questions.size)
    }

    // ---------- 高頻詞過濾 ----------

    @Test
    fun `不挖高頻虛詞`() {
        assertFalse(FillChoiceBuilder.isWorthDrilling("the"))
        assertFalse(FillChoiceBuilder.isWorthDrilling("and"))
        assertFalse(FillChoiceBuilder.isWorthDrilling("you"))
        // 大小寫不影響
        assertFalse(FillChoiceBuilder.isWorthDrilling("The"))
        assertFalse(FillChoiceBuilder.isWorthDrilling("with"))
        assertFalse(FillChoiceBuilder.isWorthDrilling("are"))
    }

    @Test
    fun `高頻實詞同樣不挖（新規則）`() {
        // 這些不是虛詞，但在 Top 1000 內 → 依擁有者指定一律過濾
        assertFalse(FillChoiceBuilder.isWorthDrilling("love"))
        assertFalse(FillChoiceBuilder.isWorthDrilling("hope"))
        assertFalse(FillChoiceBuilder.isWorthDrilling("night"))
    }

    @Test
    fun `挖榜外的字`() {
        assertTrue(FillChoiceBuilder.isWorthDrilling("silhouette"))
        assertTrue(FillChoiceBuilder.isWorthDrilling("whisper"))
        assertTrue(FillChoiceBuilder.isWorthDrilling("lantern"))
        assertTrue(FillChoiceBuilder.isWorthDrilling("carousel"))
    }

    @Test
    fun `長度不足 3 的字不挖`() {
        assertFalse(FillChoiceBuilder.isWorthDrilling("am"))
        assertFalse(FillChoiceBuilder.isWorthDrilling("oh"))
        assertFalse(FillChoiceBuilder.isWorthDrilling("a"))
    }

    @Test
    fun `整句都是虛詞時不挖空`() {
        assertEquals(
            emptyList<FillChoiceBuilder.BlankCandidate>(),
            FillChoiceBuilder.findBlankCandidates("and you are the"),
        )
    }

    // ---------- 偵錯輸出 ----------

    @Test
    fun `偵錯記錄挖空的詞與等級來源`() {
        val dataset = FillChoiceBuilder.buildDataset(
            listOf(line(0, "The whispering silhouettes")),
            Random(1),
        )

        assertEquals(1, dataset.debug.size)
        val decision = dataset.debug.first()

        assertNotNull(decision.chosenWord)
        assertTrue(decision.chosenWord in listOf("whispering", "silhouettes"))
        // 沒有傳入語言資料時，所有字都是「未收錄」——正式執行時會有真的等級
        assertEquals(UNLISTED_SOURCE, decision.chosenLevelSource)
    }

    @Test
    fun `偵錯記錄被過濾的詞與等級來源`() {
        val dataset = FillChoiceBuilder.buildDataset(
            listOf(line(0, "The whispering silhouettes and you")),
            Random(1),
        )

        val rejected = dataset.debug.first().rejected
        assertEquals(listOf("The", "and", "you"), rejected.map { it.word })

        assertEquals("冠詞", rejected[0].levelSource)
        assertEquals("Top 1000 高頻詞", rejected[1].levelSource)
        assertEquals("代名詞", rejected[2].levelSource)
    }

    @Test
    fun `偵錯會標出縮寫展開`() {
        val dataset = FillChoiceBuilder.buildDataset(
            listOf(line(0, "it's a wonder")),
            Random(1),
        )

        val rejected = dataset.debug.first().rejected
        // "wonder" 在高頻榜內 → 也被過濾
        assertTrue(rejected.any { it.word == "it's" && it.levelSource == "代名詞（it's→it）" })
    }

    @Test
    fun `沒有可挖字的行也會留下偵錯紀錄`() {
        val dataset = FillChoiceBuilder.buildDataset(
            listOf(line(0, "I really love you")),
            Random(1),
        )

        assertEquals(1, dataset.debug.size)
        assertNull(dataset.debug.first().chosenWord)
        assertNull(dataset.debug.first().chosenLevelSource)
        assertTrue(dataset.debug.first().rejected.isNotEmpty())
    }

    @Test
    fun `偵錯紀錄與行數一一對應`() {
        val dataset = FillChoiceBuilder.buildDataset(sampleLines, Random(2))

        assertEquals(sampleLines.size, dataset.debug.size)
        assertEquals(sampleLines.map { it.index }, dataset.debug.map { it.lineIndex })
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
        val options = FillChoiceBuilder.buildOptions(sampleQuestion, listOf("velvet", "lantern", "echoes"), Random(2))
        val normalized = options.map { FillChoiceBuilder.normalizeAnswer(it) }
        assertEquals(1, normalized.count { it == FillChoiceBuilder.normalizeAnswer(sampleQuestion.answer) })
    }

    @Test
    fun `選項彼此相異`() {
        val options = FillChoiceBuilder.buildOptions(
            sampleQuestion,
            listOf("velvet", "VELVET", "lantern", "echoes"),
            Random(4),
        )
        val normalized = options.map { FillChoiceBuilder.normalizeAnswer(it) }
        assertEquals(normalized.size, normalized.toSet().size)
    }

    @Test
    fun `同歌答案不足時由備援字庫補滿`() {
        val options = FillChoiceBuilder.buildOptions(sampleQuestion, listOf("velvet"), Random(8))
        assertEquals(4, options.size)
        assertTrue(options.contains("velvet"))
        assertTrue(
            options.any {
                FillChoiceBuilder.normalizeAnswer(it) == FillChoiceBuilder.normalizeAnswer(sampleQuestion.answer)
            },
        )
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
