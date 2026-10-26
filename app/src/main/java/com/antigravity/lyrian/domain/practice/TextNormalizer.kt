package com.antigravity.lyrian.domain.practice

import java.text.Normalizer
import java.util.Locale

/**
 * 文本正規化（選詞與答案比對的唯一標準）。
 *
 * ```
 * café   → cafe
 * naïve  → naive
 * Don't  → don't   （撇號保留：它是詞的一部分）
 * HELLO  → hello
 * ```
 *
 * ## 為什麼要 NFD
 *
 * `é` 有兩種表示法：單一碼位（U+00E9）或 `e` + 組合重音符（U+0065 U+0301）。
 * 歌詞檔兩種都可能出現，若不正規化，同一首詩會出現「é 查不到、e 查得到」
 * 這種不可能解釋的差異。NFD 之後把組合記號（`\p{M}`）全部移除，再轉小寫。
 */
object TextNormalizer {

    private val COMBINING_MARKS = Regex("\\p{M}+")

    /**
     * 切成「單字」的樣式。
     *
     * 吃**拉丁字母（含變音字母）**與詞內撇號（`don't` 是一個 token）；
     * 組合記號也算在內，因為正規化前後都要能完整抓到同一個 token。
     * 數字、標點、中日韓字元天然被排除。
     */
    val TOKEN = Regex("[\\p{IsLatin}\\p{M}][\\p{IsLatin}\\p{M}'’]*")

    /** 正規化：NFD → 去組合記號 → 小寫 → 統一直撇號。 */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return decomposed
            .replace(COMBINING_MARKS, "")
            .replace('’', '\'')
            .lowercase(Locale.ROOT)
    }
}
