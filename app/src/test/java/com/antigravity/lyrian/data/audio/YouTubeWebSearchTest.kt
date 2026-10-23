package com.antigravity.lyrian.data.audio

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 解析 YouTube 搜尋頁的測試。
 *
 * 用手寫的 HTML 片段（不是抓下來的整頁），因為要驗證的是「形狀改變時還能找到影片」。
 * JSON 路徑刻意寫得跟真實頁面一樣深，並多包一層不相關的 renderer，確保搜尋是遞迴的。
 */
class YouTubeWebSearchTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun page(inner: String) = """
        <!DOCTYPE html><html><head><title>hello - YouTube</title></head>
        <body>
        <script nonce="abc">var ytInitialData = $inner;</script>
        <script>window.ytcfg = {};</script>
        </body></html>
    """.trimIndent()

    private fun videoRenderer(
        id: String,
        title: String,
        channel: String,
        length: String,
        views: String,
    ) = """
        {"videoRenderer":{
          "videoId":"$id",
          "title":{"runs":[{"text":"$title"}]},
          "ownerText":{"runs":[{"text":"$channel"}]},
          "lengthText":{"simpleText":"$length"},
          "viewCountText":{"simpleText":"$views"}
        }}
    """.trimIndent()

    private fun pageWith(vararg renderers: String) = page(
        """
        {"contents":{"twoColumnSearchResultsRenderer":{"primaryContents":{
          "sectionListRenderer":{"contents":[{"itemSectionRenderer":{"contents":[
            ${renderers.joinToString(",")}
          ]}}]}
        }}}}
        """.trimIndent(),
    )

    @Test
    fun `解析出影片的標題頻道長度與觀看數`() {
        val html = pageWith(
            videoRenderer("abc12345678", "Adele - Hello (Official Audio)", "Adele - Topic", "4:55", "1,234,567 views"),
            videoRenderer("def12345678", "Hello (Live at Home)", "SomeChannel", "5:01", "2,000 views"),
        )

        val videos = YouTubeWebSearch.parseSearchResults(html, json)

        assertEquals(2, videos.size)
        assertEquals("abc12345678", videos[0].videoId)
        assertEquals("Adele - Hello (Official Audio)", videos[0].title)
        assertEquals("Adele - Topic", videos[0].channel)
        assertEquals(295_000L, videos[0].durationMs)
        assertEquals(1_234_567L, videos[0].viewCount)
    }

    @Test
    fun `字串裡有括號也不會截斷 JSON`() {
        val html = pageWith(
            videoRenderer("zzz12345678", "Hello } { weird } title", "Adele - Topic", "4:55", "10 views"),
        )

        val videos = YouTubeWebSearch.parseSearchResults(html, json)
        assertEquals(1, videos.size)
        assertEquals("Hello } { weird } title", videos[0].title)
    }

    @Test
    fun `沒有 ytInitialData 時回傳空清單`() {
        assertEquals(emptyList<YouTubeVideo>(), YouTubeWebSearch.parseSearchResults("<html></html>", json))
    }

    @Test
    fun `重複的影片只留一筆`() {
        val renderer = videoRenderer("same1234567", "Hello", "Adele - Topic", "4:55", "10 views")
        val videos = YouTubeWebSearch.parseSearchResults(pageWith(renderer, renderer), json)
        assertEquals(1, videos.size)
    }

    @Test
    fun `長度解析`() {
        assertEquals(242_000L, YouTubeWebSearch.parseDurationMs("4:02"))
        assertEquals(3_723_000L, YouTubeWebSearch.parseDurationMs("1:02:03"))
        assertNull(YouTubeWebSearch.parseDurationMs("LIVE"))
        assertNull(YouTubeWebSearch.parseDurationMs(null))
    }

    @Test
    fun `觀看數解析`() {
        assertEquals(1_234_567L, YouTubeWebSearch.parseViewCount("1,234,567 views"))
        assertEquals(0L, YouTubeWebSearch.parseViewCount("No views"))
        assertEquals(0L, YouTubeWebSearch.parseViewCount(null))
    }
}
