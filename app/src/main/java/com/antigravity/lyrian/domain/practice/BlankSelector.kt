package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.data.lang.WordLevel
import com.antigravity.lyrian.data.lang.WordLevelSource
import com.antigravity.lyrian.domain.model.CefrLevel
import com.antigravity.lyrian.domain.model.LyricLine
import com.antigravity.lyrian.domain.model.RejectedCandidate

/**
 * 挖空難度（模組 C-4）。
 *
 * **只影響選詞優先級，不影響題數。** 題數由設定頁的滑桿決定（上限 40）。
 */
enum class BlankDifficulty(
    val label: String,
    val description: String,
) {
    /** 先挖 A2 及以下（入門者）。 */
    BASIC("基礎優先", "先挖 A2 及以下"),

    /** 先挖 B1 及以上（預設）。 */
    ADVANCED("進階優先", "先挖 B1 及以上"),

    /** 先挖 B2 及以上。 */
    CHALLENGE("挑戰優先", "先挖 B2 及以上"),
    ;

    /** 這個等級是否落在「優先」那一側。 */
    fun isPreferred(level: CefrLevel): Boolean = when (this) {
        BASIC -> level <= CefrLevel.A2
        ADVANCED -> level >= CefrLevel.B1
        CHALLENGE -> level >= CefrLevel.B2
    }

    /** 這個難度「瞄準」的等級，用來算延伸詞彙的門檻。 */
    val minLevel: CefrLevel
        get() = when (this) {
            BASIC -> CefrLevel.A2
            ADVANCED -> CefrLevel.B1
            CHALLENGE -> CefrLevel.B2
        }

    companion object {
        val DEFAULT = ADVANCED

        fun fromName(name: String?): BlankDifficulty =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * 逐句選詞的挖空策略（模組 C）。
 *
 * ## 規則（擁有者指定，2026-10 重寫）
 *
 * ```
 * 1. 以句為單位，每句最多一個空
 * 2. 候選池 = 該句所有實詞
 * 3. 硬性排除：功能詞（冠詞/介系詞/代名詞/連接詞/助動詞/數詞）、
 *              專有名詞、詞頻前 500 名
 * 4. 排序：難度優先 → CEFR 降序 → 詞頻升序（罕見優先）→ 長度降序
 * 5. 取排序第一個
 * 6. 只有「硬性排除後完全沒有候選」才不挖——**不得因為字都很常見就整句跳過**
 * 7. 跨句 lemma 不重複（同一原形全曲只挖一次）
 * 8. 純感嘆詞（Oh / Yeah / La la）不挖
 * ```
 *
 * ## 與上一版的差別
 *
 * 上一版把「高頻詞」當成硬性排除，於是 Hello 有 42/48 句整句被跳過（那些句子的字
 * 全在前 2000 名內）。現在高頻只影響**排序**（罕見的優先），
 * 只有前 500 名才是硬性排除——這是擁有者明確的修正。
 */
internal fun isFunctionWord(category: CommonWordFilter.Category?): Boolean =
    category?.isFunctionWord() == true

object BlankSelector {

    /** 硬性排除的詞頻界線：前 500 名。 */
    const val HARD_FREQUENCY_LIMIT = 500

    /** 詞頻表的說明文字（顯示在偵錯輸出與過濾清單）。 */
    const val HARD_FREQUENCY_SOURCE = "詞頻前 500"

    /** 每句最多一個空。 */
    const val MAX_BLANKS_PER_LINE = 1

    /** 題數上限（擁有者指定 40）。 */
    const val MAX_BLANKS = 40

    /** 一個候選字。 */
    data class Candidate(
        val word: String,
        val start: Int,
        val end: Int,
        val level: WordLevel,
    )

    /** 一句的結果。 */
    data class Selection(
        val line: LyricLine,
        val chosen: Candidate?,
        val rejected: List<RejectedCandidate>,
        val basis: String? = null,
        /** 這句是完全沒有實詞的感嘆句（Oh / Yeah / La la） */
        val skippedAsFiller: Boolean = false,
    )

    /**
     * 歌詞裡的大小寫線索，用來判斷專有名詞（C-2 規則 1）。
     *
     * 規則：**每次出現都首字母大寫，而且從來不出現在句首**。
     * 只看單一 token 看不出來，所以要先把整首歌掃一遍。
     */
    private class CasingProfile(lines: List<LyricLine>) {
        private val total = HashMap<String, Int>()
        private val capitalized = HashMap<String, Int>()
        private val atLineStart = HashMap<String, Int>()

        init {
            lines.forEach { line ->
                FillChoiceBuilder.TOKEN_PATTERN.findAll(line.text).forEachIndexed { order, match ->
                    val key = match.value.lowercase().replace('’', '\'')
                    total[key] = (total[key] ?: 0) + 1
                    if (match.value.first().isUpperCase()) {
                        capitalized[key] = (capitalized[key] ?: 0) + 1
                    }
                    if (order == 0) {
                        atLineStart[key] = (atLineStart[key] ?: 0) + 1
                    }
                }
            }
        }

        /** 永遠大寫、而且從不出現在句首。 */
        fun looksProper(word: String): Boolean {
            val key = word.lowercase().replace('’', '\'')
            val all = total[key] ?: return false
            val caps = capitalized[key] ?: 0
            val starts = atLineStart[key] ?: 0
            return caps == all && starts == 0
        }
    }

    /**
     * 選詞。
     *
     * @param difficulty 挖空難度（只影響優先級）
     * @param maxBlanks 題數上限（均勻分布；擁有者指定上限 40）
     */
    fun select(
        lines: List<LyricLine>,
        difficulty: BlankDifficulty,
        maxBlanks: Int,
        language: WordLevelSource,
    ): List<Selection> {
        val casing = CasingProfile(lines)
        val scanned = lines.map { line -> scanLine(line, difficulty, language, casing) }
        val eligible = scanned.filter { it.candidates.isNotEmpty() }

        // 先決定要挖哪幾句（均勻分布），再逐句套用跨句去重。
        // 順序很重要：若先去重再選句，去重會讓後段的歌詞大量落空，
        // 整首歌的空就會全部擠在副歌之前。
        val chosenIndices = pickEvenly(eligible.map { it.line.index }, maxBlanks)

        val usedLemmas = HashSet<String>()
        // member extension 放在這裡，lambda 裡才看得到
        fun isFunctionWord(category: CommonWordFilter.Category?) =
            category?.isFunctionWord() == true
        val byIndex = scanned.associateBy { it.line.index }
        val selections = mutableListOf<Selection>()

        for (line in lines) {
            val entry = byIndex[line.index] ?: continue
            val candidates = entry.candidates
            val rejected = entry.rejected

            if (candidates.isEmpty()) {
                selections += Selection(
                    line = line,
                    chosen = null,
                    rejected = rejected,
                    skippedAsFiller = rejected.isEmpty(),
                )
                continue
            }

            if (line.index !in chosenIndices) {
                selections += Selection(line = line, chosen = null, rejected = rejected)
                continue
            }

            val available = candidates.filter { it.level.lemma !in usedLemmas }
            val picked = available.firstOrNull()

            if (picked == null) {
                selections += Selection(
                    line = line,
                    chosen = null,
                    rejected = rejected,
                    basis = "此句的候選都已被前面的句子挖過（lemma 不重複）",
                )
                continue
            }

            usedLemmas += picked.level.lemma
            selections += Selection(
                line = line,
                chosen = picked,
                rejected = rejected,
                basis = describeBasis(picked, available.drop(1), difficulty),
            )
        }

        return selections
    }

    /**
     * 掃描一句，回傳**已排序**的候選與被排除的字。
     */
    private fun scanLine(
        line: LyricLine,
        difficulty: BlankDifficulty,
        language: WordLevelSource,
        casing: CasingProfile,
    ): Scan {
        val candidates = mutableListOf<Candidate>()
        val rejected = mutableListOf<RejectedCandidate>()

        FillChoiceBuilder.TOKEN_PATTERN.findAll(line.text).forEach { match ->
            val token = match.value
            if (token.length < FillChoiceBuilder.BLANK_MIN_WORD_LENGTH) return@forEach

            // 1. 功能詞（冠詞／介系詞／代名詞／連接詞／助動詞／數詞）
            val common = CommonWordFilter.classify(token)
            if (isFunctionWord(common.category)) {
                rejected += RejectedCandidate(token, common.rejectionLabel())
                return@forEach
            }

            // 2. 專有名詞
            val levelInfo = language.lookup(token)
            if (casing.looksProper(token) || levelInfo.isProperNoun) {
                rejected += RejectedCandidate(token, PROPER_NOUN_SOURCE)
                return@forEach
            }

            // 3. 詞頻前 500（只擋詞形本身；`years` 的複數形沒有排名，所以可以挖）
            val rank = levelInfo.surfaceRank
            if (rank != null && rank <= HARD_FREQUENCY_LIMIT) {
                rejected += RejectedCandidate(token, HARD_FREQUENCY_SOURCE)
                return@forEach
            }

            candidates += Candidate(
                word = token,
                start = match.range.first,
                end = match.range.last + 1,
                level = levelInfo,
            )
        }

        candidates.sortWith(comparator(difficulty))
        return Scan(line = line, candidates = candidates, rejected = rejected)
    }

    /** 一句的掃描結果（保留原行，後面要用索引對回來）。 */
    private data class Scan(
        val line: LyricLine,
        val candidates: List<Candidate>,
        val rejected: List<RejectedCandidate>,
    )

    private const val PROPER_NOUN_SOURCE = "專有名詞"

    /** 沒有詞頻排名的字當成「比任何有排名的字更罕見」。 */
    private const val UNRANKED = Int.MAX_VALUE

    /**
     * 排序：難度偏好 → 等級高者 → 罕見者 → 較長者。
     *
     * 「難度偏好」只是一個**層**：偏好的那一側全部排在前面，
     * 但另一側仍然有機會（該句沒有偏好側的字時）。這就是「降權而非排除」。
     */
    private fun comparator(difficulty: BlankDifficulty) = Comparator<Candidate> { a, b ->
        val tierA = if (difficulty.isPreferred(a.level.treatAsLevel)) 0 else 1
        val tierB = if (difficulty.isPreferred(b.level.treatAsLevel)) 0 else 1
        if (tierA != tierB) return@Comparator tierA - tierB

        // ---- UNKNOWN 最優先（擁有者指定）----
        //
        // 四層都查不到等級的字（`piercing` 這種生僻實詞）**不可以被忽略**：
        // 它通常正是最該練的字。所以在同一個難度層裡，未知字排在所有已知字前面。
        if (a.level.isUnlisted != b.level.isUnlisted) {
            return@Comparator if (a.level.isUnlisted) -1 else 1
        }

        val byLevel = b.level.treatAsLevel.ordinal - a.level.treatAsLevel.ordinal
        if (byLevel != 0) return@Comparator byLevel

        // 詞頻升序（頻率低＝罕見的優先）：排名數字大代表罕見
        val rankA = a.level.frequencyRank ?: UNRANKED
        val rankB = b.level.frequencyRank ?: UNRANKED
        val byRank = rankB.compareTo(rankA)
        if (byRank != 0) return@Comparator byRank

        val byLength = b.word.length - a.word.length
        if (byLength != 0) return@Comparator byLength

        a.start - b.start
    }

    /**
     * 依「均勻分布」挑句子。
     *
     * 擁有者要求「不得集中在副歌」——歌詞裡副歌往往是連續好幾句，
     * 若直接取前 N 句就會全部落在同一段。因此用**等距抽樣**：
     * 把候選句子當成一條線，均勻取樣點。
     *
     * @param maxBlanks 題數上限；實際題數只會更少（eligible 不足時）
     */
    fun pickEvenly(indices: List<Int>, maxBlanks: Int): Set<Int> {
        if (maxBlanks <= 0 || indices.isEmpty()) return emptySet()
        if (maxBlanks >= indices.size) return indices.toSet()
        if (maxBlanks == 1) return setOf(indices[indices.size / 2])

        val picked = LinkedHashSet<Int>(maxBlanks)
        for (i in 0 until maxBlanks) {
            val position = Math.round(i * (indices.size - 1).toFloat() / (maxBlanks - 1))
            picked += indices[position]
        }
        return picked
    }

    /** 偵錯用的排序依據說明。 */
    private fun describeBasis(
        picked: Candidate,
        runnersUp: List<Candidate>,
        difficulty: BlankDifficulty,
    ): String {
        val next = runnersUp.firstOrNull()
        val prefixed = if (difficulty.isPreferred(picked.level.treatAsLevel)) {
            "${difficulty.label}命中"
        } else {
            "該句無${difficulty.label}的字"
        }
        if (next == null) {
            return "$prefixed；該句唯一合格候選（${picked.level.treatAsLevel.label}）"
        }

        val pickedLevel = picked.level.treatAsLevel
        val nextLevel = next.level.treatAsLevel
        if (pickedLevel != nextLevel) {
            return "$prefixed；等級優先（${pickedLevel.label} > ${nextLevel.label}）"
        }
        if (picked.level.frequencyRank != next.level.frequencyRank) {
            return "$prefixed；同級取罕見（${picked.level.frequencyLabel} 而非 ${next.level.frequencyLabel}）"
        }
        if (picked.word.length != next.word.length) {
            return "$prefixed；同頻取較長（${picked.word.length} 字 > ${next.word.length} 字）"
        }
        return "$prefixed；同級同頻，取句中較前面者"
    }
}
