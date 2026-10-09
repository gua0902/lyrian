package com.antigravity.lyrian.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AudioTrackMatcher] 的單元測試。
 *
 * ## 這裡守著整個音源接法最大的風險
 *
 * 歌詞時間戳來自 **LRCLIB**，音訊來自 **YouTube Music**——不同來源。
 * 同一首歌在 YouTube 上有錄音室版、現場版、MV 版、remaster 版，長度各異。
 * 挑錯版本 → **整首歌詞錯位**，而使用者只會覺得「App 壞了」。
 *
 * 所以最重要的性質是：**時長差太多時必須回報「不確信」**，
 * 而不是硬選一個看起來像的。
 */
class AudioTrackMatcherTest {

    private fun song(
        videoId: String,
        title: String,
        durationSeconds: Int? = 296,
        artist: String = "Adele",
        album: String? = null,
    ) = YouTubeSong(
        videoId = videoId,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = durationSeconds,
        thumbnailUrl = null,
    )

    // ---------- 時長差距的計算 ----------

    @Test
    fun `時長差距以毫秒計算`() {
        val candidate = song("a", "Hello", durationSeconds = 296)
        // 期望 296000ms，實際 296s → 差距 0
        assertEquals(0L, AudioTrackMatcher.durationDelta(candidate, 296_000L))
    }

    @Test
    fun `時長差距可為正負`() {
        val candidate = song("a", "Hello", durationSeconds = 300)
        // 實際 300s，期望 296s → 差 4 秒
        assertEquals(4_000L, AudioTrackMatcher.durationDelta(candidate, 296_000L))
    }

    @Test
    fun `任一邊時長未知時回傳 null`() {
        assertNull(AudioTrackMatcher.durationDelta(song("a", "Hello", null), 296_000L))
        assertNull(AudioTrackMatcher.durationDelta(song("a", "Hello", 296), null))
        assertNull(AudioTrackMatcher.durationDelta(song("a", "Hello", 0), 296_000L))
        assertNull(AudioTrackMatcher.durationDelta(song("a", "Hello", 296), 0L))
    }

    // ---------- 挑選 ----------

    @Test
    fun `空清單回傳 null`() {
        assertNull(AudioTrackMatcher.pick(emptyList(), 296_000L))
    }

    @Test
    fun `挑出時長最接近的版本`() {
        val candidates = listOf(
            song("live", "Hello (Live)", durationSeconds = 420),
            song("studio", "Hello", durationSeconds = 296),
            song("mv", "Hello (Official Video)", durationSeconds = 380),
        )

        val match = AudioTrackMatcher.pick(candidates, expectedDurationMs = 296_000L)

        assertEquals("studio", match?.candidate?.videoId)
        assertTrue(match!!.isConfident)
    }

    @Test
    fun `時長差太多的版本不算確信`() {
        val candidates = listOf(
            song("live", "Hello (Live at the Royal Albert Hall)", durationSeconds = 420),
        )

        val match = AudioTrackMatcher.pick(candidates, expectedDurationMs = 296_000L)

        // 還是會回傳一個候選，但明確標記為「不確信」——
        // UI 據此決定不要硬播，否則歌詞會錯位
        assertEquals("live", match?.candidate?.videoId)
        assertFalse(match!!.isConfident)
    }

    @Test
    fun `容忍度內視為確信`() {
        // 差 2 秒，在預設 3 秒容忍度內
        val candidates = listOf(song("a", "Hello", durationSeconds = 298))
        val match = AudioTrackMatcher.pick(candidates, expectedDurationMs = 296_000L)

        assertTrue(match!!.isConfident)
        assertEquals(2_000L, match.durationDeltaMs)
    }

    @Test
    fun `容忍度邊界剛好等於視為確信`() {
        val candidates = listOf(song("a", "Hello", durationSeconds = 299))
        val match = AudioTrackMatcher.pick(
            candidates,
            expectedDurationMs = 296_000L,
            toleranceMs = 3_000L,
        )

        assertTrue(match!!.isConfident)
        assertEquals(3_000L, match.durationDeltaMs)
    }

    @Test
    fun `超過容忍度就不算確信`() {
        val candidates = listOf(song("a", "Hello", durationSeconds = 300))
        val match = AudioTrackMatcher.pick(
            candidates,
            expectedDurationMs = 296_000L,
            toleranceMs = 3_000L,
        )

        assertFalse(match!!.isConfident)
    }

    @Test
    fun `時長未知時全部視為不確信`() {
        val candidates = listOf(song("a", "Hello", durationSeconds = null))
        val match = AudioTrackMatcher.pick(candidates, expectedDurationMs = 296_000L)

        assertFalse(match!!.isConfident)
    }

    // ---------- 排除現場／翻唱 ----------

    @Test
    fun `時長相同時偏好非現場版本`() {
        // 刻意讓兩者時長一樣，逼出「標題」這個次要判準
        val candidates = listOf(
            song("live", "Hello (Live)", durationSeconds = 296),
            song("studio", "Hello", durationSeconds = 296),
        )

        val match = AudioTrackMatcher.pick(candidates, expectedDurationMs = 296_000L)

        assertEquals("studio", match?.candidate?.videoId)
    }

    @Test
    fun `辨識各種現場與翻唱關鍵字`() {
        listOf(
            "Hello (Live)",
            "Hello - Live at Wembley",
            "Hello (Acoustic)",
            "Hello (Cover by Someone)",
            "Hello (Remix)",
            "Hello (Instrumental)",
            "Hello (Karaoke Version)",
            "Hello (Demo)",
            "Hello (Unplugged)",
            "Hello 現場版",
        ).forEach { title ->
            assertEquals(
                "應判定為劣質版本：$title",
                1,
                AudioTrackMatcher.penaltyFor(song("x", title)),
            )
        }
    }

    @Test
    fun `乾淨的標題不扣分`() {
        assertEquals(0, AudioTrackMatcher.penaltyFor(song("x", "Hello")))
        assertEquals(0, AudioTrackMatcher.penaltyFor(song("x", "Hello (Remastered 2015)")))
    }

    @Test
    fun `專輯名稱也納入關鍵字判斷`() {
        val candidate = song("x", "Hello", album = "Live at Wembley")
        assertEquals(1, AudioTrackMatcher.penaltyFor(candidate))
    }

    // ---------- 標題正規化 ----------

    @Test
    fun `括號內容會被移除`() {
        assertEquals("hello", AudioTrackMatcher.normalizeTitle("Hello (Remastered 2015)"))
        assertEquals("hello", AudioTrackMatcher.normalizeTitle("Hello [Official Audio]"))
    }

    @Test
    fun `常見的影片雜訊字會被移除`() {
        assertEquals("hello", AudioTrackMatcher.normalizeTitle("Hello Official Video"))
        assertEquals("hello", AudioTrackMatcher.normalizeTitle("Hello Lyrics HD"))
    }

    @Test
    fun `大小寫與多餘空白會被正規化`() {
        assertEquals("hello world", AudioTrackMatcher.normalizeTitle("  HELLO    World  "))
    }

    @Test
    fun `標點符號會被移除`() {
        assertEquals("hello world", AudioTrackMatcher.normalizeTitle("Hello, World!"))
    }

    @Test
    fun `保留中日韓文字`() {
        assertEquals("你好世界", AudioTrackMatcher.normalizeTitle("你好世界"))
    }

    // ---------- 實際情境 ----------

    @Test
    fun `模擬 Adele Hello 的搜尋結果`() {
        // 真實 YouTube Music 搜尋會混入這些版本
        val candidates = listOf(
            song("mv", "Hello (Official Music Video)", durationSeconds = 367),
            song("live", "Hello (Live at the Royal Albert Hall)", durationSeconds = 402),
            song("studio", "Hello", durationSeconds = 295, album = "25"),
            song("cover", "Hello (Cover)", durationSeconds = 296),
        )

        val match = AudioTrackMatcher.pick(candidates, expectedDurationMs = 295_502L)

        // 錄音室版與翻唱版時長幾乎一樣，靠標題關鍵字決勝
        assertEquals("studio", match?.candidate?.videoId)
        assertTrue(match!!.isConfident)
    }
}
