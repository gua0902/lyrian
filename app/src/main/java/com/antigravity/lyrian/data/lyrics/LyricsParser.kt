package com.antigravity.lyrian.data.lyrics

import com.antigravity.lyrian.domain.model.LyricLine

/**
 * 歌詞解析。
 *
 * 依 ARCHITECTURE.md §4.1，來源優先序為：
 *  1. [parseLyricsFile]  —— LRCLIB 的結構化格式，**同時有 start_ms 與 end_ms**
 *  2. [parseSyncedLyrics] —— 標準 LRC，只有開始時間
 *  3. [parsePlainLyrics]  —— 純文字，無時間軸，以固定間隔推算
 *
 * 之所以把 `lyricsfile` 放在最前，是因為 `end_ms` 讓我們知道「這句唱完了沒」，
 * 這對歌詞高亮推進與題目節奏是必要的。
 *
 * 本物件**不依賴 Android 框架**，因此可完整單元測試。
 */
object LyricsParser {

    /** 純文字歌詞的每行間隔（毫秒）。 */
    const val PLAIN_LYRICS_INTERVAL_MS = 4_000L

    /**
     * 解析 LRC 的 `[mm:ss.xx]` 為毫秒。
     *
     * 支援 `mm:ss`、`mm:ss.xx`、`mm:ss.xxx`，以及 `mm:ss:xx` 這種以冒號分隔小數的變體。
     *
     * @return 解析失敗時回傳 null
     */
    fun parseTimestamp(value: String): Long? {
        val match = TIMESTAMP_REGEX.matchEntire(value) ?: return null
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null

        // 小數位數不固定：.5 = 500ms、.50 = 500ms、.500 = 500ms
        val fractionRaw = match.groupValues[3]
        val fractionMs = if (fractionRaw.isEmpty()) {
            0L
        } else {
            // 補齊或截斷到三位，避免浮點誤差
            val padded = fractionRaw.padEnd(3, '0').take(3)
            padded.toLongOrNull() ?: 0L
        }

        return minutes * 60_000L + seconds * 1_000L + fractionMs
    }

    /**
     * 解析 LRCLIB 的 `lyricsfile`（YAML-like）。
     *
     * 目標形狀：
     * ```yaml
     * lines:
     * - text: Hello, it's me
     *   start_ms: 6220
     *   end_ms: 11840
     * ```
     *
     * 刻意不引入 YAML 函式庫：格式固定，且未知的鍵（例如逐字同步的 `words:`）會被忽略。
     * 巢狀的 `- text:`（縮排更深）會被跳過，不會被誤判成歌詞行。
     */
    fun parseLyricsFile(source: String): List<LyricLine> {
        if (source.isBlank()) return emptyList()

        val collected = mutableListOf<Triple<String, Long, Long?>>()
        var text: String? = null
        var startMs = 0L
        var endMs: Long? = null

        /**
         * 頂層清單項目的縮排寬度。第一次遇到 `- text:` 時鎖定，
         * 之後更深縮排的 `- text:` 視為巢狀項目（例如逐字同步的 `words:`）並忽略。
         */
        var itemIndent: Int? = null

        fun flush() {
            val t = text
            if (t != null) collected += Triple(t, startMs, endMs)
            text = null
            startMs = 0L
            endMs = null
        }

        for (raw in source.lineSequence()) {
            val textMatch = TEXT_ITEM_REGEX.find(raw)
            if (textMatch != null) {
                val indent = textMatch.groupValues[1].length
                if (itemIndent == null) itemIndent = indent
                if (indent != itemIndent) continue

                flush()
                text = unquoteYamlScalar(textMatch.groupValues[2])
                continue
            }

            if (text == null) continue

            val startMatch = START_MS_REGEX.find(raw)
            if (startMatch != null) {
                startMs = readIntScalar(startMatch.groupValues[1]) ?: 0L
                continue
            }

            val endMatch = END_MS_REGEX.find(raw)
            if (endMatch != null) {
                endMs = readIntScalar(endMatch.groupValues[1])
            }
        }
        flush()

        return finalize(collected)
    }

    /**
     * 解析標準 LRC（`syncedLyrics`）。時間軸只有開始時間，因此 `endMs` 為 null。
     *
     * 一行可帶多個時間標籤（`[00:01.00][00:05.00] 歌詞`），每個標籤各產生一行。
     */
    fun parseSyncedLyrics(source: String): List<LyricLine> {
        if (source.isBlank()) return emptyList()

        val collected = mutableListOf<Triple<String, Long, Long?>>()

        for (raw in source.lineSequence()) {
            val tags = mutableListOf<Long>()
            var lastTagEnd = 0

            for (match in TIMESTAMP_TAG_REGEX.findAll(raw)) {
                parseTimestamp(match.groupValues[1])?.let { tags += it }
                lastTagEnd = match.range.last + 1
            }

            if (tags.isEmpty()) continue

            val text = raw.substring(lastTagEnd).trim()
            if (text.isEmpty()) continue

            tags.forEach { collected += Triple(text, it, null) }
        }

        return finalize(collected)
    }

    /**
     * 解析純文字歌詞（`plainLyrics`）。沒有時間軸，以固定間隔推算，
     * 僅為了讓畫面能運作；實際節奏不會準確。
     */
    fun parsePlainLyrics(
        source: String,
        intervalMs: Long = PLAIN_LYRICS_INTERVAL_MS,
    ): List<LyricLine> {
        if (source.isBlank()) return emptyList()

        val collected = source.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { position, line ->
                Triple(line, position * intervalMs, (position + 1) * intervalMs)
            }
            .toList()

        return finalize(collected)
    }

    /** 依優先序嘗試三種來源，回傳第一個解析出內容的結果。 */
    fun parse(
        lyricsFile: String? = null,
        syncedLyrics: String? = null,
        plainLyrics: String? = null,
    ): List<LyricLine> {
        parseLyricsFile(lyricsFile.orEmpty()).takeIf { it.isNotEmpty() }?.let { return it }
        parseSyncedLyrics(syncedLyrics.orEmpty()).takeIf { it.isNotEmpty() }?.let { return it }
        parsePlainLyrics(plainLyrics.orEmpty()).takeIf { it.isNotEmpty() }?.let { return it }
        return emptyList()
    }

    // ---- 內部工具 ----

    /** 濾掉空行、依時間排序，並重新連續編號。 */
    private fun finalize(raw: List<Triple<String, Long, Long?>>): List<LyricLine> =
        raw.asSequence()
            .filter { it.first.isNotBlank() }
            .sortedBy { it.second }
            .mapIndexed { index, (text, startMs, endMs) ->
                LyricLine(index = index, text = text, startMs = startMs, endMs = endMs)
            }
            .toList()

    /** 去掉 YAML 純量外層的引號，並還原基本的跳脫。 */
    private fun unquoteYamlScalar(raw: String): String {
        val value = raw.trim()
        if (value.length < 2) return value

        return when {
            value.startsWith("'") && value.endsWith("'") ->
                value.substring(1, value.length - 1).replace("''", "'")
            value.startsWith("\"") && value.endsWith("\"") ->
                value.substring(1, value.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
            else -> value
        }
    }

    /** 從 `key: 123` 這種字串取出整數。 */
    private fun readIntScalar(raw: String): Long? =
        INT_SCALAR_REGEX.find(raw.trim())?.value?.toLongOrNull()

    private val TIMESTAMP_REGEX = Regex("""^(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?$""")
    private val TIMESTAMP_TAG_REGEX = Regex("""\[(\d{1,3}:\d{2}(?:[.:]\d{1,3})?)]""")
    private val TEXT_ITEM_REGEX = Regex("""^([ \t]*)-[ \t]*text:[ \t]*(.*)$""")
    private val START_MS_REGEX = Regex("""^[ \t]*start_ms:[ \t]*(.*)$""")
    private val END_MS_REGEX = Regex("""^[ \t]*end_ms:[ \t]*(.*)$""")
    private val INT_SCALAR_REGEX = Regex("""^-?\d+""")
}
