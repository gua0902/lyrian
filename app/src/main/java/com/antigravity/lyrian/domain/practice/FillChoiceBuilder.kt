package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.domain.model.Attempt
import com.antigravity.lyrian.domain.model.BlankDecision
import com.antigravity.lyrian.domain.model.FillChoiceDataset
import com.antigravity.lyrian.domain.model.FillChoiceQuestion
import com.antigravity.lyrian.domain.model.LyricLine
import com.antigravity.lyrian.domain.model.PracticeModeId
import com.antigravity.lyrian.domain.model.RejectedCandidate
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
     * 不值得挖空的常見詞，交由 [CommonWordFilter] 判定。
     *
     * 這份判定涵蓋：**Top 1000 高頻詞**、代名詞、be/do/have、冠詞、介系詞、助動詞。
     * 比對是**整詞比對**（不是子字串），且會先做縮寫展開（`don't` → `do`、`it's` → `it`）。
     *
     * **之前的做法**是一份幾十個字的靜態虛詞表，只擋得掉 the/and/you 這類虛詞；
     * love、hope、night 這些高頻實詞仍會被挖，使用者就得一直回答早就會的字。
     */
    fun isWorthDrilling(word: String): Boolean {
        if (word.trim().length < BLANK_MIN_WORD_LENGTH) return false
        return !CommonWordFilter.isCommon(word)
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
     * 一行的掃描結果。
     *
     * @property candidates 通過過濾、可挖空的位置
     * @property rejected 被等級表擋掉的候選（含等級來源），供偵錯輸出
     */
    data class LineScan(
        val candidates: List<BlankCandidate>,
        val rejected: List<RejectedCandidate>,
    )

    /**
     * 切成「單字」的樣式。
     *
     * **含撇號**——`don't` 必須是**一個** token，否則會被切成 `don` + `t`，
     * 就無法展開成 `do` 而讓它溜過過濾（這正是先前的 bug，見 `FillChoiceBuilderTest`）。
     *
     * 只吃英文字母，因此數字與標點天然被排除。
     */
    private val TOKEN_PATTERN = Regex("""[A-Za-z]+(?:['’][A-Za-z]+)*""")

    /**
     * 掃描一行：分出可挖空的候選與被過濾掉的字。
     *
     * 過濾順序（擁有者指定）：**命中等級表 → 挖空前先過濾**。
     */
    fun scanLine(
        text: String,
        minWordLength: Int = BLANK_MIN_WORD_LENGTH,
    ): LineScan {
        val candidates = mutableListOf<BlankCandidate>()
        val rejected = mutableListOf<RejectedCandidate>()

        TOKEN_PATTERN.findAll(text).forEach { match ->
            val token = match.value

            // 太短的字連等級都不用查（a / am / oh）
            if (token.length < minWordLength) return@forEach

            val level = CommonWordFilter.classify(token)
            if (level.isCommon) {
                rejected += RejectedCandidate(
                    word = token,
                    levelSource = if (level.matchedForm != null) {
                        "${level.sourceLabel}（${level.token}→${level.matchedForm}）"
                    } else {
                        level.sourceLabel
                    },
                )
            } else {
                candidates += BlankCandidate(
                    word = token,
                    start = match.range.first,
                    end = match.range.last + 1,
                )
            }
        }

        return LineScan(candidates = candidates, rejected = rejected)
    }

    /**
     * 找出可挖空的候選單字。
     *
     * 條件：長度 >= [minWordLength] **且**通過 [CommonWordFilter] 的等級過濾
     * （Top 1000 高頻詞、代名詞、be/do/have、冠詞、介系詞、助動詞都會被排除）。
     */
    fun findBlankCandidates(
        text: String,
        minWordLength: Int = BLANK_MIN_WORD_LENGTH,
    ): List<BlankCandidate> = scanLine(text, minWordLength).candidates

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
        val decisions = mutableListOf<BlankDecision>()

        // 把挖空結果**同時**套回每一行——這樣整首歌一開始就都是挖好的
        val blankedLines = lines.map { line ->
            val scan = scanLine(line.text, minWordLength)
            if (scan.candidates.isEmpty()) {
                // 過濾後沒有可挖的字：記錄下來，讓偵錯輸出能說明為什麼這行沒有題目
                decisions += BlankDecision(
                    lineIndex = line.index,
                    chosenWord = null,
                    chosenLevelSource = null,
                    rejected = scan.rejected,
                )
                return@map line
            }

            val picked = scan.candidates[random.nextInt(scan.candidates.size)]

            decisions += BlankDecision(
                lineIndex = line.index,
                chosenWord = picked.word,
                // 被選中的字必然「非常見詞」，這裡記錄它的等級來源以便驗證
                chosenLevelSource = CommonWordFilter.classify(picked.word).sourceLabel,
                rejected = scan.rejected,
            )

            questions += FillChoiceQuestion(
                blankIndex = questions.size,
                lineIndex = line.index,
                prefix = line.text.substring(0, picked.start),
                answer = picked.word,
                suffix = line.text.substring(picked.end),
                startMs = line.startMs,
                endMs = line.endMs,
            )

            line.copy(
                blankStart = picked.start,
                blankEnd = picked.end,
                answer = picked.word,
            )
        }

        return FillChoiceDataset(
            lines = blankedLines,
            questions = questions,
            debug = decisions,
        )
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
