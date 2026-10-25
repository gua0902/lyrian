package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.data.lang.WordLevel
import com.antigravity.lyrian.data.lang.WordLevelSource
import com.antigravity.lyrian.domain.model.CefrLevel
import com.antigravity.lyrian.domain.model.LyricLine
import com.antigravity.lyrian.domain.model.RejectedCandidate

/**
 * 挖空難度（模組 C-4）。
 *
 * **難度決定「哪些字值得挖」，不決定題數。**
 * 題數由 [BlankCountPolicy] 決定（下限 20、句子多時 0.6 倍）。
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
 * ## 規則（擁有者指定，2026-10 重構）
 *
 * ```
 * 1. 以句為單位，每句最多一個空
 * 2. 候選池 = 該句所有實詞
 * 3. 硬性排除：功能詞（冠詞/介系詞/代名詞/連接詞/助動詞/數詞）、
 *              專有名詞、詞頻前 500 名
 * 4. 每個候選算學習價值分數（WordValue），每句只留最高的那一個
 * 5. 題數由 BlankCountPolicy 決定（下限 20、句子多時 0.6 倍、上限 maxBlanks）
 * 6. 先從「通過兩把鑰匙」的嚴格池分段取最佳；不足保量時用剩下的句子分段補齊
 * 7. 跨句 lemma 不重複（同一原形全曲只挖一次）
 * 8. 純感嘆詞（Oh / Yeah / La la）不挖
 * ```
 *
 * ## 與上一版的差別
 *
 * 上一版「湊不到 5 題就少出」，Yellow 只有 3 題。現在保量下限 20：
 * 嚴格池不夠時依分數往下補，補進來的句子會標記成 filler，
 * 報表與結果頁都看得出來「哪幾題是為了湊數」。
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
     * 保量下限（擁有者 2026-10 核定）。
     *
     * 舊版是 5——「湊不到就少出」，於是 Yellow 只有 3 題。現在由
     * [BlankCountPolicy] 統一決定，這裡保留常數是為了讓舊呼叫端能被編譯器
     * 抓到（見 `@Deprecated` 訊息）。
     */
    @Deprecated(
        "改用 BlankCountPolicy.MIN_BLANKS（20）",
        ReplaceWith("BlankCountPolicy.MIN_BLANKS"),
    )
    const val MIN_BLANKS = 5

    /** 一個候選字。 */
    data class Candidate(
        val word: String,
        val start: Int,
        val end: Int,
        val level: WordLevel,
        /** 學習價值分數（[WordValue]）；選句與排序都看它。 */
        val score: Int = 0,
        /**
         * 這首歌裡每次都是大寫（`Gave`、`Highs`）。
         *
         * 這種字**不能當保量補題**：挖一個句中被大寫的字，答案大小寫會有歧義，
         * 而且九成是歌詞的強調寫法，不是詞彙。
         */
        val capitalizedOnly: Boolean = false,
    )

    /** 這一句是不是「保量補題」（嚴格池不夠時才被抓進來）。 */
    data class Selection(
        val line: LyricLine,
        val chosen: Candidate?,
        val rejected: List<RejectedCandidate>,
        val basis: String? = null,
        /** 這句是完全沒有實詞的感嘆句（Oh / Yeah / La la） */
        val skippedAsFiller: Boolean = false,
        /** 這一句所有合格候選（已依分數排序）；報表要列出「候選有哪些、各幾分」。 */
        val candidates: List<Candidate> = emptyList(),
        /** 這題是為了湊到保量下限才選的（分數低於兩把鑰匙）。 */
        val filler: Boolean = false,
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
        val byIndex = scanned.associateBy { it.line.index }
        val eligible = scanned.filter { it.candidates.isNotEmpty() }

        // 選句與跨句去重**在同一個迴圈裡**完成。
        //
        // 舊版先選句、再逐句去重，於是「這句的最佳字被前面挖過」的句子整句落空，
        // 保量下限永遠達不到（Yellow 37 句只剩 11 題就是這樣來的）。
        // 現在 `chooseLines` 在挑字的當下就檢查 lemma，並且會退而取該句的
        // 次佳候選（同樣要通過兩把鑰匙）。
        val target = BlankCountPolicy.target(eligible.size, maxBlanks)
        val chosen = chooseLines(eligible, difficulty, target)
        val usedLemmas = chosen.values.mapTo(HashSet()) { it.candidate.level.lemma }

        val selections = ArrayList<Selection>(lines.size)
        for (line in lines) {
            val entry = byIndex[line.index]
            val candidates = entry?.candidates.orEmpty()
            val rejected = entry?.rejected.orEmpty()

            if (candidates.isEmpty()) {
                selections += Selection(
                    line = line,
                    chosen = null,
                    rejected = rejected,
                    skippedAsFiller = rejected.isEmpty(),
                )
                continue
            }

            val pick = chosen[line.index]
            if (pick == null) {
                // 說得出「為什麼這句沒挖」：全部候選的原形都已被別的句子用掉時
                // 才是去重造成的；其餘是題數額度或分數不足（不硬掰原因）。
                val allUsed = candidates.all { candidate ->
                    candidate.level.lemma in usedLemmas
                }
                selections += Selection(
                    line = line,
                    chosen = null,
                    rejected = rejected,
                    basis = if (allUsed) {
                        "此句的候選原形都已被挖過（lemma 不重複）"
                    } else {
                        null
                    },
                    candidates = candidates,
                )
                continue
            }

            // 同一原形不重複（已在 chooseLines 保證）；這裡只負責說明依據
            val others = candidates.filter { it !== pick.candidate }
            val basis = describeBasis(pick.candidate, others, difficulty)
            selections += Selection(
                line = line,
                chosen = pick.candidate,
                rejected = rejected,
                basis = if (pick.filler) "保量補題：$basis" else basis,
                candidates = candidates,
                filler = pick.filler,
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
                // 看整個詞性集合：`love` 的釋義第一行是 n.、第二行才是 v.
                partsOfSpeech = levelInfo.allPartsOfSpeech,
            )

            candidates += Candidate(
                word = token,
                start = match.range.first,
                end = match.range.last + 1,
                level = levelInfo,
                score = score,
                capitalizedOnly = casing.isCapitalizedOnly(token),
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
     * 這次難度的分數門檻（所有候選都要過）。
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
     */
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

    /**
     * 選句＋選字（**同一個迴圈**，因為 lemma 去重會影響「哪幾句還選得到字」）。
     *
     * ```
     * target = BlankCountPolicy.target(可用句數, maxBlanks)
     * 1. 嚴格池：每段依分數由高到低，取第一個「原形還沒被用過」的字
     * 2. 不足 target：剩下的句子再分段，允許低分（filler），直到達標或沒進展
     * ```
     *
     * @return 行號 → 選中的字（含 filler 標記）
     */
    private fun chooseLines(
        eligible: List<Scan>,
        difficulty: BlankDifficulty,
        target: Int,
    ): Map<Int, Pick> {
        if (target <= 0 || eligible.isEmpty()) return emptyMap()

        val byIndex = eligible.associateBy { it.line.index }
        val bestScore = eligible.associate { scan ->
            scan.line.index to (scan.candidates.firstOrNull()?.score ?: 0)
        }
        val usedLemmas = HashSet<String>(target * 2)
        val chosen = LinkedHashMap<Int, Pick>(target)

        /** 這句還有沒有「原形沒被用過」的字；有的話就挖它。 */
        fun take(lineIndex: Int, allowFiller: Boolean): Boolean {
            val scan = byIndex[lineIndex] ?: return false
            val candidate = scan.candidates.firstOrNull { c ->
                c.level.lemma !in usedLemmas &&
                    (allowFiller || isValuable(c, difficulty)) &&
                    (!allowFiller || c.isAcceptableFiller())
            } ?: return false
            usedLemmas += candidate.level.lemma
            chosen[lineIndex] = Pick(
                candidate = candidate,
                filler = !isValuable(candidate, difficulty),
            )
            return true
        }

        // 1) 嚴格池（這句的最佳字通過兩把鑰匙）→ 分段，逐段往下找
        val strict = eligible
            .filter { isValuable(it.candidates.first(), difficulty) }
            .map { it.line.index }
        segmentLines(strict, bestScore, minOf(target, strict.size)).forEach { segment ->
            segment.firstOrNull { take(it, allowFiller = false) }
        }

        // 2) 保量補題：剩下的句子一樣分段，每段取還選得到字的最高分那句
        while (chosen.size < target) {
            val remaining = eligible.map { it.line.index }.filter { it !in chosen }
            if (remaining.isEmpty()) break

            var progressed = false
            segmentLines(remaining, bestScore, minOf(target - chosen.size, remaining.size))
                .forEach { segment ->
                    if (segment.firstOrNull { take(it, allowFiller = true) } != null) {
                        progressed = true
                    }
                }
            // 沒有進展＝剩下的句子都只剩被用過的原形，再跑一次也是空轉
            if (!progressed) break
        }

        return chosen
    }

    /** 一次選取：選中的字 + 它是不是補題。 */
    private data class Pick(val candidate: Candidate, val filler: Boolean)

    /**
     * 補題的**品質底線**。
     *
     * 擁有者要求「保量、依分數往下拿、不限等級」——但分數 17 分的字（A1 + 每次都大寫
     * + 前 2000 名）挖出來只是浪費使用者的時間，寧可少一題。
     *
     * 這一條不是「等級門檻」（等級可以很低），是「分數門檻」：
     * 補題仍然必須是**像樣的實詞**。兩首歌實測會因此少 1–2 題。
     */
    private const val FILLER_MIN_SCORE = 30

    /** 這句話符合補題的品質底線嗎（見 [FILLER_MIN_SCORE]）。 */
    private fun Candidate.isAcceptableFiller(): Boolean =
        score >= FILLER_MIN_SCORE && !capitalizedOnly

    /**
     * 把候選句子切成 [count] 段，回傳**每段內依分數排序**的行號。
     *
     * 為什麼不是直接每段取第一名：同名原形可能已經被前面的句子用掉，
     * 呼叫端需要「這一段還有哪些句子可選」才知道要不要退而求其次。
     */
    internal fun segmentLines(
        indices: List<Int>,
        scores: Map<Int, Int>,
        count: Int,
    ): List<List<Int>> {
        if (count <= 0 || indices.isEmpty()) return emptyList()
        val k = minOf(count, indices.size)
        val segments = ArrayList<List<Int>>(k)

        for (i in 0 until k) {
            val from = i * indices.size / k
            val to = ((i + 1) * indices.size / k).coerceAtLeast(from + 1)
            val end = minOf(to, indices.size)
            val segment = indices.subList(from, end)
            val centre = (from + end - 1) / 2.0

            // 分數高的排前面；同分時取「離區段中心最近」的那一句
            segments += segment.sortedWith(
                compareByDescending<Int> { scores[it] ?: 0 }
                    .thenBy { kotlin.math.abs(indices.indexOf(it) - centre) },
            )
        }
        return segments
    }

    /**
     * 把候選句子切成 [count] 段，每段取**分數最高**的那一句。
     *
     * @param indices 已排序的句子行號
     * @param scores 行號 → 該句最佳候選分數
     */
    fun pickBySegment(indices: List<Int>, scores: Map<Int, Int>, count: Int): Set<Int> =
        segmentLines(indices, scores, count).mapNotNull { it.firstOrNull() }.toSet()

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
