package com.antigravity.lyrian.domain.practice

/**
 * 歌詞／口語專用的停用詞（**查詞典之前**就剃除）。
 *
 * ## 為什麼需要這一層
 *
 * 本地詞庫（ECDICT）把 `AHH` 當成「芳基烴羥化酶」這種醫學縮寫，
 * 於是歌詞裡的 `ahh` 會被當成 B1+ 專有名詞候選。問題不是詞庫錯，
 * 而是**歌詞裡的 ahh 根本不是那個字**。
 *
 * 因此這一層放在最前面：先認定「這是發語詞／感嘆詞／無意義音節」，
 * 連查都不查。這比事後靠 POS 或等級過濾可靠，因為它不受詞庫內容影響。
 *
 * ## 三種判定
 *
 * 1. **明列清單**：`ah`、`oh`、`yeah`、`la`、`mmm`…（歌詞最常見的填充音）
 * 2. **單音節重複**：`aaa`、`oooh`、`ahh`（同一字母連續 2 次以上）
 * 3. **長度 ≤ 3 的無母音字串**：`shh`、`mmm`、`pff`
 *
 * ## 不該放進來的字
 *
 * `love`、`baby`、`time` 這些是**真單字**，它們的過濾交給 CEFR 門檻，
 * 不要放進停用詞表——那只會誤殺。
 */
object LyricStopWords {

    /** 歌詞／口語填充音（小寫、正規化後比對）。 */
    private val FILLERS = setOf(
        // 感嘆／發語
        "ah", "ahh", "aah", "ahhh", "oh", "ohh", "ooh", "oooh", "uh", "uhh", "uhhh",
        "um", "umm", "ummm", "hmm", "hmmm", "hm", "mm", "mmm", "mhm", "eh", "ey",
        "yeah", "yea", "yeh", "yep", "yup", "nah", "nope", "huh", "huh",
        "hey", "heyy", "ho", "whoa", "woah", "woo", "wooh", "wow", "oops", "shh",
        "ha", "haha", "hah", "hee", "hehe", "hihi", "lol",
        // 旋律音節
        "la", "lah", "lala", "na", "nah", "da", "dah", "doo", "doo", "dum", "dumdum",
        "tra", "twinkle", "bop", "bom", "boom", "tss", "tsk", "psst",
        // 常見語氣尾
        "ya", "yo", "aye", "ay", "oi", "oy", "aw", "aww", "awww",
    )

    /** 同一字母連續出現（`aaa`、`oooh`、`ahh` 的 `hh`）。 */
    private val REPEATED_LETTER = Regex("^([a-z])\\1+$")

    /** 沒有母音的三字母以下音節（`shh`、`pff`、`mmm`）。 */
    private val NO_VOWEL = Regex("^[^aeiouy]{1,4}$")

    /**
     * 這個（正規化後的）詞是不是填充音。
     */
    fun isFiller(word: String): Boolean {
        if (word.isEmpty()) return true
        if (word in FILLERS) return true
        // `ahh` = a + hh：整串是同一個字母重複
        if (REPEATED_LETTER.matches(word)) return true
        // 任何位置的「同字母連三」也視為音節（`oooh`、`hmmm`、`ahhh`）
        if (Regex("([a-z])\\1{2,}").containsMatchIn(word)) return true
        // 沒有母音又很短 → 不是英文詞
        if (word.length <= 3 && NO_VOWEL.matches(word)) return true
        return false
    }
}
