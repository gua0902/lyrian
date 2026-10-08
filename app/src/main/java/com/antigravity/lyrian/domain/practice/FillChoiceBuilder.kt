package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.domain.model.Attempt
import com.antigravity.lyrian.domain.model.FillChoiceDataset
import com.antigravity.lyrian.domain.model.FillChoiceQuestion
import com.antigravity.lyrian.domain.model.LyricLine
import com.antigravity.lyrian.domain.model.PracticeModeId
import com.antigravity.lyrian.domain.model.VocabCandidate
import kotlin.random.Random

/**
 * 四選一填空（`fill-choice`）的純邏輯。
 *
 * 完全離線、零 AI 依賴——這是 App 的保底體驗。
 *
 * 本物件**不依賴 Android 框架**，可完整單元測試。所有隨機行為都接受可注入的 [Random]，
 * 讓測試能重現固定的結果。
 */
object FillChoiceBuilder {

    /**
     * 可挖空單字的最小長度（4）。
     *
     * **以 `UI references/Lyrian · 歌詞練習.png` 為準**——該參考圖的答案是 Hope 與 Love，
     * 皆為 4 字母。這與 `docs/REQUIREMENTS.md` Spec 2 的「長度 > 4」不同，
     * 已於 2026-10-06 由擁有者裁決：**以參考圖為準**，
     * 因為 love / hope / hold / heart 這類學習者最需要的常用字才挖得到。
     */
    const val BLANK_MIN_WORD_LENGTH = 4

    /** 每題固定 4 個選項（依參考圖的 2x2 選項格）。 */
    const val OPTION_COUNT = 4

    /**
     * 每首歌最多挖幾個空。
     *
     * 依 UI references——兩首歌都顯示「10 個填空」／「共 10 個」。
     * 實測 Adele《Hello》在門檻 4 之下有 48 行可挖，若不設上限，
     * 一輪練習會變成 48 題（不符參考圖，也不是好的練習長度）。
     */
    const val DEFAULT_MAX_BLANKS = 10

    /**
     * 當同一首歌湊不出 3 個相異干擾項時的備援字庫。
     * 沒有這個的話，短歌會出現少於 4 個選項。
     */
    private val FALLBACK_DISTRACTORS = listOf(
        "heart", "light", "dream", "world", "music", "night", "story", "voice",
        "water", "happy", "young", "place", "sound", "peace", "smile", "truth",
        "grace", "shine", "river", "cloud", "color", "dance", "quiet", "brave",
    )

    /** 可挖空的候選單字，附帶在原文中的位置。 */
    data class BlankCandidate(val word: String, val start: Int, val end: Int)

    /**
     * 找出可挖空的候選單字。
     *
     * @param minWordLength 最短可挖空的字長。預設 4（依參考圖），可調高以限制候選。
     */
    fun findBlankCandidates(
        text: String,
        minWordLength: Int = BLANK_MIN_WORD_LENGTH,
    ): List<BlankCandidate> {
        val pattern = Regex("""\b[A-Za-z]{$minWordLength,}\b""")
        return pattern.findAll(text).map { match ->
            BlankCandidate(
                word = match.value,
                start = match.range.first,
                end = match.range.last + 1,
            )
        }.toList()
    }

    /**
     * 從清單中**均勻取樣** [count] 個項目，讓挖空涵蓋整首歌而不是只集中在前半段。
     *
     * 這是上限的選擇策略；接上 ECDICT 後會改由詞頻決定要挑哪些字。
     */
    fun <T> pickEvenlySpaced(items: List<T>, count: Int): List<T> {
        if (count <= 0 || items.isEmpty()) return emptyList()
        if (count >= items.size) return items.toList()
        if (count == 1) return listOf(items.first())

        return (0 until count).map { i ->
            // 整數運算避免浮點誤差造成索引越界
            items[(i.toLong() * (items.size - 1) / (count - 1)).toInt()]
        }
    }

    /**
     * 把整首歌詞轉成題目集。
     *
     * 每一行**最多挖一個空**，但整首最多 [maxBlanks] 題（預設 10，依參考圖）。
     * 沒有合格單字的行不會產生題目，但仍保留在 `lines` 中供歌詞流顯示。
     */
    fun buildDataset(
        lines: List<LyricLine>,
        random: Random = Random.Default,
        minWordLength: Int = BLANK_MIN_WORD_LENGTH,
        maxBlanks: Int = DEFAULT_MAX_BLANKS,
    ): FillChoiceDataset {
        // 先收集所有可挖空的行
        val blankable = lines.mapNotNull { line ->
            val candidates = findBlankCandidates(line.text, minWordLength)
            if (candidates.isEmpty()) null else line to candidates
        }

        val questions = pickEvenlySpaced(blankable, maxBlanks)
            .mapIndexed { blankIndex, (line, candidates) ->
                val picked = candidates[random.nextInt(candidates.size)]
                FillChoiceQuestion(
                    blankIndex = blankIndex,
                    lineIndex = line.index,
                    prefix = line.text.substring(0, picked.start),
                    answer = picked.word,
                    suffix = line.text.substring(picked.end),
                    startMs = line.startMs,
                    endMs = line.endMs,
                )
            }

        return FillChoiceDataset(lines = lines, questions = questions)
    }

    /**
     * 只計算「可挖空的行數」，不產生題目。
     *
     * 搜尋結果清單要顯示「N 個填空」時使用，避免為 20 筆結果各建立一份完整題目集。
     * 上限與 [buildDataset] 一致。
     */
    fun countBlankableLines(
        lines: List<LyricLine>,
        minWordLength: Int = BLANK_MIN_WORD_LENGTH,
        maxBlanks: Int = DEFAULT_MAX_BLANKS,
    ): Int {
        var count = 0
        for (line in lines) {
            if (count >= maxBlanks) break
            if (findBlankCandidates(line.text, minWordLength).isNotEmpty()) count++
        }
        return count
    }

    /**
     * 產生一題的 4 個選項：正解 + 3 個干擾項。
     *
     * 干擾項優先取自**同一首歌的其他答案**（與參考圖一致），不足時才由備援字庫補齊，
     * 確保永遠是 4 個選項。回傳前會洗牌，正解位置不固定。
     */
    fun buildOptions(
        question: FillChoiceQuestion,
        allAnswers: List<String>,
        random: Random = Random.Default,
    ): List<String> {
        val needed = OPTION_COUNT - 1
        val seen = mutableSetOf(normalizeAnswer(question.answer))
        val distractors = mutableListOf<String>()

        fun takeFrom(pool: List<String>) {
            for (word in pool.shuffled(random)) {
                if (distractors.size >= needed) return
                val key = normalizeAnswer(word)
                if (key.isEmpty() || !seen.add(key)) continue
                distractors += word
            }
        }

        takeFrom(allAnswers)
        takeFrom(FALLBACK_DISTRACTORS)

        return (listOf(question.answer) + distractors).shuffled(random)
    }

    /** 把一次作答轉成 [Attempt]（所有模式的唯一收斂點）。 */
    fun grade(question: FillChoiceQuestion, selected: String): Attempt {
        val isCorrect = normalizeAnswer(selected) == normalizeAnswer(question.answer)

        return Attempt(
            modeId = PracticeModeId.FILL_CHOICE,
            lineIndex = question.lineIndex,
            blankIndex = question.blankIndex,
            prompt = "${question.prefix}____${question.suffix}",
            userAnswer = selected,
            correctAnswer = question.answer,
            isCorrect = isCorrect,
            score = if (isCorrect) 1f else 0f,
        )
    }

    /**
     * 從一次作答決定是否要收進字庫。**只有答錯才收**；答對回傳 null。
     */
    fun vocabCandidateFrom(
        question: FillChoiceQuestion,
        attempt: Attempt,
        songId: Long? = null,
    ): VocabCandidate? {
        if (attempt.isCorrect) return null

        return VocabCandidate(
            lemma = normalizeAnswer(question.answer),
            surfaceForm = question.answer,
            contextLine = question.originalLine,
            songId = songId,
        )
    }

    /** 比較答案時統一大小寫與前後空白。 */
    fun normalizeAnswer(value: String): String = value.trim().lowercase()
}
