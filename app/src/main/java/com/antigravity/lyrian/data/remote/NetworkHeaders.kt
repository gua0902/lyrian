package com.antigravity.lyrian.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 全站共用的請求標頭。
 *
 * ## 為什麼一定要改 User-Agent
 *
 * OkHttp 預設送 `okhttp/4.12.0`。Cloudflare（擋在 lrclib.net 前面）與 Google
 * 都把這種 UA 當成爬蟲，**直接回 503**——而用瀏覽器開同一個網址卻是正常的。
 * 這是「App 內 503、瀏覽器正常」最典型的原因。
 *
 * 因此所有外呼（lrclib／iTunes／YouTube 搜尋）都必須走同一組瀏覽器標頭。
 * 三個網域共用同一個 [okhttp3.OkHttpClient] 實例，攔截器掛在那裡，
 * 不會有「某個請求忘了帶」的情況。
 */
object NetworkHeaders {

    /**
     * 一般 Android Chrome 的 UA。
     *
     * 刻意不含 `okhttp`、`wv`、`Version/4.0`——這三個字串都會被判定為
     * 「非瀏覽器」或「受限的 WebView」。
     */
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    /** JSON API（LRCLIB、iTunes）用的 Accept。 */
    const val JSON_ACCEPT = "application/json, text/plain, */*"

    /** 抓 HTML 頁面（YouTube 搜尋結果）用的 Accept。 */
    const val HTML_ACCEPT =
        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"

    const val ACCEPT_LANGUAGE = "en-US,en;q=0.9"

    /**
     * ⚠️ **刻意不覆寫 `Accept-Encoding`**。
     *
     * 這不是漏掉：OkHttp 只在「自己加上這個 header」時才會透明解壓 gzip。
     * 一旦我們手動寫 `Accept-Encoding: gzip, deflate, br`，OkHttp 就不再解壓，
     * `response.body.string()` 會拿到壓縮過的位元組——JSON 解析全部失敗，
     * 而且錯誤看起來完全不像編碼問題（會變成「回應格式錯誤」）。
     *
     * 交給 OkHttp 自己送 `gzip` 就好：那同樣是瀏覽器行為、不會被判定為爬蟲，
     * 而且三個網域拿到的是**同一組** header（見 [LyrianNetLoggingInterceptor] 的 log）。
     */
    const val NOTE_ACCEPT_ENCODING = "由 OkHttp 自動帶 gzip 並透明解壓"
}

/**
 * 把瀏覽器標頭套到**每一個**請求上。
 *
 * - `User-Agent`：一律覆寫（這是 503 的根因）
 * - `Accept`：請求自己指定過就尊重它（抓 HTML 的請求要用 [NetworkHeaders.HTML_ACCEPT]）
 * - `Accept-Language`：固定 en-US（上游會依此回英文內容）
 * - `Connection: keep-alive`：與瀏覽器一致
 */
class BrowserHeadersInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        val accept = original.header("Accept") ?: NetworkHeaders.JSON_ACCEPT

        val request = original.newBuilder()
            .header("User-Agent", NetworkHeaders.BROWSER_USER_AGENT)
            .header("Accept", accept)
            .header("Accept-Language", NetworkHeaders.ACCEPT_LANGUAGE)
            .header("Connection", "keep-alive")
            .build()

        return chain.proceed(request)
    }
}

/**
 * 網路診斷日誌（tag `LyrianNet`）。
 *
 * 每次外呼印四件事：**URL、實際送出的 User-Agent、回應 status、回應前 200 字元**。
 * 503 的時候這四行就能分辨是「UA 被拒」還是「其他原因」：
 *
 * ```
 * D/LyrianNet: → GET https://lrclib.net/api/search?artist_name=Adele...
 * D/LyrianNet:   UA=Mozilla/5.0 (Linux; Android 14; Pixel 8) ... Chrome/126.0.0.0
 * D/LyrianNet: ← 200 （18 ms，3421 bytes）
 * D/LyrianNet:   body[0..200]=[{"id":7136713,"trackName":"Hello",...}]
 * ```
 *
 * 回應內容用 [Response.peekBody] 讀，**不會吃掉**後續解析要用的 body。
 */
class LyrianNetLoggingInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        android.util.Log.d(TAG, "→ ${request.method} $url")
        android.util.Log.d(
            TAG,
            "  UA=${request.header("User-Agent")} | Accept=${request.header("Accept")} | " +
                "Accept-Encoding=${request.header("Accept-Encoding")}",
        )

        val startedAt = System.nanoTime()
        val response = chain.proceed(request)
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        val length = response.body?.contentLength() ?: -1L
        android.util.Log.d(TAG, "← ${response.code} （${elapsedMs} ms，${length} bytes）$url")

        if (!response.isSuccessful) {
            // 失敗時把回應開頭印出來——503 的 HTML/JSON 會直接說明原因
            val preview = runCatching { response.peekBody(PREVIEW_BYTES).string() }.getOrNull()
            android.util.Log.w(TAG, "  body[0..200]=${preview?.take(PREVIEW_CHARS)}")
        } else if (request.url.encodedPath.endsWith("/api/search")) {
            val preview = runCatching { response.peekBody(PREVIEW_BYTES).string() }.getOrNull()
            android.util.Log.d(TAG, "  body[0..200]=${preview?.take(PREVIEW_CHARS)}")
        }

        return response
    }

    private companion object {
        const val TAG = "LyrianNet"

        /** 讀多一點再截斷，避免多位元組字元被切壞。 */
        const val PREVIEW_BYTES = 2_048L

        const val PREVIEW_CHARS = 200
    }
}
