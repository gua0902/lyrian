package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.data.lexicon.LexiconSource
import com.antigravity.lyrian.domain.model.Attempt
import com.antigravity.lyrian.domain.model.FillChoiceDataset
import com.antigravity.lyrian.domain.model.FillChoiceQuestion
import com.antigravity.lyrian.domain.model.LyricBlank
import com.antigravity.lyrian.domain.model.LyricLine
import com.antigravity.lyrian.domain.model.PracticeModeId
import kotlin.random.Random

/**
 * 四選一填空（`fill-choice`）的題目組裝與評分。
 *
 * **選詞已完全搬到 [BlankPicker]**。這個物件只做三件事：
 * ```
 * 1. 把「挖空位置」套回歌詞行（一行可以有多個空）
 * 2. 產生一題的 4 個選項
 * 3. 評分
 * ```
 *
 * 沒有分數、沒有難度、沒有保底、沒有 fallback。
 */
object FillChoiceBuilder {

    /** 每題固定 4 個選項。 */
    const val OPTION_COUNT = 4

    /**
     * 干擾項備援字庫。
     *
     * 只在「同一首歌的其他答案湊不滿 3 個」時使用，避免短歌出現少於 4 個選項。
     * 這些字只會出現在選項裡，不影響挖空選詞。
     */
    private val FALLBACK_DISTRACTORS = listOf(
        "silence", "lantern", "wilderness", "gravity", "fragile", "horizon",
        "temporary", "generous", "hesitate", "ordinary", "peculiar", "reckless",
        "solitude", "stubborn", "tender", "vanish", "whisper", "yield",
    )

    /**
     * 用詞典選詞並組出題目（**唯一**會呼叫 [BlankPicker] 的入口）。
     */
    suspend fun buildDataset(
        lines: List<LyricLine>,
        lexicon: LexiconSource,
    ): FillChoiceDataset = fromBlanks(lines, BlankPicker.pick(lines, lexicon).blanks)

    /**
     * 純函式版本：把已選好的挖空套回歌詞行。
     *
     * 抽出來是為了讓測試能直接餵「要挖哪些位置」，不必經過詞典。
     */
    fun fromBlanks(
        lines: List<LyricLine>,
        blanks: List<BlankPicker.Blank>,
    ): FillChoiceDataset {
        // 全曲統一編號：先依行、再依位置
        val ordered = blanks.sortedWith(compareBy({ it.lineIndex }, { it.start }))
        val blankLines = HashMap<Int, MutableList<LyricBlank>>()

        ordered.forEachIndexed { index, blank ->
            blankLines.getOrPut(blank.lineIndex) { mutableListOf() } += LyricBlank(
                blankIndex = index,
                start = blank.start,
                end = blank.end,
                surface = blank.surface,
                answer = blank.answer,
                lemma = blank.lemma,
            )
        }

        val blankedLines = lines.map { line ->
            val lineBlanks = blankLines[line.index]?.sortedBy { it.start }.orEmpty()
            line.copy(blanks = lineBlanks)
        }

        val byLine = blankedLines.associateBy { it.index }
        val questions = ordered.mapIndexed { index, blank ->
            val line = byLine.getValue(blank.lineIndex)
            FillChoiceQuestion(
                blankIndex = index,
                lineIndex = blank.lineIndex,
                // 同一行的其他挖空也遮起來，避免洩漏答案
                prompt = line.masked(),
                answer = blank.answer,
                surface = blank.surface,
                startMs = line.startMs,
                endMs = line.endMs,
            )
        }

        return FillChoiceDataset(lines = blankedLines, questions = questions)
    }

    /** 只算「總共幾個空」（搜尋頁顯示題數用）。 */
    suspend fun countBlanks(lines: List<LyricLine>, lexicon: LexiconSource): Int =
        BlankPicker.pick(lines, lexicon).blanks.size

    /**
     * 產生一題的 4 個選項：正解 + 3 個干擾項。
     *
     * 干擾項優先取自**同一首歌的其他答案**，不足時才由備援字庫補齊。
     * 回傳前洗牌，正解位置不固定。
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
                val normalized = normalizeAnswer(word)
                if (normalized.isEmpty() || !seen.add(normalized)) continue
                distractors += normalized
            }
        }

        takeFrom(allAnswers)
        takeFrom(FALLBACK_DISTRACTORS)

        return (listOf(question.answer) + distractors).shuffled(random)
    }

    /** 把一次作答轉成 [Attempt]。 */
    fun grade(question: FillChoiceQuestion, selected: String): Attempt {
        val isCorrect = normalizeAnswer(selected) == normalizeAnswer(question.answer)

        return Attempt(
            modeId = PracticeModeId.FILL_CHOICE,
            lineIndex = question.lineIndex,
            blankIndex = question.blankIndex,
            prompt = question.prompt,
            userAnswer = selected,
            correctAnswer = question.answer,
            isCorrect = isCorrect,
            score = if (isCorrect) 1f else 0f,
        )
    }

    /** 答案比對的正規化（與選詞規則一致）。 */
    fun normalizeAnswer(value: String): String = TextNormalizer.normalize(value).trim()
}
