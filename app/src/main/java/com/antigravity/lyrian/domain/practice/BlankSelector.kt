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

    /**
     * 至少出幾題。
     *
     * 新選詞會用地板分數過濾「不值得學的字」；若過濾到太少，逐段降低地板，
     * 但**至少要湊到這個數字**（真的有候選的話），否則使用者會拿到空練習。
     */
    const val MIN_BLANKS = 5

    /** 一個候選字。 */
    data class Candidate(
        val word: String,
        val start: Int,
        val end: Int,
        val level: WordLevel,
        /** 學習價值分數（[WordValue]）；選句與排序都看它。 */
        val score: Int = 0,
    )

    /** 一句的結果。 */
    data class Selection(
        val line: LyricLine,
        val chosen: Candidate?,
        val rejected: List<RejectedCandidate>,
        val basis: String? = null,
        /** 這句是完全沒有實詞的感嘆句（Oh / Yeah / La la） */
        val skippedAsFiller: Boolean = false,
        /** 這一句所有合格候選（已依分數排序）；報表要列出「候選有哪些、各幾分」。 */
        val candidates: List<Candidate> = emptyList(),
    )


    /**
     * 選詞。
     *
     * ## 與舊版的差別（擁有者要求重新設計）
     *
     * 舊版「每一句各挑一個最好的」，跨句之間完全沒有比較——於是
     * `sorry`（A1）與 `desperate`（B1）等價，題數一多就整份都是簡單字。
     *
     * 新版：
     * ```
     * 1. 每個候選算學習價值分數（WordValue，0–100）
     * 2. 每句只留分數最高的那一個
     * 3. 分數地板（依難度）先過濾「不值得學的字」
     * 4. 把剩下的句子切成 K 段，每段取分數最高的那句
     *    → 題數不變、分散不變，但每一題都是該區段裡最值得學的字
     * 5. 地板太高導致題數 < MIN_BLANKS 時逐段降地板
     * ```
     *
     * @param difficulty 挖空難度（影響等級契合度與地板）
     * @param maxBlanks 題數上限（均勻分布；擁有者指定上限 40）
     * @param songTitle 歌名；歌名本身就是歌詞裡的字（`Hello`、`Yellow`）→ 不挖
     */
    fun select(
        lines: List<LyricLine>,
        difficulty: BlankDifficulty,
        maxBlanks: Int,
        language: WordLevelSource,
        songTitle: String = "",
    ): List<Selection> {
        val casing = LyricCasing(lines)
        val titleWords = titleWordsOf(songTitle)
        val scanned = lines.map { line ->
            scanLine(line, difficulty, language, casing, titleWords)
        }
        val eligible = scanned.filter { it.candidates.isNotEmpty() }

        // 先決定要挖哪幾句（依分數、分段均勻），再逐句套用跨句去重。
        // 順序很重要：若先去重再選句，去重會讓後段的歌詞大量落空，
        // 整首歌的空就會全部擠在副歌之前。
        val choice = chooseLines(eligible, difficulty, maxBlanks)
        val chosenIndices = choice.indices

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
                selections += Selection(
                    line = line,
                    chosen = null,
                    rejected = rejected,
                    candidates = candidates,
                )
                continue
            }

            // 同一原形不重複；被前面的句子用掉時，退而求其次的那個字也要有價值，
            // 否則會出現「這句的最佳字被用過 → 挖一個低分字」的漏洞
            // 句內仍取分數最高的候選；被前面的句子用掉原形時，才退而求其次。
            // （「哪幾句值得出題」已經由 chooseLines 的兩把鑰匙決定，這裡不再二次過濾，
            //   否則重複字多的歌會整首只剩兩三題。）
            val available = candidates.filter { it.level.lemma !in usedLemmas }
            val picked = available.firstOrNull()

            if (picked == null) {
                selections += Selection(
                    line = line,
                    chosen = null,
                    rejected = rejected,
                    basis = "此句的候選都已被前面的句子挖過（lemma 不重複）",
                    candidates = candidates,
                )
                continue
            }

            usedLemmas += picked.level.lemma
            selections += Selection(
                line = line,
                chosen = picked,
                rejected = rejected,
                basis = describeBasis(picked, available.drop(1), difficulty),
                candidates = candidates,
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
        casing: LyricCasing,
        titleWords: Set<String>,
    ): Scan {
        val candidates = mutableListOf<Candidate>()
        val rejected = mutableListOf<RejectedCandidate>()

        FillChoiceBuilder.TOKEN_PATTERN.findAll(line.text).forEach { match ->
            val token = match.value
            if (token.length < FillChoiceBuilder.BLANK_MIN_WORD_LENGTH) return@forEach

            // 0. 縮寫（`time's` / `don't`）——字幹可能查得到等級，但挖它當題目
            //    等於要使用者記住一個縮寫，不是詞彙。
            if (token.any { it == '\'' || it == '’' }) {
                rejected += RejectedCandidate(token, CONTRACTION_SOURCE)
                return@forEach
            }

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

            // 2.5 歌名本身就是這個字（`Hello`、`Yellow`）→ 不挖：
            //     使用者一定早就看到，挖了只是送分。
            if (normalizeForMatch(token) in titleWords) {
                rejected += RejectedCandidate(token, TITLE_WORD_SOURCE)
                return@forEach
            }

            // 3. 詞頻前 500（只擋詞形本身；`years` 的複數形沒有排名，所以可以挖）
            val rank = levelInfo.surfaceRank
            if (rank != null && rank <= HARD_FREQUENCY_LIMIT) {
                rejected += RejectedCandidate(token, HARD_FREQUENCY_SOURCE)
                return@forEach
            }

            val score = WordValue.score(
                level = levelInfo.level,
                rank = levelInfo.commonnessRank,
                partOfSpeech = levelInfo.partOfSpeech,
                surface = token,
                lemma = levelInfo.lemma,
                difficulty = difficulty,
                capitalizedOnly = casing.isCapitalizedOnly(token),
                occurrences = casing.occurrences(token),
            )

            candidates += Candidate(
                word = token,
                start = match.range.first,
                end = match.range.last + 1,
                level = levelInfo,
                score = score,
            )
        }

        // 分數高的排前面；同分才用稀有度／長度／位置決勝（保持穩定）
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

    /** 縮寫（含撇號）不挖：那是語法黏著，不是詞彙。 */
    private const val CONTRACTION_SOURCE = "縮寫"

    /** 歌名本身就是這個字（`Hello`、`Yellow`）→ 不挖。 */
    private const val TITLE_WORD_SOURCE = "歌詞標題詞"

    /**
     * 依難度決定的**地板分數**。
     *
     * 低於地板的句子不列入選題池（除非題數不足 [MIN_BLANKS]，見 [chooseLines]）。
     * 數字是設計值：A1 `sorry`（等級契合 0 + 稀缺 18 + 詞性 20 + 詞形 15 + 語境 10
     * − 常見 10 = 53）在高階難度下仍會被扣到地板以下；B1 `desperate` 約 67。
     */
    /** 歌名切成小寫字集合（含去所有格）。 */
    private fun titleWordsOf(title: String): Set<String> = LyricCasing.titleWords(title)

    /** 比對用的正規化：小寫、去所有格 `'s`。 */
    private fun normalizeForMatch(token: String): String = LyricCasing.matchKey(token)

    /** 沒有詞頻排名的字當成「比任何有排名的字更罕見」。 */
    private const val UNRANKED = Int.MAX_VALUE

    /**
     * 排序：**學習價值分數**（[WordValue]）為主。
     *
     * 舊版的「難度偏好 → 等級 → 詞頻」三層比較已經併進分數裡
     * （`levelFit` 就是難度偏好），這裡只保留同分時的決勝條件，
     * 讓輸出可重現。
     */
    private fun comparator(difficulty: BlankDifficulty) = Comparator<Candidate> { a, b ->
        val byScore = b.score - a.score
        if (byScore != 0) return@Comparator byScore

        // 同分：罕見者優先
        val rankA = a.level.frequencyRank ?: UNRANKED
        val rankB = b.level.frequencyRank ?: UNRANKED
        val byRank = rankB.compareTo(rankA)
        if (byRank != 0) return@Comparator byRank

        val byLength = b.word.length - a.word.length
        if (byLength != 0) return@Comparator byLength

        a.start - b.start
    }

    /**
     * 依分數挑句子（新選詞系統的核心）。
     *
     * ## 為什麼不是「均勻抽樣句子」
     *
     * 舊版（`pickEvenly`）只按**行號**均勻取樣：取到的句子若只有 A1 字，
     * 還是會挖那個 A1 字。分數過濾 + 分段取最佳之後：
     * ```
     * 1. 先用地板分數把「不值得學的句子」濾掉
     * 2. 把剩下的句子切成 K 段（K = 題數），每段取分數最高的那一句
     * ```
     * 分散度與舊版相同（不會擠在副歌），但每一題都是該區段裡最值得學的字。
     *
     * ## 兩把鑰匙：等級要夠、或分數要很高
     *
     * 只用一個分數門檻會有漏洞：A1 的 `sorry` 靠「詞性 + 詞形 + 語境」也能拿 51 分，
     * 於是在 50 分的地板下又被挖了出來。因此每一題要通過：
     * ```
     * score >= 分數門檻   而且   (等級 >= 這次難度的下限   或   score >= 極高分門檻)
     * ```
     * 「A2 但很罕見」的字（`beg` 70、`apart` 74）還能進來；
     * 「A1 但很普通」的字（`sorry` 51、`lovely` 51）就不行。
     *
     * ## 地板太高怎麼辦
     *
     * 若嚴格過濾後不足 [MIN_BLANKS] 句，依分數由高到低放寬（仍然依分數排序），
     * 直到湊到 [MIN_BLANKS]——寧可少出題，也不要整頁都是 `sorry`。
     */
    private data class Choice(val indices: Set<Int>)

    /** 這次難度的分數門檻（所有候選都要過）。 */
    private fun scoreCutFor(difficulty: BlankDifficulty): Int = when (difficulty) {
        BlankDifficulty.BASIC -> 35
        BlankDifficulty.ADVANCED -> 50
        BlankDifficulty.CHALLENGE -> 60
    }

    /** 等級不到下限時，必須拿到的「極高分」（代表這個字罕見到值得學）。 */
    private fun hardCutFor(difficulty: BlankDifficulty): Int = when (difficulty) {
        BlankDifficulty.BASIC -> 0
        BlankDifficulty.ADVANCED -> 65
        BlankDifficulty.CHALLENGE -> 75
    }

    /** 這個候選夠不夠格被挖（見上面「兩把鑰匙」）。 */
    private fun isValuable(candidate: Candidate, difficulty: BlankDifficulty): Boolean {
        if (candidate.score < scoreCutFor(difficulty)) return false
        val level = candidate.level.level
        val levelOk = level != null && level >= difficulty.minLevel
        return levelOk || candidate.score >= hardCutFor(difficulty)
    }

    private fun chooseLines(
        eligible: List<Scan>,
        difficulty: BlankDifficulty,
        maxBlanks: Int,
    ): Choice {
        if (maxBlanks <= 0 || eligible.isEmpty()) return Choice(emptySet())

        val best = eligible.associate { scan ->
            scan.line.index to scan.candidates.first()
        }
        val bestScore = best.mapValues { it.value.score }

        val strict = eligible.filter { isValuable(best.getValue(it.line.index), difficulty) }
        val pool = strict.toMutableList()

        // 嚴格過濾不足 MIN_BLANKS → 依分數放寬（低分的句子只補到剛好夠用）
        val wanted = minOf(MIN_BLANKS, eligible.size)
        if (pool.size < wanted) {
            val pickedLines = pool.map { it.line.index }.toSet()
            eligible
                .filter { it.line.index !in pickedLines }
                .sortedByDescending { bestScore[it.line.index] ?: 0 }
                .forEach { scan ->
                    if (pool.size >= wanted) return@forEach
                    pool += scan
                }
        }

        val target = minOf(maxBlanks, pool.size)
        return Choice(pickBySegment(pool.map { it.line.index }, bestScore, target))
    }

    /**
     * 把候選句子切成 [count] 段，每段取**分數最高**的那一句。
     *
     * @param indices 已排序的句子行號
     * @param scores 行號 → 該句最佳候選分數
     */
    fun pickBySegment(indices: List<Int>, scores: Map<Int, Int>, count: Int): Set<Int> {
        if (count <= 0 || indices.isEmpty()) return emptySet()
        if (count >= indices.size) return indices.toSet()

        val picked = LinkedHashSet<Int>(count)
        for (i in 0 until count) {
            val from = i * indices.size / count
            val to = ((i + 1) * indices.size / count).coerceAtLeast(from + 1)
            val segment = indices.subList(from, minOf(to, indices.size))
            val best = segment.maxOf { scores[it] ?: 0 }
            val centre = (from + minOf(to, indices.size) - 1) / 2.0
            // 同分時取「離區段中心最近」的那一句：分數優先，但不會全部擠在區段開頭
            picked += segment
                .filter { (scores[it] ?: 0) == best }
                .minByOrNull { kotlin.math.abs(indices.indexOf(it) - centre) }
                ?: segment.first()
        }
        return picked
    }

    /**
     * 依「均勻分布」挑句子（舊版演算法，保留給既有測試與需要純行號取樣的場合）。
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

    /** 偵錯用的排序依據說明（報表會直接印出來）。 */
    private fun describeBasis(
        picked: Candidate,
        runnersUp: List<Candidate>,
        difficulty: BlankDifficulty,
    ): String {
        val next = runnersUp.firstOrNull()
        val head = "分數 ${picked.score}/${WordValue.MAX}"
        val level = picked.level.treatAsLevel.label +
            if (picked.level.isUnlisted) "（未收錄）" else ""
        if (next == null) {
            return "$head；該句唯一合格候選（$level）"
        }
        if (next.score != picked.score) {
            return "$head > ${next.score}（${picked.word} 勝 ${next.word}，$level）"
        }
        return "$head；同分取罕見（${picked.word} vs ${next.word}）"
    }
}
