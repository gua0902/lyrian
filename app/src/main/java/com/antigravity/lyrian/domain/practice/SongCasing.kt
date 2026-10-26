package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.domain.model.LyricLine

/**
 * 一首歌的**大小寫分佈**——只用來判斷「這個字是不是專有名詞嫌疑」。
 *
 * ## 規則（規格原文）
 *
 * ```
 * 若詞在歌詞中首字母大寫且不是句首 → 列入專有名詞嫌疑 → 排除
 * ```
 *
 * 「句首」的定義是**該行第一個 token**。因此：
 *
 * ```
 * Hello, it's me        ← Hello 在句首，不算嫌疑
 * I'm in Californication ← Californication 不是句首且大寫 → 嫌疑
 * ```
 *
 * 同一首詩裡只要**有一次**符合嫌疑，整個字就排除（寧可少挖，也不挖人名）。
 */
internal class SongCasing(lines: List<LyricLine>) {

    /** 正規化原形 → 是否曾在「非句首」的位置大寫出現。 */
    private val suspicious = HashSet<String>()

    init {
        lines.forEach { line ->
            TextNormalizer.TOKEN.findAll(line.text).forEachIndexed { order, match ->
                val token = match.value
                val isCapitalized = token.firstOrNull()?.isUpperCase() == true
                if (!isCapitalized) return@forEachIndexed
                if (order == 0) return@forEachIndexed
                suspicious += TextNormalizer.normalize(token)
            }
        }
    }

    /** 這個（正規化的）字是否為專有名詞嫌疑。 */
    fun isSuspicious(normalizedToken: String): Boolean = normalizedToken in suspicious
}
