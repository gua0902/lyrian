package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.domain.model.LyricLine

/**
 * 整首歌的**大小寫統計**。
 *
 * 單看一個 token 判斷不出「這是專有名詞還是句首大寫」，所以先把整首歌掃一遍，
 * 記下每個字出現幾次、幾次大寫、幾次在句首。
 *
 * 兩條規則：
 * ```
 * looksProper          每次都大寫，而且從來不在句首  → 專有名詞（`California`）→ 排除
 * isCapitalizedOnly    每次都大寫，但包含句首        → 歌詞強調（`Highs`、`Gave`）→ 扣分
 * ```
 *
 * 由 `BlankSelector` 與 `ExtensionPicker` 共用——兩邊對「專有名詞」的定義
 * 必須一致，否則會出現「不能挖，但可以被推薦學習」這種矛盾。
 */
internal class LyricCasing(lines: List<LyricLine>) {

    private val total = HashMap<String, Int>()
    private val capitalized = HashMap<String, Int>()
    private val atLineStart = HashMap<String, Int>()

    init {
        lines.forEach { line ->
            FillChoiceBuilder.TOKEN_PATTERN.findAll(line.text).forEachIndexed { order, match ->
                val key = normalize(match.value)
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

    /** 永遠大寫、而且從不出現在句首 → 專有名詞。 */
    fun looksProper(word: String): Boolean {
        val key = normalize(word)
        val all = total[key] ?: return false
        val caps = capitalized[key] ?: 0
        val starts = atLineStart[key] ?: 0
        return caps == all && starts == 0
    }

    /** 這首歌出現幾次（副歌重複的字扣分用）。 */
    fun occurrences(word: String): Int = total[normalize(word)] ?: 1

    /** 每次都大寫（但包含句首）→ 歌詞強調，扣分但不排除。 */
    fun isCapitalizedOnly(word: String): Boolean {
        val key = normalize(word)
        val all = total[key] ?: return false
        return (capitalized[key] ?: 0) == all
    }

    companion object {
        /** 小寫化並統一彎引號。 */
        fun normalize(token: String): String = token.lowercase().replace('’', '\'')

        /** 比對用的正規化：小寫、去所有格 `'s`（`Hello's` 也算標題詞）。 */
        fun matchKey(token: String): String {
            val lower = normalize(token)
            return if (lower.endsWith("'s")) lower.dropLast(2) else lower
        }

        /** 把歌名切成比對用的字集合。 */
        fun titleWords(title: String): Set<String> =
            FillChoiceBuilder.TOKEN_PATTERN.findAll(title)
                .map { matchKey(it.value) }
                .filter { it.isNotEmpty() }
                .toSet()
    }
}
