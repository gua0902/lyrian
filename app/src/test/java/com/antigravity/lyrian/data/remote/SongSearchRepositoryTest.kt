package com.antigravity.lyrian.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SongSearchRepository] 的純函式測試。
 *
 * 這裡**不測網路**——只測轉換與去重邏輯。轉換邏輯用假的 iTunes 資料驅動，
 * 因此不需要 MockWebServer，測試跑得很快。
 */
class SongSearchRepositoryTest {

    /** 取出真實的轉換邏輯來測（繞過 Hilt 的建構子注入）。 */
    private val repository = SongSearchRepository(
        lrclibApi = object : LrclibApi {
            override suspend fun search(
                userAgent: String,
                artistName: String?,
                trackName: String?,
                q: String?,
            ): List<LrclibTrackDto> = emptyList()
        },
        itunesApi = object : ItunesApi {
            override suspend fun search(
                term: String,
                entity: String,
                limit: Int,
            ): ItunesSearchResponseDto = ItunesSearchResponseDto()
        },
        // context = null ⇒ 走 opencc4j 退路，不需 Android 框架
        chineseConverter = com.antigravity.lyrian.data.text.ChineseConverter(context = null),
    )

    /** 取自 LRCLIB `/api/search` 的實測形狀。 */
    private fun lrclibEntry(
        id: Long = 197097,
        trackName: String = "Hello",
        artistName: String = "Adele, Adele",
        duration: Double? = 296.0,
        lyricsFile: String? = "lines:\n- text: I keep a lantern burning in the hollow\n  start_ms: 0\n  end_ms: 1000\n",
    ) = LrclibTrackDto(
        id = id,
        trackName = trackName,
        artistName = artistName,
        albumName = "25",
        duration = duration,
        lyricsfile = lyricsFile,
    )

    private fun itunesTrack(
        trackName: String = "Hello",
        artistName: String = "Adele",
        genre: String? = "Pop",
        artwork: String? = "https://example.com/abc/100x100bb.jpg",
    ) = ItunesTrackDto(
        trackName = trackName,
        artistName = artistName,
        primaryGenreName = genre,
        artworkUrl100 = artwork,
        trackTimeMillis = 295_502,
    )

    // ---------- toSearchResult ----------

    @Test
    fun `把 LRCLIB 的秒數 duration 換成毫秒`() {
        val result = repository.toSearchResult(lrclibEntry(duration = 296.0), emptyList())
        assertEquals(296_000L, result.durationMs)
    }

    @Test
    fun `duration 為 null 時保持 null`() {
        val result = repository.toSearchResult(lrclibEntry(duration = null), emptyList())
        assertEquals(null, result.durationMs)
    }

    @Test
    fun `正規化歌手名稱`() {
        val result = repository.toSearchResult(lrclibEntry(artistName = "Adele, Adele"), emptyList())
        assertEquals("Adele", result.artist)
    }

    @Test
    fun `從 iTunes 配對補上放大後的封面與曲風`() {
        val result = repository.toSearchResult(lrclibEntry(), listOf(itunesTrack()))
        assertEquals("https://example.com/abc/600x600bb.jpg", result.coverUrl)
        assertEquals("流行", result.genre)
    }

    @Test
    fun `沒有 iTunes 配對時封面與曲風為 null 但其餘仍可用`() {
        val result = repository.toSearchResult(lrclibEntry(), emptyList())
        assertEquals(null, result.coverUrl)
        assertEquals(null, result.genre)
        assertEquals("Hello", result.title)
        assertTrue(result.blankCount > 0)
    }

    @Test
    fun `計算可挖空的行數（對應 UI 的 N 個填空）`() {
        val lyricsFile = buildString {
            append("lines:\n")
            append("- text: I keep a lantern burning in the hollow\n  start_ms: 0\n  end_ms: 1000\n")
            append("- text: a b c\n  start_ms: 1000\n  end_ms: 2000\n")
            append("- text: A brand-new day, a brand-new start\n  start_ms: 2000\n  end_ms: 3000\n")
        }

        val result = repository.toSearchResult(lrclibEntry(lyricsFile = lyricsFile), emptyList())
        assertEquals(2, result.blankCount)
    }

    @Test
    fun `沒有歌詞時 blankCount 為 0`() {
        val result = repository.toSearchResult(lrclibEntry(lyricsFile = null), emptyList())
        assertEquals(0, result.blankCount)
    }

    @Test
    fun `保留原始歌詞供選歌後直接使用`() {
        val result = repository.toSearchResult(lrclibEntry(), emptyList())
        assertTrue(result.lyricsFile!!.contains("I keep a lantern"))
    }

    // ---------- dedupe ----------

    private fun song(
        lrclibId: Long,
        title: String = "Hello",
        artist: String = "Adele",
    ) = SongSearchResult(
        lrclibId = lrclibId,
        title = title,
        artist = artist,
        blankCount = 5,
    )

    @Test
    fun `依標題加歌手去重（大小寫與標點不影響）`() {
        val deduped = repository.dedupe(
            listOf(
                song(1, title = "Hello"),
                song(2, title = "hello"),
                song(3, title = "Yellow", artist = "Coldplay"),
            ),
        )

        assertEquals(listOf(1L, 3L), deduped.map { it.lrclibId })
    }

    @Test
    fun `標題相同但歌手不同時保留兩筆`() {
        val deduped = repository.dedupe(
            listOf(
                song(1, title = "Hello", artist = "Adele"),
                song(2, title = "Hello", artist = "Lionel Richie"),
            ),
        )

        assertEquals(2, deduped.size)
    }

    @Test
    fun `忽略版本註記造成的重複`() {
        val deduped = repository.dedupe(
            listOf(
                song(1, title = "Hello"),
                song(2, title = "Hello (Remastered 2015)"),
            ),
        )

        assertEquals(1, deduped.size)
    }

    @Test
    fun `保留第一筆的資料`() {
        val deduped = repository.dedupe(
            listOf(
                song(1, title = "Hello"),
                song(2, title = "hello"),
            ),
        )

        assertEquals(1L, deduped[0].lrclibId)
    }
}
