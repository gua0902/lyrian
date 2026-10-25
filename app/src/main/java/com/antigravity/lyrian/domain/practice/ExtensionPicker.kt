package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.data.lang.LanguageData
import com.antigravity.lyrian.domain.model.LyricLine

/**
 * 結果頁的「延伸詞彙」：本首歌中**沒被挖空、而且值得學**的字。
 *
 * ## 與舊版的差別
 *
 * 舊版只挑「等級高於難度門檻」的字，於是清單常常是「這首歌裡其他難字」——
 * 可能包含專有名詞、歌名、縮寫、或根本沒被挖到的重複字。
 * 新版用**同一套學習價值分數**（[WordValue]）排序，並加上三道過濾：
 *
 * ```
 * 1. 只要實詞（名詞／動詞／形容詞／副詞；詞性未知者保留，由分數決定）
 * 2. 排除：已挖空、專有名詞、歌名本身、縮寫、功能詞、高頻詞
 * 3. 分數 >= [MIN_SCORE]；同一詞性最多 [MAX_PER_POS] 個（避免整份都是名詞）
 * ```
 *
 * 抽成獨立物件（而不是留在 `PracticeViewModel`）是為了讓報表工具
 * （`BlankReportTest`）印出**真正**的清單，而不是自己再寫一份規則。
 */
object ExtensionPicker {

    /** 延伸詞彙上限（擁有者指定最多 20 條）。 */
    const val LIMIT = 20

    /** 低於這個分數就不值得推薦。 */
    const val MIN_SCORE = 40

    /** 同一詞性的上限；超過就讓位給其他詞性。 */
    const val MAX_PER_POS = 12

    private const val UNKNOWN_POS = "?"

    /**
     * @param lines 整首歌詞
     * @param difficulty 這一輪的難度（影響分數的等級契合）
     * @param language 等級資料
     * @param blankedLemmas 已經被挖空的**原形**（`dreaming` 被挖 → 這裡是 `dream`）
     * @param songTitle 歌名；歌名本身就是歌詞裡的字時不推薦
     * @return 依學習價值排序後的原形清單
     */
    fun pick(
        lines: List<LyricLine>,
        difficulty: BlankDifficulty,
        language: LanguageData,
        blankedLemmas: Set<String>,
        songTitle: String = "",
    ): List<String> {
        val casing = LyricCasing(lines)
        val titleWords = LyricCasing.titleWords(songTitle)

        data class Entry(val lemma: String, val score: Int, val posKey: String, val rank: Int)

        val byLemma = LinkedHashMap<String, Entry>()

        lines.forEach { line ->
            FillChoiceBuilder.TOKEN_PATTERN.findAll(line.text).forEach { match ->
                val token = match.value
                if (token.length < FillChoiceBuilder.BLANK_MIN_WORD_LENGTH) return@forEach
                if (token.any { it == '\'' || it == '’' }) return@forEach
                if (CommonWordFilter.classify(token).isCommon) return@forEach
                if (casing.looksProper(token)) return@forEach
                if (LyricCasing.matchKey(token) in titleWords) return@forEach
                if (language.isHighFrequency(token)) return@forEach

                val info = language.lookup(token)
                if (info.isProperNoun) return@forEach
                // 同一原形已被挖空 → 不再推薦（`dreaming` 挖過就不再推 `dream`）
                if (info.lemma in blankedLemmas) return@forEach

                // 詞性已知但**不是實詞**（介系詞／代名詞…）→ 不值得推薦。
                // 詞性未知（資料裡沒有）則保留，由分數決定。
                val knownPos = info.partOfSpeech
                if (knownPos != null &&
                    WordValue.posValue(info.allPartsOfSpeech.ifEmpty { setOf(knownPos) }) < 20
                ) {
                    return@forEach
                }

                val score = WordValue.score(
                    level = info.level,
                    rank = info.commonnessRank,
                    partOfSpeech = knownPos,
                    surface = token,
                    lemma = info.lemma,
                    difficulty = difficulty,
                    capitalizedOnly = casing.isCapitalizedOnly(token),
                    occurrences = casing.occurrences(token),
                    partsOfSpeech = info.allPartsOfSpeech,
                )
                if (score < MIN_SCORE) return@forEach

                val entry = Entry(
                    lemma = info.lemma,
                    score = score,
                    posKey = knownPos?.trim()?.trimEnd('.')?.lowercase() ?: UNKNOWN_POS,
                    rank = info.frequencyRank ?: Int.MAX_VALUE,
                )
                val previous = byLemma[info.lemma]
                if (previous == null || entry.score > previous.score) {
                    byLemma[info.lemma] = entry
                }
            }
        }

        val perPos = HashMap<String, Int>()
        val picked = mutableListOf<String>()
        for (entry in byLemma.values.sortedWith(
            compareByDescending<Entry> { it.score }.thenByDescending { it.rank }.thenBy { it.lemma },
        )) {
            val used = perPos[entry.posKey] ?: 0
            if (used >= MAX_PER_POS) continue
            perPos[entry.posKey] = used + 1
            picked += entry.lemma
            if (picked.size >= LIMIT) break
        }
        return picked
    }
}
