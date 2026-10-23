package com.antigravity.lyrian.data.audio

import android.annotation.SuppressLint
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.graphics.Color
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 播放器狀態。 */
data class PlayerState(
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    /** 播放位置（毫秒）。廣告期間會被凍結，見 [YouTubePlayerController.onTick]。 */
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val error: String? = null,
)

/**
 * YouTube 播放器：用**官方 IFrame Player API** 播放，嵌在畫面裡。
 *
 * ## 為什麼是 IFrame 而不是自己抓串流
 *
 * 擁有者要求「播放器需內嵌在練習頁封面區域且保持可見，不可縮成 1x1 隱藏」，
 * 並且明訂「不加入任何廣告攔截、去廣告或串流抓取邏輯」。
 * IFrame Player 正好是唯一同時滿足這兩條的作法：它是官方播放器、廣告自然播放，
 * 我們只讀 `getCurrentTime()` 當時間軸。
 *
 * （另一條路 NewPipe Extractor 需要自己抓串流並實作 `Downloader`，
 * 會踩到簽章與 PoToken，而且**繞過廣告**——與擁有者的要求相反。）
 *
 * ## 時間軸為什麼會被凍結
 *
 * 廣告期間 IFrame 回報的時間會**倒退**（廣告自己從 0 開始，或停在 0）。
 * [onTick] 因此做單調遞增的夾制：位置突然倒退就忽略這次回報。
 * 這就是「廣告期間歌詞自然凍結」，不需要偵測廣告。
 *
 * ## WebView 的生命週期
 *
 * WebView **不放在單例裡**（那會洩漏 Activity）。由 `AndroidView` 建立後
 * 透過 [attach] 交給這個控制器，[detach] 時放掉。旋轉螢幕時 WebView 會重建，
 * 控制器會用 [pendingLoad] 把**同一個影片載回來並回到原本的位置**——
 * 聲音會中斷一下，但歌詞與進度不會跑掉。
 */
@Singleton
class YouTubePlayerController @Inject constructor() {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var webView: WebView? = null

    /** 最近一次載入要求；WebView 重建後用它恢復。 */
    private var pendingLoad: LoadRequest? = null

    private var lastPositionMs = 0L

    /** 目前音量（0–100），淡入淡出時以它為起點。 */
    private var currentVolume = 100

    /** 使用者剛剛拖過進度條：下一次回報的倒退是合理的。 */
    private var seekPending = false

    private data class LoadRequest(val videoId: String, val startAtMs: Long, val autoPlay: Boolean)

    /**
     * 綁定一個 WebView 並載入播放器頁。
     *
     * 由 `AndroidView` 的 `factory` 呼叫。同一個 WebView 重複呼叫不會重載。
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun attach(view: WebView) {
        if (webView === view) return
        webView = view

        view.setBackgroundColor(Color.BLACK)
        view.settings.apply {
            javaScriptEnabled = true
            // 使用者按了播放，之後由 `Lyrian.play()` 觸發，不需要點擊手勢
            mediaPlaybackRequiresUserGesture = false
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        view.webViewClient = WebViewClient()
        view.addJavascriptInterface(Bridge(), "Bridge")
        view.loadDataWithBaseURL(BASE_URL, PLAYER_HTML, "text/html", "utf-8", null)

        pendingLoad?.let { request ->
            _state.value = PlayerState()
            view.post {
                load(request.videoId, request.startAtMs, request.autoPlay)
            }
        }
    }

    /** 畫面結束時解除綁定。播放器的狀態刻意保留，回到同一頁時可直接恢復。 */
    fun detach() {
        webView?.removeJavascriptInterface("Bridge")
        webView = null
    }

    /**
     * 載入（或換成）一個影片。
     *
     * @param startAtMs 從哪裡開始播；換版本時給 0
     * @param autoPlay 載入後是否直接播放
     */
    fun load(videoId: String, startAtMs: Long = 0L, autoPlay: Boolean = false) {
        pendingLoad = LoadRequest(videoId, startAtMs, autoPlay)
        lastPositionMs = startAtMs
        seekPending = true
        _state.value = PlayerState(positionMs = startAtMs)
        evaluate(js("Lyrian.load(%s, %.2f, %s)", videoId.jsString(), startAtMs / 1000.0, autoPlay))
    }

    fun play() {
        evaluate("Lyrian.play()")
    }

    fun pause() {
        evaluate("Lyrian.pause()")
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        lastPositionMs = positionMs.coerceAtLeast(0L)
        seekPending = true
        _state.value = _state.value.copy(positionMs = lastPositionMs)
        evaluate(js("Lyrian.seek(%.2f)", lastPositionMs / 1000.0))
    }

    fun currentPositionMs(): Long = lastPositionMs

    /** 音量淡出（0–100）。IFrame 的 `setVolume` 是唯一的音量控制方式。 */
    suspend fun fadeOut(durationMs: Long = FADE_MS) = rampVolume(from = null, to = 0, durationMs)

    /** 音量淡入，回到滿音量。 */
    suspend fun fadeIn(durationMs: Long = FADE_MS) = rampVolume(from = 0, to = 100, durationMs)

    private suspend fun rampVolume(from: Int?, to: Int, durationMs: Long) {
        val start = from ?: currentVolume
        for (step in 0..FADE_STEPS) {
            val fraction = step.toFloat() / FADE_STEPS
            currentVolume = start + ((to - start) * fraction).toInt()
            evaluate("Lyrian.volume($currentVolume)")
            delay((durationMs / FADE_STEPS).coerceAtLeast(1L))
        }
        currentVolume = to
    }

    /** 停止並清掉目前影片。 */
    fun stop() {
        pendingLoad = null
        lastPositionMs = 0L
        _state.value = PlayerState()
        evaluate("Lyrian.pause()")
    }

    fun release() {
        stop()
        detach()
    }

    private fun evaluate(script: String) {
        val view = webView ?: return
        // 頁面可能還沒載入完（`Lyrian` 尚未定義）。先把指令排進 JS 端的佇列，
        // 由播放器頁面在初始化完成後自己排空——這樣就不必猜「等多久才安全」。
        val wrapped = "(function(){ if (window.Lyrian) { $script; } else { " +
            "(window.__lyrianQueue = window.__lyrianQueue || []).push(function(){ $script; }); } })()"
        val run = { view.evaluateJavascript(wrapped, null) }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            run()
        } else {
            view.post(run)
        }
    }

    /**
     * 用 **Locale.US** 格式化小數。
     *
     * `String.format` 預設用系統語系——在德文/法文環境會產生 `12,50`，
     * 送進 JS 會變成語法錯誤。這種錯只會在某些語系的手機上出現，
     * 本地測試永遠看不到。
     */
    private fun js(template: String, vararg args: Any?): String =
        java.lang.String.format(java.util.Locale.US, template, *args)

    // ---------- JS 回呼 ----------

    /**
     * JS 每 100ms 回報一次位置。
     *
     * **單調夾制**：位置突然倒退（廣告開始、或播放器重置）時忽略，
     * 讓歌詞時間軸停在原處。使用者自己拖曳時 [seekPending] 會放行。
     */
    private fun onTick(positionMs: Long, durationMs: Long?, state: Int) {
        val sanitized = positionMs.coerceAtLeast(0L)

        if (!seekPending && sanitized < lastPositionMs - REWIND_TOLERANCE_MS) {
            // 廣告把時間拉回 0 → 凍結歌詞；只更新播放狀態
            _state.value = _state.value.copy(
                isPlaying = state == YT_STATE_PLAYING,
                isBuffering = state == YT_STATE_BUFFERING,
            )
            return
        }

        if (seekPending && kotlin.math.abs(sanitized - lastPositionMs) > REWIND_TOLERANCE_MS) {
            // 還在等播放器追上剛剛拖到的位置
            return
        }
        seekPending = false

        lastPositionMs = sanitized
        _state.value = _state.value.copy(
            isReady = true,
            isPlaying = state == YT_STATE_PLAYING,
            isBuffering = state == YT_STATE_BUFFERING,
            positionMs = sanitized,
            durationMs = durationMs?.takeIf { it > 0 } ?: _state.value.durationMs,
            error = null,
        )
    }

    private fun MutableStateFlow<PlayerState>.updatePosition(positionMs: Long) {
        value = value.copy(positionMs = positionMs)
    }

    private inner class Bridge {

        @JavascriptInterface
        fun onTick(positionSeconds: Double, durationSeconds: Double, state: Int) {
            onTick(
                positionMs = (positionSeconds * 1000).toLong(),
                durationMs = if (durationSeconds > 0) (durationSeconds * 1000).toLong() else null,
                state = state,
            )
        }

        @JavascriptInterface
        fun onState(state: Int) {
            _state.value = _state.value.copy(
                isPlaying = state == YT_STATE_PLAYING,
                isBuffering = state == YT_STATE_BUFFERING,
                isReady = _state.value.isReady || state == YT_STATE_CUED,
            )
        }

        @JavascriptInterface
        fun onReady() {
            _state.value = _state.value.copy(isReady = true, isBuffering = false)
        }

        /**
         * IFrame 的錯誤碼。
         *
         * `101`／`150` 是「擁有者不允許嵌入」，這是最常見的失敗——通常換一個
         * 候選就會好，所以 UI 要能據此自動換版本。
         */
        @JavascriptInterface
        fun onError(code: Int) {
            val message = when (code) {
                2 -> "影片 id 無效"
                5 -> "HTML5 播放器錯誤"
                100 -> "影片已移除或設為私人"
                101, 150 -> "這個版本不允許嵌入播放"
                else -> "播放錯誤（$code）"
            }
            _state.value = _state.value.copy(
                isPlaying = false,
                isBuffering = false,
                error = message,
            )
        }
    }

    private companion object {
        /** 給 IFrame API 一個正常的 origin，否則會拒絕初始化。 */
        const val BASE_URL = "https://www.youtube.com/"

        /** 淡入淡出的總時長（擁有者指定 0.3 秒）。 */
        const val FADE_MS = 300L

        const val FADE_STEPS = 20

        /** 位置「倒退」多少毫秒以上才視為異常（廣告／重置）。 */
        const val REWIND_TOLERANCE_MS = 1_500L

        // YT.PlayerState
        const val YT_STATE_PLAYING = 1
        const val YT_STATE_BUFFERING = 3
        const val YT_STATE_CUED = 5

        /**
         * 播放器頁面。
         *
         * 這個 HTML 是**唯一的**播放實作：官方 IFrame API + 100ms 的位置回報。
         * `controls: 0` 是因為練習頁有自己的播放鍵，兩個播放鍵會互相打架。
         */
        val PLAYER_HTML: String = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
              <style>
                html,body{margin:0;padding:0;height:100%;background:#000;overflow:hidden}
                #p{width:100%;height:100%}
                iframe{width:100%;height:100%;border:0}
              </style>
            </head>
            <body>
              <div id="p"></div>
              <script>
                var player = null, apiReady = false, wanted = null, timer = null;

                function onYouTubeIframeAPIReady() {
                  apiReady = true;
                  if (wanted) { var cfg = wanted; wanted = null; start(cfg); }
                }

                function start(cfg) {
                  player = new YT.Player('p', {
                    videoId: cfg.videoId,
                    playerVars: {
                      autoplay: cfg.autoPlay ? 1 : 0,
                      controls: 0,
                      disablekb: 1,
                      playsinline: 1,
                      rel: 0,
                      modestbranding: 1,
                      iv_load_policy: 3,
                      fs: 0,
                      origin: 'https://www.youtube.com'
                    },
                    events: {
                      onReady: function () {
                        Bridge.onReady();
                        if (cfg.startSec > 0) { player.seekTo(cfg.startSec, true); }
                        if (cfg.autoPlay) { player.playVideo(); }
                        tick();
                      },
                      onStateChange: function (e) { Bridge.onState(e.data); },
                      onError: function (e) { Bridge.onError(e.data); }
                    }
                  });
                }

                function tick() {
                  if (timer) return;
                  timer = setInterval(function () {
                    if (!player || !player.getCurrentTime) return;
                    try {
                      Bridge.onTick(
                        player.getCurrentTime() || 0,
                        player.getDuration() || 0,
                        player.getPlayerState()
                      );
                    } catch (e) { }
                  }, 100);
                }

                var Lyrian = {
                  load: function (videoId, startSec, autoPlay) {
                    var cfg = { videoId: videoId, startSec: startSec, autoPlay: autoPlay };
                    if (!apiReady) { wanted = cfg; return; }
                    if (player && player.loadVideoById) {
                      player.loadVideoById({ videoId: videoId, startSeconds: startSec });
                      if (autoPlay) { player.playVideo(); }
                      return;
                    }
                    start(cfg);
                  },
                  play: function () { if (player) { player.playVideo(); } },
                  pause: function () { if (player) { player.pauseVideo(); } },
                  seek: function (sec) { if (player) { player.seekTo(sec, true); } },
                  volume: function (v) { if (player) { player.setVolume(v); } }
                };

                // 頁面載入前 Kotlin 送來的指令，現在可以執行了
                if (window.__lyrianQueue) {
                  var queued = window.__lyrianQueue;
                  window.__lyrianQueue = [];
                  for (var i = 0; i < queued.length; i++) { try { queued[i](); } catch (e) { } }
                }
              </script>
              <script src="https://www.youtube.com/iframe_api"></script>
            </body>
            </html>
        """.trimIndent()
    }
}

/** 把字串包成 JS 字面值；影片 id 只含 `[A-Za-z0-9_-]`，仍保險轉義一次。 */
private fun String.jsString(): String = "'" + replace("\\", "\\\\").replace("'", "\\'") + "'"
