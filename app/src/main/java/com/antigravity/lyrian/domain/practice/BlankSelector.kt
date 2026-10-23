package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.data.lang.WordLevel
import com.antigravity.lyrian.data.lang.WordLevelSource
import com.antigravity.lyrian.domain.model.CefrLevel
import com.antigravity.lyrian.domain.model.LyricLine
import com.antigravity.lyrian.domain.model.RejectedCandidate

/** 一首歌要挖幾句。擁有者指定設定頁提供這三個選項。 */
enum class BlankCoverage(val label: String, val sentences: Int?) {
    FIVE("5 句", 5),
    TEN("10 句", 10),
    ALL("全部", null),
    ;

    companion object {
        val DEFAULT = ALL

        fun fromName(name: String?): BlankCoverage =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * 逐句選詞的挖空策略（模組 C，**與舊版完全不同**）。
 *
 * ## 舊版 vs 新版
 *
 * | | 舊版 | 新版 |
 * |---|---|---|
 * | 篩選 | 全域：低於等級門檻的字**一律不挖** | 逐句：每句挑一個最好的字，幾乎每句都挖 |
 * | 等級 | **硬性門檻** | **排序起點**（低於門檻的字只在該句沒別的字時才用）|
 * | 重複 | 同一個字可以在不同句被挖 | **跨句依 lemma 去重** |
 * | 選句 | 全部句子 | 均勻抽樣（5 / 10 / 全部）|
 *
 * 舊版對歌詞簡單的歌（Hello、Yellow）會挖不到 3 個字，使用者根本沒得練；
 * 新版的目標是「每句都有得練，而且越難的字越優先」。
 *
 * ## 排序規則（擁有者指定）
 *
 * ```
 * 1. 先分兩層：等級 >= 設定值（含未收錄）　→　等級 < 設定值
 * 2. 同層內：CEFR 等級高者優先（B2 → B1 → A2 → A1）
 * 3. 同級：詞頻更低（更罕見）者優先
 * 4. 同頻：長度更長者優先
 * ```
 *
 * A1 之所以會被排到最後，是因為它在第 1 條就落到第二層；只有在該句
 * 完全沒有達標的字時才會被選中。
 */
object BlankSelector {

    /** 詞頻表的說明文字（顯示在偵錯輸出與過濾清單）。 */
    const val FREQUENCY_SOURCE = "BNC 前 2000 高頻詞"

    /** 一個候選字。 */
    data class Candidate(
        val word: String,
        val start: Int,
        val end: Int,
        val level: WordLevel,
        /** 排序用的詞頻排名；沒有排名時當成「極罕見」。 */
        val rank: Int,
    )

    /** 一句的結果。 */
    data class Selection(
        val line: LyricLine,
        /** 選中的字；null 表示這句不挖（沒有合格候選，或詞都被前面的句子用掉了） */
        val chosen: Candidate?,
        /** 這句被排除的字與原因（偵錯用） */
        val rejected: List<RejectedCandidate>,
        /** 排序依據說明（偵錯用），例如 `等級優先（B2 > B1）` */
        val basis: String? = null,
        /** 這句是「無實詞」而被明確略過（Oh / Yeah / La la） */
        val skippedAsFiller: Boolean = false,
    )

    /** 每句最多一個空。 */
    const val MAX_BLANKS_PER_LINE = 1

    /**
     * 選詞。
     *
     * @param lines 全部歌詞行（順序即歌曲順序）
     * @param minLevel 設定頁的「挖空等級」，作為排序起點
     * @param coverage 要挖幾句
     * @param language 等級／詞頻資料
     */
    /** 一句的掃描結果（保留原行，後面要用索引對回來）。 */
    private data class Scan(
        val line: LyricLine,
        val candidates: List<Candidate>,
        val rejected: List<RejectedCandidate>,
    )

    fun select(
        lines: List<LyricLine>,
        minLevel: CefrLevel,
        coverage: BlankCoverage,
        language: WordLevelSource,
    ): List<Selection> {
        val scanned = lines.map { line -> scanLine(line, minLevel, language) }
        val eligible = scanned.filter { it.candidates.isNotEmpty() }

        // 先決定要挖哪幾句（均勻分布），再逐句套用跨句去重。
        // 順序很重要：若先去重再選句，去重會讓後段的歌詞大量落空，
        // 整首歌的空就會全部擠在副歌之前。
        val chosenIndices = pickEvenly(eligible.map { it.line.index }, coverage)

        val usedLemmas = HashSet<String>()
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
                    skippedAsFiller = true,
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
                basis = describeBasis(picked, available.drop(1)),
            )
        }

        return selections
    }

    /**
     * 掃描一句，回傳**已排序**的候選與被排除的字。
     */
    private fun scanLine(
        line: LyricLine,
        minLevel: CefrLevel,
        language: WordLevelSource,
    ): Scan {
        val candidates = mutableListOf<Candidate>()
        val rejected = mutableListOf<RejectedCandidate>()

        FillChoiceBuilder.TOKEN_PATTERN.findAll(line.text).forEach { match ->
            val token = match.value
            if (token.length < FillChoiceBuilder.BLANK_MIN_WORD_LENGTH) return@forEach

            val common = CommonWordFilter.classify(token)
            if (common.isCommon) {
                rejected += RejectedCandidate(token, common.rejectionLabel())
                return@forEach
            }

            val expanded = CommonWordFilter.expandContraction(token)
            if (expanded.any { language.isHighFrequency(it) }) {
                rejected += RejectedCandidate(token, FREQUENCY_SOURCE)
                return@forEach
            }

            val level = language.lookup(token)
            candidates += Candidate(
                word = token,
                start = match.range.first,
                end = match.range.last + 1,
                level = level,
                rank = level.frequencyRank ?: UNRANKED,
            )
        }

        candidates.sortWith(comparator(minLevel))
        return Scan(line = line, candidates = candidates, rejected = rejected)
    }

    /** 沒有詞頻排名的字當成「比任何有排名的字更罕見」。 */
    private const val UNRANKED = Int.MAX_VALUE

    /**
     * 排序：先分「達標 / 未達標」兩層，再比等級、詞頻、長度。
     *
     * 用 `sortedWith` 而不是 `sortedBy` 是因為要比三個欄位、其中兩個要反序。
     */
    private fun comparator(minLevel: CefrLevel) = Comparator<Candidate> { a, b ->
        val tierA = if (a.level.treatAsLevel >= minLevel) 0 else 1
        val tierB = if (b.level.treatAsLevel >= minLevel) 0 else 1
        if (tierA != tierB) return@Comparator tierA - tierB

        // 等級高者優先
        val byLevel = b.level.treatAsLevel.ordinal - a.level.treatAsLevel.ordinal
        if (byLevel != 0) return@Comparator byLevel

        // 同級：詞頻更低（排名數字更大＝更罕見）者優先
        val byRank = b.rank.compareTo(a.rank)
        if (byRank != 0) return@Comparator byRank

        // 同頻：長度更長者優先
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
     */
    fun pickEvenly(indices: List<Int>, coverage: BlankCoverage): Set<Int> {
        val target = coverage.sentences ?: return indices.toSet()
        if (target <= 0 || indices.isEmpty()) return emptySet()
        if (target >= indices.size) return indices.toSet()
        if (target == 1) return setOf(indices[indices.size / 2])

        val picked = LinkedHashSet<Int>(target)
        for (i in 0 until target) {
            val position = Math.round(i * (indices.size - 1).toFloat() / (target - 1))
            picked += indices[position]
        }
        return picked
    }

    /** 偵錯用的排序依據說明。 */
    private fun describeBasis(picked: Candidate, runnersUp: List<Candidate>): String {
        val next = runnersUp.firstOrNull()
        if (next == null) return "該句唯一合格候選（${picked.level.treatAsLevel.label}）"

        val pickedLevel = picked.level.treatAsLevel
        val nextLevel = next.level.treatAsLevel
        if (pickedLevel != nextLevel) {
            return "等級優先（${pickedLevel.label} > ${nextLevel.label}）"
        }
        if (picked.rank != next.rank) {
            return "同級取罕見（${picked.level.frequencyLabel} 而非 ${next.level.frequencyLabel}）"
        }
        if (picked.word.length != next.word.length) {
            return "同頻取較長（${picked.word.length} 字 > ${next.word.length} 字）"
        }
        return "同級同頻，取句中較前面者"
    }
}
