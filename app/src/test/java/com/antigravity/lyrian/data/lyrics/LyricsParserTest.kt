package com.antigravity.lyrian.data.lyrics

import com.antigravity.lyrian.domain.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [LyricsParser] 的單元測試。
 *
 * 測試資料取自 LRCLIB `/api/get` 的實際回傳形狀（2026-10 實測）。
 */
class LyricsParserTest {

    /** 取自 LRCLIB 的真實 `lyricsfile` 形狀。 */
    private val lyricsFileSample = """
        version: '1.0'
        metadata:
          title: Hello
          artist: Adele
          album: '25'
          duration_ms: 295000
          instrumental: false
        lines:
        - text: Hello, it's me
          start_ms: 6220
          end_ms: 11840
        - text: I was wondering if after all these years you'd like to meet
          start_ms: 11840
          end_ms: 17960
        - text: ''
          start_ms: 17960
          end_ms: 18000
        - text: To go over everything
          start_ms: 18000
          end_ms: 23470
    """.trimIndent()

    // ---------- parseTimestamp ----------

    @Test
    fun `parseTimestamp 解析 mm ss`() {
        assertEquals(83_000L, LyricsParser.parseTimestamp("01:23"))
    }

    @Test
    fun `parseTimestamp 解析兩位小數`() {
        assertEquals(6_220L, LyricsParser.parseTimestamp("00:06.22"))
    }

    @Test
    fun `parseTimestamp 解析三位小數`() {
        assertEquals(62_345L, LyricsParser.parseTimestamp("01:02.345"))
    }

    @Test
    fun `parseTimestamp 支援冒號分隔小數`() {
        assertEquals(6_220L, LyricsParser.parseTimestamp("00:06:22"))
    }

    @Test
    fun `parseTimestamp 支援超過 99 的分鐘`() {
        assertEquals(123 * 60_000L + 45_000L, LyricsParser.parseTimestamp("123:45"))
    }

    @Test
    fun `parseTimestamp 對無效字串回傳 null`() {
        assertNull(LyricsParser.parseTimestamp("ar:Adele"))
        assertNull(LyricsParser.parseTimestamp(""))
        assertNull(LyricsParser.parseTimestamp("1:2"))
    }

    // ---------- parseLyricsFile ----------

    @Test
    fun `parseLyricsFile 解析 text start_ms end_ms`() {
        val lines = LyricsParser.parseLyricsFile(lyricsFileSample)

        assertEquals(3, lines.size)
        assertEquals(
            LyricLine(0, "Hello, it's me", 6_220L, 11_840L),
            lines[0],
        )
        assertEquals(11_840L, lines[1].startMs)
        assertEquals(17_960L, lines[1].endMs)
        assertEquals(2, lines[2].index)
    }

    @Test
    fun `parseLyricsFile 不會把 metadata 當成歌詞`() {
        val joined = LyricsParser.parseLyricsFile(lyricsFileSample).joinToString(" ") { it.text }
        assertEquals(false, joined.contains("Adele"))
        assertEquals(false, joined.contains("295000"))
    }

    @Test
    fun `parseLyricsFile 濾掉空行並重新編號`() {
        val lines = LyricsParser.parseLyricsFile(lyricsFileSample)
        assertEquals(listOf(0, 1, 2), lines.map { it.index })
        assertEquals(true, lines.all { it.text.isNotEmpty() })
    }

    @Test
    fun `parseLyricsFile 依時間排序`() {
        val source = """
            lines:
            - text: later
              start_ms: 900
              end_ms: 1000
            - text: earlier
              start_ms: 100
              end_ms: 200
        """.trimIndent()

        assertEquals(listOf("earlier", "later"), LyricsParser.parseLyricsFile(source).map { it.text })
    }

    @Test
    fun `parseLyricsFile 去掉單引號並還原跳脫`() {
        val source = """
            lines:
            - text: 'Hello, it''s me'
              start_ms: 0
              end_ms: 10
        """.trimIndent()

        assertEquals("Hello, it's me", LyricsParser.parseLyricsFile(source)[0].text)
    }

    @Test
    fun `parseLyricsFile 去掉雙引號`() {
        val source = """
            lines:
            - text: "double quoted"
              start_ms: 0
              end_ms: 10
        """.trimIndent()

        assertEquals("double quoted", LyricsParser.parseLyricsFile(source)[0].text)
    }

    @Test
    fun `parseLyricsFile 忽略巢狀的逐字同步 words 陣列`() {
        val source = """
            lines:
            - text: keep me
              start_ms: 10
              end_ms: 20
              words:
              - text: keep
                start_ms: 10
                end_ms: 15
              - text: me
                start_ms: 15
                end_ms: 20
        """.trimIndent()

        val lines = LyricsParser.parseLyricsFile(source)
        assertEquals(1, lines.size)
        assertEquals("keep me", lines[0].text)
    }

    @Test
    fun `parseLyricsFile 支援縮排兩格的清單樣式`() {
        val source = """
            lines:
              - text: indented
                start_ms: 5
                end_ms: 9
        """.trimIndent()

        val lines = LyricsParser.parseLyricsFile(source)
        assertEquals(1, lines.size)
        assertEquals("indented", lines[0].text)
    }

    @Test
    fun `parseLyricsFile 空輸入回傳空清單`() {
        assertEquals(emptyList<LyricLine>(), LyricsParser.parseLyricsFile(""))
        assertEquals(emptyList<LyricLine>(), LyricsParser.parseLyricsFile("   "))
    }

    @Test
    fun `parseLyricsFile 沒有 end_ms 時為 null`() {
        val source = """
            lines:
            - text: no end
              start_ms: 42
        """.trimIndent()

        assertNull(LyricsParser.parseLyricsFile(source)[0].endMs)
    }

    // ---------- parseSyncedLyrics ----------

    @Test
    fun `parseSyncedLyrics 解析標準 LRC 且 endMs 為 null`() {
        val source = """
            [00:06.22] Hello, it's me
            [00:11.84] I was wondering if after all these years you'd like to meet
        """.trimIndent()

        val lines = LyricsParser.parseSyncedLyrics(source)
        assertEquals(2, lines.size)
        assertEquals(LyricLine(0, "Hello, it's me", 6_220L, null), lines[0])
    }

    @Test
    fun `parseSyncedLyrics 忽略 LRC 的 metadata 標籤`() {
        val source = """
            [ar:Adele]
            [ti:Hello]
            [00:01.00] real lyric
        """.trimIndent()

        val lines = LyricsParser.parseSyncedLyrics(source)
        assertEquals(1, lines.size)
        assertEquals("real lyric", lines[0].text)
    }

    @Test
    fun `parseSyncedLyrics 一行多個時間標籤會產生多行`() {
        val lines = LyricsParser.parseSyncedLyrics("[00:01.00][00:05.00] repeated")
        assertEquals(2, lines.size)
        assertEquals(listOf(1_000L, 5_000L), lines.map { it.startMs })
        assertEquals(true, lines.all { it.text == "repeated" })
    }

    @Test
    fun `parseSyncedLyrics 忽略沒有文字的純時間行`() {
        assertEquals(emptyList<LyricLine>(), LyricsParser.parseSyncedLyrics("[00:01.00]"))
    }

    // ---------- parsePlainLyrics ----------

    @Test
    fun `parsePlainLyrics 以固定間隔推算時間軸`() {
        val lines = LyricsParser.parsePlainLyrics("first\n\n  second  \nthird")

        assertEquals(3, lines.size)
        assertEquals(0L, lines[0].startMs)
        assertEquals(LyricsParser.PLAIN_LYRICS_INTERVAL_MS, lines[1].startMs)
        assertEquals(LyricsParser.PLAIN_LYRICS_INTERVAL_MS * 2, lines[2].startMs)
        assertEquals("second", lines[1].text)
        assertEquals(LyricsParser.PLAIN_LYRICS_INTERVAL_MS, lines[0].endMs)
    }

    @Test
    fun `parsePlainLyrics 可自訂間隔`() {
        assertEquals(1_000L, LyricsParser.parsePlainLyrics("a\nb", 1_000L)[1].startMs)
    }

    // ---------- parse（優先序）----------

    @Test
    fun `parse 優先使用 lyricsfile`() {
        val lines = LyricsParser.parse(
            lyricsFile = lyricsFileSample,
            syncedLyrics = "[00:01.00] from synced",
            plainLyrics = "from plain",
        )

        assertEquals("Hello, it's me", lines[0].text)
        assertEquals(true, lines[0].endMs != null)
    }

    @Test
    fun `parse 在 lyricsfile 無法解析時退回 syncedLyrics`() {
        val lines = LyricsParser.parse(
            lyricsFile = "not a valid lyrics file",
            syncedLyrics = "[00:01.00] from synced",
            plainLyrics = "from plain",
        )

        assertEquals("from synced", lines[0].text)
        assertNull(lines[0].endMs)
    }

    @Test
    fun `parse 在前兩者都沒有時退回 plainLyrics`() {
        assertEquals("from plain", LyricsParser.parse(plainLyrics = "from plain")[0].text)
    }

    @Test
    fun `parse 全部為空時回傳空清單`() {
        assertEquals(emptyList<LyricLine>(), LyricsParser.parse())
        assertEquals(
            emptyList<LyricLine>(),
            LyricsParser.parse(lyricsFile = null, syncedLyrics = null, plainLyrics = null),
        )
    }
}
