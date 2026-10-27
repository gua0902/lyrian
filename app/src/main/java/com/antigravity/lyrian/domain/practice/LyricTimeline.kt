package com.antigravity.lyrian.domain.practice

import com.antigravity.lyrian.domain.model.LyricLine

/**
 * 「某個時間點正在唱哪一行」。
 *
 * ## 為什麼要抽出來
 *
 * 這段判斷每 50ms 跑一次（播放位置輪詢）。舊版用
 * `lines.lastOrNull { it.startMs <= positionMs }` —— 一次完整的線性掃描，
 * 200 句的歌詞就是每秒 4000 次比較，而且它跑在主執行緒的 ViewModel 協程裡。
 *
 * 歌詞的時間軸**依建構方式就是單調遞增**（LRC 的時間戳由檔案順序遞增；
 * 從 Room 回復時 `ORDER BY lineIndex ASC`），所以可以二分搜尋。
 *
 * ## 非單調的資料不會崩潰
 *
 * 如果真的遇到亂序的歌詞檔，二分搜尋只會回一個「某個開始時間 <= 位置」的索引，
 * 不會回傳越界的值——行為退化但安全。
 */
object LyricTimeline {

    /**
     * @return 開始時間 <= [positionMs] 的最後一行的 `index`；位置在第一行之前或
     *   整份歌詞都沒有時間戳時回傳 null
     */
    fun lineAt(lines: List<LyricLine>, positionMs: Long): Int? {
        if (lines.isEmpty()) return null
        // 純文字歌詞（全部 startMs = 0）沒有時間軸，UI 不該跟著跑
        if (lines.all { it.startMs <= 0L }) return null

        var low = 0
        var high = lines.lastIndex
        var found = -1

        while (low <= high) {
            val mid = (low + high).ushr(1)
            if (lines[mid].startMs <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return lines.getOrNull(found)?.index
    }
}
