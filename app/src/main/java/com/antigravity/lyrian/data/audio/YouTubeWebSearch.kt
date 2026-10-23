package com.antigravity.lyrian.data.audio

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Request

/**
 * 一個 YouTube 搜尋結果。
 *
 * @property videoId 11 個字元的影片 id
 * @property title 影片標題
 * @property channel 頻道名（官方自動生成的頻道長這樣：`Adele - Topic`）
 * @property durationMs 長度；搜尋結果沒給時為 null
 * @property viewCount 觀看數；解析不到時為 0
 */
data class YouTubeVideo(
    val videoId: String,
    val title: String,
    val channel: String,
    val durationMs: Long?,
    val viewCount: Long,
)

/**
 * 用**公開搜尋頁**找 YouTube 影片，不呼叫任何需要金鑰的 API。
 *
 * ## 為什麼不是官方 Data API
 *
 * YouTube Data API 每次搜尋都要 `key`，而這個 App 的鐵律是「BYOK 只用在 AI」，
 * 不該為了搜尋塞一組開發者的金鑰進去（那會產生配額與帳務）。
 *
 * ## 為什麼不是 InnerTube / NewPipe
 *
 * 兩者都能拿到結果，但：
 *  - InnerTube 是**內部 API**，違反 YouTube 服務條款；而且 GPL-3.0 的程式庫
 *    會讓整個 App 必須以 GPL 釋出（本專案已移除）。
 *  - NewPipe Extractor 需要自己實作 `Downloader`、還要追簽章與 PoToken 變動，
 *    在沒有實機可驗證的情況下風險太高。
 *
 * 公開搜尋頁面本身不需要金鑰、不需要登入，回傳的 HTML 裡嵌著 `ytInitialData`
 * 這個 JSON，剛好包含我們需要的四項：標題、頻道、長度、觀看數。
 *
 * ⚠️ 這裡**不做任何串流抓取、不繞過廣告**：只讀搜尋結果的公開中繼資料，
 * 實際播放交給官方 IFrame Player（見 `YouTubePlayerController`）。
 */
@Singleton
class YouTubeWebSearch @Inject constructor(
    private val client: okhttp3.OkHttpClient,
    private val json: Json,
) {
    /** 搜尋並回傳原始候選（排序由 [YouTubeCandidateScorer] 負責）。 */
    suspend fun search(query: String, limit: Int = 12): List<YouTubeVideo> =
        withContext(Dispatchers.IO) {
            val url = "https://www.youtube.com/results?search_query=" +
                java.net.URLEncoder.encode(query, "UTF-8") + "&hl=en&gl=US"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DESKTOP_USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val html = runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        android.util.Log.w(TAG, "YouTube 搜尋回應 ${response.code}")
                        return@withContext emptyList()
                    }
                    response.body?.string().orEmpty()
                }
            }.getOrElse {
                android.util.Log.w(TAG, "YouTube 搜尋失敗", it)
                return@withContext emptyList()
            }

            parseSearchResults(html, json, limit).also { videos ->
                android.util.Log.d(
                    TAG,
                    "搜尋「$query」解析出 ${videos.size} 個候選" +
                        videos.take(3).joinToString(
                            prefix = "：",
                            separator = " | ",
                        ) { "\"${it.title}\" @${it.channel} ${it.durationMs?.div(1000)}s" },
                )
            }
        }

    companion object {
        private const val TAG = "LyrianYouTubeSearch"

        /**
         * 用桌機版 User-Agent。
         *
         * 行動版搜尋頁會回傳完全不同的版面（`ytInitialData` 結構不同），
         * 桌機版是最穩定的形狀。WebView 那一層另外自己帶自己的 UA。
         */
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        /**
         * 從搜尋頁的 HTML 取出影片清單。
         *
         * 刻意**不寫死 JSON 路徑**（`contents.twoColumnSearchResultsRenderer…`）：
         * YouTube 每隔幾個月就會調整一次結構。這裡改成「遞迴找出所有
         * `videoRenderer`」，只要影片 renderer 還在，路徑怎麼變都找得到。
         */
        fun parseSearchResults(
            html: String,
            json: Json,
            limit: Int = 12,
        ): List<YouTubeVideo> {
            val root = extractInitialData(html)?.let { raw ->
                runCatching { json.parseToJsonElement(raw) }.getOrNull()
            } ?: return emptyList()

            val renderers = mutableListOf<JsonObject>()
            collectRenderers(root, renderers)

            val seen = mutableSetOf<String>()
            val videos = mutableListOf<YouTubeVideo>()

            for (renderer in renderers) {
                val videoId = renderer.string("videoId") ?: continue
                if (!seen.add(videoId)) continue

                val title = renderer.textOf("title") ?: continue
                val channel = renderer.textOf("ownerText")
                    ?: renderer.textOf("longBylineText")
                    ?: ""

                videos += YouTubeVideo(
                    videoId = videoId,
                    title = title,
                    channel = channel,
                    durationMs = parseDurationMs(renderer.textOf("lengthText")),
                    viewCount = parseViewCount(renderer.textOf("viewCountText")),
                )

                if (videos.size >= limit) break
            }

            return videos
        }

        /** 取出 `ytInitialData = {...}` 那段 JSON 字串。 */
        private fun extractInitialData(html: String): String? {
            var index = html.indexOf("ytInitialData")
            while (index >= 0) {
                val brace = html.indexOf('{', index)
                if (brace < 0) return null
                val balanced = readBalancedObject(html, brace)
                if (balanced != null) return balanced
                index = html.indexOf("ytInitialData", index + 1)
            }
            return null
        }

        /** 從 `start` 的 `{` 開始，回傳配對到的完整物件字串（正確跳過字串內的括號）。 */
        private fun readBalancedObject(text: String, start: Int): String? {
            var depth = 0
            var inString = false
            var escaped = false

            for (i in start until text.length) {
                val c = text[i]
                when {
                    escaped -> escaped = false
                    c == '\\' && inString -> escaped = true
                    c == '"' -> inString = !inString
                    inString -> Unit
                    c == '{' -> depth++
                    c == '}' -> {
                        depth--
                        if (depth == 0) return text.substring(start, i + 1)
                    }
                }
            }
            return null
        }

        /** 遞迴收集所有 `videoRenderer` / `compactVideoRenderer`。 */
        private fun collectRenderers(element: JsonElement, out: MutableList<JsonObject>) {
            when (element) {
                is JsonObject -> {
                    element["videoRenderer"]?.let { if (it is JsonObject) out += it }
                    element["compactVideoRenderer"]?.let { if (it is JsonObject) out += it }
                    element.values.forEach { collectRenderers(it, out) }
                }

                is JsonArray -> element.forEach { collectRenderers(it, out) }
                else -> Unit
            }
        }

        /** 取 `{"simpleText": ...}` 或 `{"runs": [{"text": ...}]}` 的文字。 */
        private fun JsonObject.textOf(key: String): String? {
            val node = this[key] as? JsonObject ?: return null
            (node["simpleText"] as? JsonPrimitive)?.contentOrNullSafe()?.let { return it }
            val runs = node["runs"] as? JsonArray ?: return null
            val parts = runs.mapNotNull { run ->
                ((run as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNullSafe()
            }
            return parts.joinToString("").takeIf { it.isNotBlank() }
        }

        private fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNullSafe()?.takeIf { it.isNotBlank() }

        private fun JsonPrimitive.contentOrNullSafe(): String? =
            if (this is JsonPrimitive && !this.isString) {
                // 數字型欄位（例如 viewCount 的數字形式）也要能取
                content.takeIf { it.isNotBlank() && it != "null" }
            } else {
                content.takeIf { it.isNotBlank() && it != "null" }
            }

        /** `4:02` / `1:02:03` → 毫秒。 */
        fun parseDurationMs(text: String?): Long? {
            if (text.isNullOrBlank()) return null
            val parts = text.trim().split(":")
            if (parts.size !in 2..3) return null
            val numbers = parts.map { it.trim().toLongOrNull() ?: return null }
            val seconds = when (numbers.size) {
                2 -> numbers[0] * 60 + numbers[1]
                else -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
            }
            return seconds * 1000
        }

        /** `1,234,567 views` → 1234567。 */
        fun parseViewCount(text: String?): Long {
            if (text.isNullOrBlank()) return 0
            return text.filter { it.isDigit() }.toLongOrNull() ?: 0
        }
    }
}
