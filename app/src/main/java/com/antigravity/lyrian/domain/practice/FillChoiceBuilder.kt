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
     * 可挖空單字的最小長度（3）。
     *
     * 規則來源（擁有者指定）：「每一句都挖一個空，除非該句所有單字都 ≤ 2 字母」。
     * 因此可挖的最小長度是 3。
     *
     * 注意：3 字母的**虛詞**（the / and / you）由 [isWorthDrilling] 排除，
     * 只挖有實意的字——這更符合「學單字」的目的。
     */
    const val BLANK_MIN_WORD_LENGTH = 3

    /** 每題固定 4 個選項（依參考圖的 2x2 選項格）。 */
    const val OPTION_COUNT = 4

    /**
     * 不值得挖空的常見虛詞。
     *
     * 這些字出現頻率極高、幾乎沒有學習價值（學習者早就會了），
     * 挖它們只會讓題目變多卻沒有學習效果。
     *
     * **這是一份暫時的靜態清單**。擁有者已指示之後會改用 **CEFR 分級**篩選
     * （依 Europe 語言共同參考框架，只挖 A2/B1 以上的字）。
     * 屆時只要替換 [isWorthDrilling] 的實作即可，呼叫端不需改動。
     */
    private val STOP_WORDS: Set<String> = setOf(
        // 冠詞、代名詞
        "the", "and", "you", "your", "yours", "she", "her", "hers", "him", "his",
        "they", "them", "their", "theirs", "our", "ours", "its", "who", "whom",
        "this", "that", "these", "those", "all", "any", "both", "each", "few",
        "more", "most", "other", "some", "such", "own", "same", "than", "too",
        "very", "just", "only", "also", "even", "still", "yet", "ever", "never",
        // be 動詞與助動詞
        "are", "was", "were", "been", "being", "has", "had", "have", "does",
        "did", "doing", "will", "would", "can", "could", "shall", "should",
        "may", "might", "must", "ought",
        // 介系詞、連接詞
        "for", "not", "but", "out", "into", "onto", "over", "under", "about",
        "with", "from", "off", "per", "via", "nor", "because", "while", "when",
        "where", "what", "which", "how", "why", "there", "here", "then", "now",
        "once", "upon", "among", "between", "through", "during", "before",
        "after", "above", "below", "again", "further", "any", "each",
    )

    /**
     * 這個字值不值得挖空。
     *
     * 目前的策略：長度 >= [BLANK_MIN_WORD_LENGTH] 且不是常見虛詞。
     *
     * **之後會改成依 CEFR 分級篩選**——這是唯一需要改動的地方。
     * 屆時把這裡換成查 ECDICT 的詞頻／CEFR 標籤即可。
     */
    fun isWorthDrilling(word: String): Boolean {
        if (word.length < BLANK_MIN_WORD_LENGTH) return false
        return word.lowercase() !in STOP_WORDS
    }

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
     * 條件：長度 >= [minWordLength] **且**值得挖（見 [isWorthDrilling]，
     * 會排除 the / and / you 這類虛詞）。
     */
    fun findBlankCandidates(
        text: String,
        minWordLength: Int = BLANK_MIN_WORD_LENGTH,
    ): List<BlankCandidate> {
        val pattern = Regex("""\b[A-Za-z]{$minWordLength,}\b""")
        return pattern.findAll(text)
            .filter { isWorthDrilling(it.value) }
            .map { match ->
                BlankCandidate(
                    word = match.value,
                    start = match.range.first,
                    end = match.range.last + 1,
                )
            }
            .toList()
    }

    /**
     * 把整首歌詞轉成題目集。
     *
     * **規則（擁有者指定）**：每一句都挖一個空，除非該句**所有**單字都 ≤ 2 字母
     * （例如 "Oh oh oh"、"I am"）。**不設題數上限**。
     *
     * 之前的做法是「每首上限 10 題並均勻取樣」，導致歌詞**跳著挖**、進度太快。
     * 已移除——現在題數等於可挖空的行數。
     *
     * 沒有合格單字的行不會產生題目，但仍保留在 `lines` 中供歌詞流顯示。
     */
    fun buildDataset(
        lines: List<LyricLine>,
        random: Random = Random.Default,
        minWordLength: Int = BLANK_MIN_WORD_LENGTH,
    ): FillChoiceDataset {
        val questions = mutableListOf<FillChoiceQuestion>()

        for (line in lines) {
            val candidates = findBlankCandidates(line.text, minWordLength)
            if (candidates.isEmpty()) continue

            val picked = candidates[random.nextInt(candidates.size)]
            questions += FillChoiceQuestion(
                blankIndex = questions.size,
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
     * 搜尋結果清單要顯示「N 個填空」時使用。與 [buildDataset] 的規則一致
     * （不設上限）。
     */
    fun countBlankableLines(
        lines: List<LyricLine>,
        minWordLength: Int = BLANK_MIN_WORD_LENGTH,
    ): Int = lines.count { findBlankCandidates(it.text, minWordLength).isNotEmpty() }

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
