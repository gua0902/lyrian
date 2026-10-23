package com.antigravity.lyrian.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本排序的測試。
 *
 * 這裡守的是擁有者的驗收條件：**播放的是官方錄音室版本**。
 * 每一條測試都對應一個真實會遇到的 YouTube 搜尋結果組合。
 */
class YouTubeCandidateScorerTest {

    private fun video(
        id: String,
        title: String,
        channel: String,
        durationMs: Long?,
        views: Long = 0,
    ) = YouTubeVideo(videoId = id, title = title, channel = channel, durationMs = durationMs, viewCount = views)

    private fun rank(vararg candidates: YouTubeVideo, expectedDurationMs: Long? = 295_000L) =
        YouTubeCandidateScorer.rank(
            candidates = candidates.toList(),
            expectedTitle = "Hello",
            expectedArtist = "Adele",
            expectedDurationMs = expectedDurationMs,
        )

    @Test
    fun `Topic 頻道贏過同樣長度的普通頻道`() {
        val topic = video("topic000001", "Adele - Hello", "Adele - Topic", 295_000)
        val random = video("random00001", "Adele - Hello", "Random Uploads", 295_000)

        assertEquals("topic000001", rank(random, topic).first().video.videoId)
    }

    @Test
    fun `現場版本即使長度一樣也會排到最後`() {
        val official = video("official001", "Adele - Hello (Official Audio)", "Adele - Topic", 295_000)
        val live = video("live0000001", "Adele - Hello (Live at the Royal Albert Hall)", "Adele", 295_000)

        val ranked = rank(live, official)
        assertEquals("official001", ranked.first().video.videoId)
        assertTrue("現場版應該被扣分", ranked.last().penaltyKeywords.isNotEmpty())
        assertFalse(ranked.last().isConfident)
    }

    @Test
    fun `時長接近的版本優先於時長差很多的`() {
        val exact = video("exact000001", "Adele - Hello (Official Audio)", "Adele - Topic", 295_000)
        val long = video("long0000001", "Adele - Hello (Official Audio)", "Adele - Topic", 320_000)

        val ranked = rank(long, exact)
        assertEquals("exact000001", ranked.first().video.videoId)
    }

    @Test
    fun `翻唱與變速版本被大幅扣分`() {
        val cover = video("cover000001", "Hello - Adele (Cover by Someone)", "Covers", 295_000)
        val slowed = video("slowed00001", "Hello (Slowed + Reverb)", "Edits", 295_000)
        val official = video("official002", "Adele - Hello", "Adele - Topic", 295_000)

        val ranked = rank(cover, slowed, official)
        assertEquals("official002", ranked.first().video.videoId)
        assertFalse(ranked[1].isConfident)
        assertFalse(ranked[2].isConfident)
    }

    @Test
    fun `時長未知時仍然可以排序，只是不算確信`() {
        val unknown = video("unknown0001", "Adele - Hello", "Adele - Topic", null)
        val scored = YouTubeCandidateScorer.score(unknown, "Hello", "Adele", 295_000)

        assertEquals(null, scored.durationDeltaMs)
        assertTrue("時長未知不該被當成「差 0 秒」", !scored.isConfident)
    }

    @Test
    fun `觀看數不會蓋過時長`() {
        val wrongLength = video("viral000001", "Adele - Hello (Official Audio)", "Adele - Topic", 360_000, 90_000_000)
        val rightLength = video("correct0001", "Adele - Hello (Official Audio)", "Adele - Topic", 295_000, 1_000)

        assertEquals("correct0001", rank(wrongLength, rightLength).first().video.videoId)
    }

    @Test
    fun `時長差距的計算`() {
        assertEquals(5_000L, YouTubeCandidateScorer.durationDelta(300_000, 295_000))
        assertEquals(null, YouTubeCandidateScorer.durationDelta(null, 295_000))
        assertEquals(null, YouTubeCandidateScorer.durationDelta(300_000, null))
        // 0 代表「沒有資料」，不是「長度 0」
        assertEquals(null, YouTubeCandidateScorer.durationDelta(0, 295_000))
    }

    @Test
    fun `標題正規化會去掉官方字樣與括號`() {
        assertEquals("hello", YouTubeCandidateScorer.normalizeTitle("Hello (Remastered 2015)"))
        assertEquals("hello", YouTubeCandidateScorer.normalizeTitle("Hello [Official Video]"))
        assertEquals("hello", YouTubeCandidateScorer.normalizeTitle("Hello - Official Audio HD"))
        assertTrue(YouTubeCandidateScorer.normalizeTitle("Adele - Hello").contains("hello"))
    }
}
