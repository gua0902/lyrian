package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.data.lang.LanguageData
import com.antigravity.lyrian.domain.model.LyricLine

/**
 * 結果頁的「延伸詞彙」：本首歌中**沒被挖空**、等級高於這次難度的字。
 *
 * 抽成獨立物件（而不是留在 `PracticeViewModel` 裡）有兩個理由：
 *  1. 報表工具（`BlankReportTest`）要能印出**真正**的延伸詞彙清單，
 *     不能自己再寫一份「看起來差不多」的規則——那樣報表就不算驗證了
 *  2. 這是純函式，規則調整時可以直接單元測試
 *
 * 規則（擁有者指定）：**依原形去重、等級高者優先、最多 [LIMIT] 條**。
 */
object ExtensionPicker {

    /** 延伸詞彙上限（擁有者指定最多 20 條）。 */
    const val LIMIT = 20

    /**
     * @param lines 整首歌詞
     * @param difficulty 這一輪的難度（決定門檻 [BlankDifficulty.minLevel]）
     * @param language 等級資料
     * @param blanked 已經被挖空（或已在題目中）的**字面形式**（小寫）
     * @return 依等級／罕見度排序後的原形清單
     */
    fun pick(
        lines: List<LyricLine>,
        difficulty: BlankDifficulty,
        language: LanguageData,
        blanked: Set<String>,
    ): List<String> {
        val floor = difficulty.minLevel

        // lemma → 排序鍵（等級序、詞頻排名），取較難的那一個
        data class Key(val level: Int, val rank: Int)

        val byLemma = LinkedHashMap<String, Key>()

        lines.forEach { line ->
            FillChoiceBuilder.TOKEN_PATTERN.findAll(line.text).forEach { match ->
                val token = match.value
                if (token.length < FillChoiceBuilder.BLANK_MIN_WORD_LENGTH) return@forEach
                if (token.lowercase() in blanked) return@forEach
                if (CommonWordFilter.classify(token).isCommon) return@forEach
                if (language.isHighFrequency(token)) return@forEach

                val level = language.lookup(token)
                // 專有名詞不是詞彙（`California` 不該出現在「值得學的字」裡）
                if (level.isProperNoun) return@forEach
                if (level.treatAsLevel < floor) return@forEach

                val key = Key(level.treatAsLevel.ordinal, level.frequencyRank ?: Int.MAX_VALUE)
                val previous = byLemma[level.lemma]
                if (previous == null || key.level > previous.level ||
                    (key.level == previous.level && key.rank > previous.rank)
                ) {
                    byLemma[level.lemma] = key
                }
            }
        }

        return byLemma.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Key>> { it.value.level }
                    .thenByDescending { it.value.rank }
                    .thenBy { it.key },
            )
            .map { it.key }
            .take(LIMIT)
    }
}
