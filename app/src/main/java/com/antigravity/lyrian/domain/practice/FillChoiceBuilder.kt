package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.data.lang.WordLevelSource
import com.antigravity.lyrian.domain.model.Attempt
import com.antigravity.lyrian.domain.model.BlankDecision
import com.antigravity.lyrian.domain.model.CefrLevel
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
 *
 * ## 挖空「選哪些字」已經搬到 [BlankSelector]
 *
 * 這裡只負責把選好的字變成題目、產生選項、評分。選詞規則（逐句、跨句去重、
 * 等級排序、均勻選句）全部在 [BlankSelector]——那套規則的細節多到值得獨立一個檔。
 */
object FillChoiceBuilder {

    /**
     * 可挖空單字的最小長度（3）。
     *
     * 規則來源（擁有者指定）：「每一句都挖一個空，除非該句所有單字都 ≤ 2 字母」。
     * 因此可挖的最小長度是 3。
     */
    const val BLANK_MIN_WORD_LENGTH = 3

    /** 每題固定 4 個選項（依參考圖的 2x2 選項格）。 */
    const val OPTION_COUNT = 4

    /** 題數上限的預設值（擁有者指定上限 40，這裡取 30 當預設）。 */
    const val DEFAULT_MAX_BLANKS = 30

    /**
     * 切成「單字」的樣式。
     *
     * **含撇號**——`don't` 必須是**一個** token，否則會被切成 `don` + `t`，
     * 就無法展開成 `do` 而讓它溜過過濾（這正是先前的 bug，見 `BlankSelectorTest`）。
     *
     * 只吃英文字母，因此數字與標點天然被排除。
     */
    internal val TOKEN_PATTERN = Regex("""[A-Za-z]+(?:['’][A-Za-z]+)*""")

    /**
     * 當同一首歌湊不出 3 個相異干擾項時的備援字庫。
     * 沒有這個的話，短歌會出現少於 4 個選項。
     */
    private val FALLBACK_DISTRACTORS = listOf(
        "heart", "light", "dream", "world", "music", "night", "story", "voice",
        "water", "happy", "young", "place", "sound", "peace", "smile", "truth",
        "grace", "shine", "river", "cloud", "color", "dance", "quiet", "brave",
    )

    /**
     * 把整首歌詞轉成題目集。
     *
     * 「哪幾句要挖、每句挖哪個字」由 [BlankSelector] 決定（學習價值分數、
     * 跨句 lemma 去重、分段均勻）。這裡只把結果套回歌詞行並建立題目。
     *
     * @param difficulty 挖空難度（影響分數與地板）
     * @param maxBlanks 題數上限（擁有者指定上限 40）
     * @param songTitle 歌名；歌名本身就是歌詞裡的字時不挖（`Hello`、`Yellow`）
     */
    fun buildDataset(
        lines: List<LyricLine>,
        minWordLength: Int = BLANK_MIN_WORD_LENGTH,
        difficulty: BlankDifficulty = BlankDifficulty.DEFAULT,
        maxBlanks: Int = DEFAULT_MAX_BLANKS,
        levelSource: WordLevelSource = WordLevelSource.None,
        songTitle: String = "",
    ): FillChoiceDataset {
        val selections = BlankSelector.select(
            lines = lines,
            difficulty = difficulty,
            maxBlanks = maxBlanks,
            language = levelSource,
            songTitle = songTitle,
        )

        val questions = mutableListOf<FillChoiceQuestion>()
        val decisions = mutableListOf<BlankDecision>()

        val blankedLines = lines.map { line ->
            val selection = selections.firstOrNull { it.line.index == line.index }
            val chosen = selection?.chosen

            decisions += BlankDecision(
                lineIndex = line.index,
                chosenWord = chosen?.word,
                chosenLemma = chosen?.level?.lemma,
                chosenLevel = chosen?.level?.treatAsLevel?.label,
                chosenLevelSource = chosen?.level?.source,
                frequencyRank = chosen?.level?.frequencyRank,
                basis = selection?.basis,
                rejected = selection?.rejected.orEmpty(),
                score = chosen?.score,
                partOfSpeech = chosen?.level?.partOfSpeech,
                candidateSummary = selection?.candidates
                    ?.take(6)
                    ?.joinToString(", ") { "${it.word}(${it.score})" }
                    ?.takeIf { it.isNotBlank() },
            )

            if (chosen == null) return@map line

            questions += FillChoiceQuestion(
                blankIndex = questions.size,
                lineIndex = line.index,
                prefix = line.text.substring(0, chosen.start),
                answer = chosen.word,
                suffix = line.text.substring(chosen.end),
                startMs = line.startMs,
                endMs = line.endMs,
            )

            line.copy(
                blankStart = chosen.start,
                blankEnd = chosen.end,
                answer = chosen.word,
            )
        }

        return FillChoiceDataset(
            lines = blankedLines,
            questions = questions,
            debug = decisions,
        )
    }

    /**
     * 只計算「會挖幾句」，不產生題目。
     *
     * 搜尋結果清單要顯示「N 個填空」時使用。與 [buildDataset] 用同一套規則
     * （同一份選句 + 去重），否則清單上的數字會與進練習頁後不一致。
     */
    fun countBlankableLines(
        lines: List<LyricLine>,
        difficulty: BlankDifficulty = BlankDifficulty.DEFAULT,
        maxBlanks: Int = DEFAULT_MAX_BLANKS,
        levelSource: WordLevelSource = WordLevelSource.None,
        songTitle: String = "",
    ): Int = BlankSelector.select(
        lines = lines,
        difficulty = difficulty,
        maxBlanks = maxBlanks,
        language = levelSource,
        songTitle = songTitle,
    ).count { it.chosen != null }

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
     *
     * ⚠️ 現在字庫的入庫規則是「依 CEFR 等級」，不是「依對錯」——
     * 實際的判斷在 `PracticeViewModel.persist`。這個函式保留給只想要
     * 「錯的才收」這個舊語意的呼叫端。
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
