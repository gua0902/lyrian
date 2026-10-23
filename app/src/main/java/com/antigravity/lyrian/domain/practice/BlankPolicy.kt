package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.data.lang.WordLevel
import com.antigravity.lyrian.data.lang.WordLevelSource
import com.antigravity.lyrian.domain.model.CefrLevel

/**
 * 一個候選字能否挖空，以及為什麼。
 *
 * 挖空規則自始至終都是「**先判斷、再挖**」：命中等級表 → 濾掉不合格的 → 剩下的才挖。
 * 這個物件把那段判斷收斂成單一出口，讓 `FillChoiceBuilder` 只負責切字與挑字。
 */
object BlankPolicy {

    /** 詞頻表的說明文字（顯示在偵錯輸出與過濾清單）。 */
    const val FREQUENCY_SOURCE = "BNC 前 2000 高頻詞"

    /** 判定結果。 */
    sealed interface Verdict {
        val token: String
    }

    /**
     * 可以挖。
     *
     * @property level 該字的等級判定（含原形與來源）
     */
    data class Accepted(override val token: String, val level: WordLevel) : Verdict {
        /** 偵錯輸出用的一行說明，例如 `silhouette | CEFR-J 之上的 Octanove C1`。 */
        val sourceLabel: String get() = level.debugLabel()
    }

    /** 不能挖，附原因。 */
    data class Rejected(override val token: String, val reason: String) : Verdict

    /**
     * 判斷一個字。
     *
     * 檢查順序（成本由低到高，也符合「現有規則優先」的要求）：
     *  1. 現有規則：[CommonWordFilter]（Top 1000 + 代名詞 + be/do/have + 冠詞 + 介系詞 + 助動詞）
     *  2. 詞頻表：BNC 前 2000（含詞形還原與縮寫展開）
     *  3. 等級表：低於 [minLevel] 的字不挖
     *
     * @param minLevel 使用者設定的最低等級；預設 B1
     */
    fun evaluate(
        token: String,
        minLevel: CefrLevel,
        source: WordLevelSource,
        minWordLength: Int = FillChoiceBuilder.BLANK_MIN_WORD_LENGTH,
    ): Verdict {
        if (token.length < minWordLength) {
            return Rejected(token, "太短（少於 $minWordLength 個字母）")
        }

        val common = CommonWordFilter.classify(token)
        if (common.isCommon) {
            return Rejected(token, common.rejectionLabel())
        }

        // 縮寫展開後的字也要查詞頻：`don't` 展開成 `do`，而 `do` 是最高頻的字之一。
        // （CommonWordFilter 已經擋掉 don't 了，這裡是雙重保險，也涵蓋 it's/that's 之類）
        val expanded = CommonWordFilter.expandContraction(token)
        if (expanded.any { source.isHighFrequency(it) }) {
            return Rejected(token, FREQUENCY_SOURCE)
        }

        val level = source.lookup(token)
        if (level.treatAsLevel < minLevel) {
            return Rejected(
                token,
                "${level.source} ${level.level?.label.orEmpty()}（低於 ${minLevel.label}）".trim(),
            )
        }

        return Accepted(token, level)
    }
}

/**
 * 偵錯輸出用：`abandoned→abandon | CEFR-J B2`。沒有原形變化時只顯示一層。
 */
fun WordLevel.debugLabel(): String {
    val head = if (lemma != surface) "$surface→$lemma" else surface
    val levelPart = level?.let { "${source} ${it.label}" } ?: source
    return "挖空 $head | 等級來源: $levelPart"
}
