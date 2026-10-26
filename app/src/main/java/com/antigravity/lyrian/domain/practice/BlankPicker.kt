package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.data.lexicon.LexiconEntry
import com.antigravity.lyrian.data.lexicon.LexiconSource
import com.antigravity.lyrian.data.lexicon.PartOfSpeech
import com.antigravity.lyrian.domain.model.CefrLevel
import com.antigravity.lyrian.domain.model.LyricLine

/**
 * 挖空選詞（2026-10 重寫，取代舊的 `BlankSelector` / `WordValue` /
 * `BlankCountPolicy` / `ExtensionPicker`）。
 *
 * ## 規則：嚴格照擁有者給的順序
 *
 * ```
 * 1. 正規化：NFD 去變音符號 → 小寫（TextNormalizer）
 * 2. 抽候選：實詞、長度 ≥ 3、非功能詞；以原形去重（全曲一個字只一個候選）
 * 3. 排除：非句首且首字母大寫、詞性 PROPN、詞典查無此詞、CEFR < B1
 * 4. 全域排序：詞頻由罕見到常見；沒有詞頻的排在有詞頻的之後，以 CEFR 為序
 * 5. 取詞：由上往下，每句最多 2 個，直到 20 個或候選耗盡
 * 6. 不足 20 → 如實回報 shortfall，**不補 A2、不補停用詞、不補專名**
 * ```
 *
 * ## 舊邏輯全部移除
 *
 * 沒有分數、沒有兩把鑰匙、沒有「保量補題」、沒有難度參數、沒有分段
 * （`pickBySegment`）、沒有「取不到就往下放寬」。規則就是上面六條。
 *
 * ## 為什麼沒有 `difficulty`
 *
 * 門檻固定 B1+。難度設定已從 App 移除——留著只會讓人以為它還有作用。
 */
object BlankPicker {

    /** 目標挖空數。 */
    const val TARGET_BLANKS = 20

    /** 同一句最多幾個空。 */
    const val MAX_BLANKS_PER_LINE = 2

    /** 太短的字不挖（`a`、`of`、`oh`）。 */
    const val MIN_WORD_LENGTH = 3

    /** 候選門檻：B1 及以上。 */
    val MIN_LEVEL: CefrLevel = CefrLevel.B1

    /** 一次出現的位置。 */
    data class Occurrence(
        val lineIndex: Int,
        val start: Int,
        val end: Int,
        val surface: String,
    )

    /** 一個通過所有條件的候選（已依原形去重）。 */
    data class Candidate(
        val lemma: String,
        /** 第一次出現的表面形式（顯示用）。 */
        val surface: String,
        val cefr: CefrLevel,
        val frequencyRank: Int?,
        val partsOfSpeech: Set<PartOfSpeech>,
        val gloss: String?,
        val occurrences: List<Occurrence>,
    ) {
        /** 顯示用的詞性（優先實詞）。 */
        val displayPos: String?
            get() = partsOfSpeech
                .firstOrNull { it != PartOfSpeech.UNKNOWN && it != PartOfSpeech.OTHER }
                ?.label
    }

    /** 一個挖空位置。 */
    data class Blank(
        val lineIndex: Int,
        val start: Int,
        val end: Int,
        /** 原始表面形式（上下文顯示用）。 */
        val surface: String,
        /** 正規化後的答案（比對與作答都用這個）。 */
        val answer: String,
        val lemma: String,
    )

    /** 被排除的字與原因（報表／除錯用）。 */
    data class Rejected(val word: String, val reason: String)

    /**
     * @property blanks 依歌詞順序排列的挖空
     * @property pool 全部通過條件的 B1+ 候選（結果頁的「較難單字」清單）
     * @property shortfall 距離 20 題還差幾個（0 = 達標）
     */
    data class Result(
        val blanks: List<Blank>,
        val pool: List<Candidate>,
        val shortfall: Int,
        val rejected: List<Rejected>,
    )

    /**
     * 選詞。
     *
     * @param lines 整首歌詞（已解析時間軸）
     * @param lexicon 詞典；`lookup` 回傳 null 的字一律排除
     */
    suspend fun pick(lines: List<LyricLine>, lexicon: LexiconSource): Result {
        if (lines.isEmpty()) return Result(emptyList(), emptyList(), TARGET_BLANKS, emptyList())

        val casing = SongCasing(lines)
        val rejected = ArrayList<Rejected>()
        val seenRejections = HashSet<String>()

        fun reject(word: String, reason: String) {
            if (seenRejections.add("$word|$reason")) rejected += Rejected(word, reason)
        }

        // 正規化詞 → 出現位置
        val occurrences = LinkedHashMap<String, MutableList<Occurrence>>()
        val entries = HashMap<String, LexiconEntry?>()

        lines.forEach { line ->
            TextNormalizer.TOKEN.findAll(line.text).forEach { match ->
                val surface = match.value
                if (surface.length < MIN_WORD_LENGTH) return@forEach
                val word = TextNormalizer.normalize(surface)
                if (word.length < MIN_WORD_LENGTH) return@forEach

                // 1. 停用詞／功能詞（冠詞、代名詞、介系詞、連接詞、助動詞、數詞、感嘆詞）
                if (CommonWordFilter.classify(word).isFunctionWord()) {
                    reject(word, "功能詞")
                    return@forEach
                }

                // 2. 專有名詞嫌疑（非句首的大寫）
                if (casing.isSuspicious(word)) {
                    reject(word, "專有名詞嫌疑（非句首大寫）")
                    return@forEach
                }

                occurrences.getOrPut(word) { mutableListOf() } += Occurrence(
                    lineIndex = line.index,
                    start = match.range.first,
                    end = match.range.last + 1,
                    surface = surface,
                )
            }
        }

        // 3. 查詞典 → 過濾
        val byLemma = LinkedHashMap<String, Candidate>()
        for ((word, occ) in occurrences) {
            val entry = entries.getOrPut(word) { lexicon.lookup(word) }

            if (entry == null) {
                reject(word, "詞典查無此詞")
                continue
            }
            if (PartOfSpeech.PROPN in entry.partsOfSpeech) {
                reject(word, "專有名詞（PROPN）")
                continue
            }
            val cefr = entry.cefr
            if (cefr == null) {
                reject(word, "查無 CEFR 等級")
                continue
            }
            if (cefr < MIN_LEVEL) {
                reject(word, "CEFR ${cefr.label}（低於 B1）")
                continue
            }

            val existing = byLemma[entry.lemma]
            if (existing == null) {
                byLemma[entry.lemma] = Candidate(
                    lemma = entry.lemma,
                    surface = occ.first().surface,
                    cefr = cefr,
                    frequencyRank = entry.frequencyRank?.takeIf { it > 0 },
                    partsOfSpeech = entry.partsOfSpeech,
                    gloss = entry.gloss,
                    occurrences = occ.toList(),
                )
            } else {
                // 同一個原形（`dream` / `dreaming`）→ 合併出現位置，仍只算一個候選
                byLemma[entry.lemma] = existing.copy(
                    occurrences = existing.occurrences + occ,
                    frequencyRank = existing.frequencyRank ?: entry.frequencyRank?.takeIf { it > 0 },
                )
            }
        }

        val pool = byLemma.values.sortedWith(candidateOrder)
        if (pool.isEmpty()) return Result(emptyList(), emptyList(), TARGET_BLANKS, rejected)

        // 4. 全域取詞（每句最多 2 個）
        val lineCounts = HashMap<Int, Int>()
        val blanks = ArrayList<Blank>(TARGET_BLANKS)

        for (candidate in pool) {
            if (blanks.size >= TARGET_BLANKS) break
            val occurrence = candidate.occurrences.firstOrNull { occ ->
                (lineCounts[occ.lineIndex] ?: 0) < MAX_BLANKS_PER_LINE
            } ?: continue

            lineCounts[occurrence.lineIndex] = (lineCounts[occurrence.lineIndex] ?: 0) + 1
            blanks += Blank(
                lineIndex = occurrence.lineIndex,
                start = occurrence.start,
                end = occurrence.end,
                surface = occurrence.surface,
                answer = TextNormalizer.normalize(occurrence.surface),
                lemma = candidate.lemma,
            )
        }

        val ordered = blanks.sortedWith(compareBy({ it.lineIndex }, { it.start }))
        return Result(
            blanks = ordered,
            pool = pool,
            shortfall = (TARGET_BLANKS - ordered.size).coerceAtLeast(0),
            rejected = rejected,
        )
    }

    /**
     * 全域排序。
     *
     * ```
     * 1. 有詞頻的排在沒詞頻的前面（沒詞頻無法證明「罕見」）
     * 2. 詞頻排名由大到小（越罕見越前）
     * 3. 沒有詞頻時：CEFR 由高到低
     * 4. 最後用原形排序，確保輸出可重現
     * ```
     */
    private val candidateOrder: Comparator<Candidate> =
        compareBy<Candidate>(
            { if (it.frequencyRank == null) 1 else 0 },
            { -(it.frequencyRank ?: 0) },
            { -(it.cefr.ordinal) },
            { it.lemma },
        )

    /**
     * 功能詞判定。
     *
     * `CommonWordFilter` 的 `TOP_1000` 分類**不算**功能詞——那只是「很常見」，
     * 而「常見」已經由 CEFR 門檻處理。這裡只排除真正沒有詞彙意義的類別。
     */
    private fun CommonWordFilter.Level.isFunctionWord(): Boolean =
        category != null && category != CommonWordFilter.Category.TOP_1000
}
