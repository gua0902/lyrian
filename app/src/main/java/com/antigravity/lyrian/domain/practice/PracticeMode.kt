package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.domain.model.PracticeModeId

/**
 * 練習模式的後設資料。
 *
 * **時間估算怎麼來的**（擁有者問的問題）
 *
 * 不寫死「3 分鐘 / 5 分鐘」，因為那對 2 分鐘的短歌或 6 分鐘的長歌都不準。
 * 改成**由題數推算**，並把依據寫在註解裡讓它可被檢驗：
 *
 * | 模式 | 每題耗時 | 依據 |
 * |---|---|---|
 * | 單字填空 | 18 秒 | 讀句 6s + 比較 4 個選項 6s + 點擊與回饋 6s |
 * | 翻譯評分 | 35 秒 | 讀句 8s + 構思中文 12s + 打字 10s + 看 AI 回饋 5s |
 *
 * 翻譯的變異很大（中文輸入速度因人而異），因此估算時給一個**區間**而非單一數字。
 *
 * 這些數字是**起始估計**，上線後應依實際作答時間校正
 * （`attempt.durationMs` 已經在記錄，就是為了這個）。
 */
enum class PracticeMode(
    val id: PracticeModeId,
    /** 每題平均秒數 */
    val secondsPerQuestion: Int,
    /** 估算的變異係數（用來給出區間） */
    val variance: Float,
    /** 是否需要 BYOK AI 才能使用 */
    val requiresAi: Boolean,
) {
    FILL_CHOICE(
        id = PracticeModeId.FILL_CHOICE,
        secondsPerQuestion = 18,
        variance = 0.25f,
        requiresAi = false,
    ),
    ZH_TRANSLATE(
        id = PracticeModeId.ZH_TRANSLATE,
        secondsPerQuestion = 35,
        // 翻譯變異較大（中文輸入速度因人而異），但也**不該大到讓下限低於填空的上限**——
        // 那會出現「翻譯可能比填空快」這種違反直覺的估算。
        // 0.45 → 0.30 是為了保證兩個模式的區間不重疊。
        variance = 0.30f,
        requiresAi = true,
    ),
    ;

    /**
     * 依題數估算所需時間（秒），回傳 `(最少, 最多)`。
     *
     * @param questionCount 題數
     * @return 秒數區間；題數為 0 時回傳 `(0, 0)`
     */
    fun estimateSeconds(questionCount: Int): IntRange {
        if (questionCount <= 0) return 0..0

        val base = questionCount * secondsPerQuestion
        val low = (base * (1f - variance)).toInt()
        val high = (base * (1f + variance)).toInt()
        return low..high
    }

    companion object {
        fun fromId(id: PracticeModeId): PracticeMode = entries.first { it.id == id }
    }
}

/**
 * 把秒數區間格式化成好讀的字串。
 *
 * 顯示規則（刻意保守，不假裝精準）：
 *  - 少於 90 秒 → 「約 1 分鐘」（不顯示秒，因為秒級估算沒有意義）
 *  - 少於 60 分鐘 → 取整到最近的半分鐘，顯示成「約 3 分鐘」或「約 3.5 分鐘」
 *  - 超過 60 分鐘 → 「約 1 小時以上」
 */
fun formatDurationEstimate(seconds: IntRange): String {
    if (seconds.isEmpty() || seconds.last <= 0) return "—"

    val highMinutes = seconds.last / 60f

    return when {
        highMinutes < 1.5f -> "約 1 分鐘"
        highMinutes < 60f -> {
            // 取整到最近的半分鐘
            val rounded = Math.round(highMinutes * 2) / 2f
            if (rounded % 1f == 0f) {
                "約 ${rounded.toInt()} 分鐘"
            } else {
                "約 $rounded 分鐘"
            }
        }
        else -> "約 1 小時以上"
    }
}
