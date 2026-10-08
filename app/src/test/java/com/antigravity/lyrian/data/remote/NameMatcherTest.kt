package com.antigravity.lyrian.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [NameMatcher] 的單元測試。
 *
 * 測試資料取自 LRCLIB 與 iTunes 的實際回傳形狀（2026-10 實測）。
 */
class NameMatcherTest {

    private fun track(
        trackName: String? = "Hello",
        artistName: String? = "Adele",
        genre: String? = null,
        artwork: String? = null,
    ) = ItunesTrackDto(
        trackName = trackName,
        artistName = artistName,
        primaryGenreName = genre,
        artworkUrl100 = artwork,
    )

    // ---------- normalizeArtist ----------

    @Test
    fun `去掉重複的歌手（LRCLIB 實測資料）`() {
        assertEquals("Adele", NameMatcher.normalizeArtist("Adele, Adele"))
        assertEquals("Adele", NameMatcher.normalizeArtist("Adele/Adele"))
    }

    @Test
    fun `保留不同的多位歌手`() {
        assertEquals("A, B", NameMatcher.normalizeArtist("A, B, A"))
    }

    @Test
    fun `忽略大小寫差異的重複`() {
        assertEquals("Adele", NameMatcher.normalizeArtist("Adele, adele"))
    }

    @Test
    fun `處理 feat 連接詞`() {
        assertEquals("A, B", NameMatcher.normalizeArtist("A feat. B"))
        assertEquals("A, B", NameMatcher.normalizeArtist("A ft. B"))
        assertEquals("A, B", NameMatcher.normalizeArtist("A with B"))
    }

    @Test
    fun `處理分號與 and 符號`() {
        assertEquals("A, B", NameMatcher.normalizeArtist("A; B"))
        assertEquals("A, B", NameMatcher.normalizeArtist("A & B"))
    }

    @Test
    fun `空字串安全`() {
        assertEquals("", NameMatcher.normalizeArtist(""))
        assertEquals("", NameMatcher.normalizeArtist("   "))
    }

    // ---------- normalizeForMatch ----------

    @Test
    fun `轉小寫並去標點`() {
        assertEquals("hello it s me", NameMatcher.normalizeForMatch("Hello, It's Me!"))
    }

    @Test
    fun `去掉括號版本註記`() {
        assertEquals("hello", NameMatcher.normalizeForMatch("Hello (Remastered 2015)"))
        assertEquals("yellow", NameMatcher.normalizeForMatch("Yellow [Live]"))
    }

    // ---------- score ----------

    @Test
    fun `標題與歌手完全相同時分數最高`() {
        assertEquals(4, NameMatcher.score("Hello", "Adele", track()))
    }

    @Test
    fun `大小寫與標點差異不影響`() {
        assertEquals(4, NameMatcher.score("hello", "adele", track(trackName = "Hello", artistName = "ADELE")))
    }

    @Test
    fun `標題相同但歌手只是包含關係時次之`() {
        val score = NameMatcher.score("Hello", "Adele", track(artistName = "Adele Tribute Band"))
        assertEquals(3, score)
    }

    @Test
    fun `標題不同時為 0`() {
        assertEquals(0, NameMatcher.score("Hello", "Adele", track(trackName = "Rolling in the Deep")))
    }

    @Test
    fun `標題與歌手都無關時為 0`() {
        assertEquals(0, NameMatcher.score("Hello", "Adele", track(trackName = "Yellow", artistName = "Coldplay")))
    }

    // ---------- pickBest ----------

    @Test
    fun `挑出分數最高的配對`() {
        val tracks = listOf(
            track(artistName = "Some Cover Band"),
            track(artistName = "Adele"),
        )
        assertEquals("Adele", NameMatcher.pickBest("Hello", "Adele", tracks)?.artistName)
    }

    @Test
    fun `沒有可用配對時回傳 null`() {
        assertNull(NameMatcher.pickBest("Hello", "Adele", emptyList()))
        assertNull(NameMatcher.pickBest("Hello", "Adele", listOf(track(trackName = "Yellow", artistName = "Coldplay"))))
    }

    @Test
    fun `能配上帶版本註記的標題`() {
        val tracks = listOf(track(trackName = "Hello (Remastered)"))
        assertEquals("Hello (Remastered)", NameMatcher.pickBest("Hello", "Adele", tracks)?.trackName)
    }

    // ---------- artworkUrlAtSize ----------

    @Test
    fun `把 100x100bb 換成 600x600bb`() {
        assertEquals(
            "https://example.com/abc/600x600bb.jpg",
            NameMatcher.artworkUrlAtSize("https://example.com/abc/100x100bb.jpg"),
        )
    }

    @Test
    fun `可指定尺寸`() {
        assertEquals(
            "https://example.com/200x200bb.jpg",
            NameMatcher.artworkUrlAtSize("https://example.com/100x100bb.jpg", 200),
        )
    }

    @Test
    fun `null 或無法辨識格式時安全處理`() {
        assertNull(NameMatcher.artworkUrlAtSize(null))
        assertNull(NameMatcher.artworkUrlAtSize(""))
        assertEquals(
            "https://example.com/cover.jpg",
            NameMatcher.artworkUrlAtSize("https://example.com/cover.jpg"),
        )
    }

    // ---------- translateGenre ----------

    @Test
    fun `常見曲風翻成繁中`() {
        assertEquals("流行", NameMatcher.translateGenre("Pop"))
        assertEquals("搖滾", NameMatcher.translateGenre("Rock"))
        assertEquals("嘻哈", NameMatcher.translateGenre("Hip-Hop/Rap"))
    }

    @Test
    fun `沒有對照表時原樣回傳`() {
        assertEquals("Some Obscure Genre", NameMatcher.translateGenre("Some Obscure Genre"))
    }

    @Test
    fun `null 回傳 null`() {
        assertNull(NameMatcher.translateGenre(null))
        assertNull(NameMatcher.translateGenre(""))
    }
}
