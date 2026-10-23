package com.antigravity.lyrian.data.audio

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
    /** 這個版本被版權方禁止嵌入（YouTube 的 101 / 150）。 */
    val embedBlocked: Boolean = false,
    /** 影片的長寬比（寬 / 高）；還沒拿到時為 null。 */
    val videoAspectRatio: Float? = null,
)

/**
 * YouTube 播放器：**直接載入 embed 頁**，再用 JS 操作底層的 `<video>` 元素。
 *
 * ## 為什麼不再用本地 HTML + iframe_api
 *
 * 前一版把播放器包在本地 HTML 裡、用 `YT.Player` + `postMessage` 控制。
 * 實機測試的結果是**完全沒聲音**：`loadDataWithBaseURL` 的頁面裡，
 * IFrame API 常因 origin 不符而拒絕初始化，而且錯誤完全被吞掉。
 *
 * 現在改成把 `https://www.youtube.com/embed/{id}` 當成**頂層頁面**載入：
 *  - origin 就是 youtube.com，沒有跨來源問題
 *  - 不需要 iframe_api 腳本
 *  - 直接操作真正的 `<video>` 元素：`currentTime` / `play()` / `pause()` / `volume`
 *
 * 代價是拿不到 `YT.Player` 的錯誤碼，所以改用 `ytInitialPlayerResponse` 的
 * `playabilityStatus`（頁面自己會把播放器設定放在那裡）判斷「不能嵌入」。
 *
 * ## 廣告與時間軸
 *
 * 廣告期間 `<video>.currentTime` 會**倒退**（廣告自己從 0 開始）。
 * [onTick] 因此做單調夾制：倒退超過 1.5 秒就忽略該次回報，
 * 歌詞時間軸自然凍結——不需要偵測廣告，也沒有任何攔截邏輯。
 *
 * ## 尺寸
 *
 * 擁有者要求播放器「內嵌在封面區域、保持可見、不可縮成 1x1」。
 * 這裡只負責回報 `videoWidth / videoHeight`，實際尺寸由 Compose 依封面框決定。
 */
@Singleton
class YouTubePlayerController @Inject constructor() {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var webView: WebView? = null

    /** 目前要載入的影片；WebView 重建（旋轉）後用它恢復。 */
    private var pendingLoad: LoadRequest? = null

    private var lastPositionMs = 0L

    /** 目前音量（0–100），淡入淡出時以它為起點。 */
    private var currentVolume = 100

    /** 使用者剛剛拖過進度條：下一次回報的倒退是合理的。 */
    private var seekPending = false

    /** 已經注入過腳本（每次換頁都要重新注入）。 */
    private var injected = false

    /** 已回報過幾次 tick（只印前幾筆，避免 log 被灌爆）。 */
    private var tickLogs = 0

    /** 靜音自救只做一次，避免每 200ms 重送同一道指令。 */
    private var triedUnmute = false

    private data class LoadRequest(val videoId: String, val startAtMs: Long, val autoPlay: Boolean)

    // ---------- 生命週期 ----------

    /**
     * 綁定 WebView 並載入播放器頁。
     *
     * 由 `AndroidView` 的 factory 呼叫。同一個 WebView 重複呼叫不會重載。
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun attach(view: WebView) {
        if (webView === view) return
        webView = view
        injected = false

        view.setBackgroundColor(Color.BLACK)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // 使用者已經按了播放鍵；之後由 JS 的 video.play() 接手，
            // 若沿用預設值，Chrome 會以「沒有手勢」為由拒絕播放。
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // 預設的 WebView UA 帶著 "Version/4.0"，YouTube 會當成舊版瀏覽器
            // 而回一個功能受限的播放器。改成一般 Chrome 的 UA。
            userAgentString = DESKTOP_UA
        }

        view.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                injected = false
                log("頁面開始載入：$url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                log("頁面載入完成：$url")
                injectBridgeScript(view)
            }
        }

        view.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                // YouTube 自己的訊息很多，只在 debug 等級轉發
                log("JS console[${message.messageLevel()}] ${message.message()}")
                return true
            }
        }

        view.addJavascriptInterface(Bridge(), "Bridge")

        pendingLoad?.let { request ->
            log("attach：恢復播放 ${request.videoId} @${request.startAtMs}ms")
            view.post { load(request.videoId, request.startAtMs, request.autoPlay) }
        } ?: run {
            log("attach：尚無影片，等待 prepareAudio()")
        }
    }

    /** 畫面結束時解除綁定。播放器的狀態刻意保留，回到同一頁時可直接恢復。 */
    fun detach() {
        webView?.removeJavascriptInterface("Bridge")
        webView = null
        injected = false
    }

    // ---------- 指令 ----------

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
        tickLogs = 0
        _state.value = PlayerState(positionMs = startAtMs)

        val start = if (startAtMs > 0) "&start=${startAtMs / 1000}" else ""
        val url = "https://www.youtube.com/embed/$videoId" +
            "?autoplay=${if (autoPlay) 1 else 0}&playsinline=1$start"

        val view = webView
        if (view == null) {
            log("load：WebView 還沒建立，先記住 $videoId，attach 後再載入")
            return
        }

        log("load：$url")
        view.post { view.loadUrl(url) }
    }

    fun play() {
        evaluateCommand("play") { """
            (function(){
              var v = document.querySelector('video');
              if (!v) return 'no-video';
              v.muted = false;
              if (v.paused) {
                var p = v.play();
                if (p && p.catch) { p.catch(function(e){ Bridge.onLog('play rejected: ' + e); }); }
              }
              return 'playing@' + (v.currentTime||0);
            })()
        """ }
    }

    fun pause() {
        evaluateCommand("pause") { """
            (function(){
              var v = document.querySelector('video');
              if (!v) return 'no-video';
              v.pause();
              return 'paused@' + (v.currentTime||0);
            })()
        """ }
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        lastPositionMs = positionMs.coerceAtLeast(0L)
        seekPending = true
        _state.value = _state.value.copy(positionMs = lastPositionMs)
        val seconds = js("%.2f", lastPositionMs / 1000.0)
        evaluateCommand("seek") { """
            (function(){
              var v = document.querySelector('video');
              if (!v) return 'no-video';
              v.currentTime = $seconds;
              return 'seek@' + v.currentTime;
            })()
        """ }
    }

    fun currentPositionMs(): Long = lastPositionMs

    /** 音量淡出（0–100）。 */
    suspend fun fadeOut(durationMs: Long = FADE_MS) = rampVolume(from = null, to = 0, durationMs)

    /** 音量淡入，回到滿音量。 */
    suspend fun fadeIn(durationMs: Long = FADE_MS) = rampVolume(from = 0, to = 100, durationMs)

    private suspend fun rampVolume(from: Int?, to: Int, durationMs: Long) {
        val start = from ?: currentVolume
        for (step in 0..FADE_STEPS) {
            val fraction = step.toFloat() / FADE_STEPS
            currentVolume = start + ((to - start) * fraction).toInt()
            setVolume(currentVolume)
            delay((durationMs / FADE_STEPS).coerceAtLeast(1L))
        }
        currentVolume = to
        setVolume(to)
    }

    private fun setVolume(percent: Int) {
        val value = js("%.2f", percent.coerceIn(0, 100) / 100.0)
        evaluateCommand("volume") { """
            (function(){
              var v = document.querySelector('video');
              if (!v) return 'no-video';
              v.volume = $value;
              v.muted = false;
              return 'vol=' + v.volume;
            })()
        """ }
    }

    /** 停止並清掉目前影片。 */
    fun stop() {
        pendingLoad = null
        lastPositionMs = 0L
        _state.value = PlayerState()
        evaluateCommand("stop") { """
            (function(){
              var v = document.querySelector('video');
              if (v) { v.pause(); }
              return 'stopped';
            })()
        """ }
    }

    fun release() {
        stop()
        detach()
    }

    // ---------- JS 注入 ----------

    /**
     * 注入監測腳本。
     *
     * 每 200ms 回報一次 `<video>` 的狀態；找不到 `<video>` 時改查
     * `ytInitialPlayerResponse` 的 `playabilityStatus`，用來判斷「不能嵌入」。
     */
    private fun injectBridgeScript(view: WebView?) {
        view ?: return
        if (injected) return
        injected = true

        val script = """
            (function(){
              if (window.__lyrian) return 'already';
              window.__lyrian = true;

              // 隱藏「在 YouTube 中開啟」與其他蓋在影片上的提示／遮罩。
              // 這只是把 YouTube 自己畫在影片上的浮層收起來，**不影響廣告**：
              // 廣告是播放器內容的一部分，不是 DOM 浮層。
              var css = '.ytp-chrome-top,.ytp-gradient-top,.ytp-pause-overlay,' +
                        '.ytp-cards-teaser,.ytp-ce-element,.ytp-endscreen-content,' +
                        '.ytp-show-cards-title,.ytp-title,.ytp-watermark,' +
                        '.ytp-persistent-progress-bar-container{display:none!important}' +
                        ' html,body{margin:0;padding:0;background:#000;overflow:hidden}' +
                        ' video{object-fit:contain}';
              var st = document.createElement('style');
              st.textContent = css;
              (document.head || document.documentElement).appendChild(st);

              var reported = false;
              function reportUnavailable(){
                var pr = window.ytInitialPlayerResponse;
                if (pr && pr.playabilityStatus && pr.playabilityStatus.status &&
                    pr.playabilityStatus.status !== 'OK') {
                  Bridge.onUnavailable(
                    String(pr.playabilityStatus.status),
                    String(pr.playabilityStatus.reason || ''),
                    JSON.stringify(pr.playabilityStatus.errorScreen || {})
                  );
                  return true;
                }
                var e = document.querySelector('.ytp-error-content-wrap,.ytp-error');
                if (e && e.innerText) {
                  Bridge.onUnavailable('ERROR_PAGE', e.innerText.slice(0, 160), '');
                  return true;
                }
                return false;
              }

              setInterval(function(){
                var v = document.querySelector('video');
                if (!v) {
                  if (!reported) { reported = reportUnavailable(); }
                  return;
                }
                if (!window.__lyrianVideo) {
                  window.__lyrianVideo = true;
                  Bridge.onVideoFound(v.videoWidth || 0, v.videoHeight || 0);
                }
                Bridge.onTick(
                  v.currentTime || 0,
                  v.duration || 0,
                  v.paused ? 2 : 1,
                  v.volume,
                  v.muted ? 1 : 0,
                  v.readyState
                );
              }, 200);

              return 'injected';
            })()
        """.trimIndent()

        view.evaluateJavascript(script) { result -> log("注入監測腳本：$result") }
    }

    /** 執行一段 JS 並把回傳值寫進 log；這是「播放不動」時最重要的線索。 */
    private fun evaluateCommand(name: String, script: () -> String) {
        val view = webView ?: run {
            log("指令 $name 略過：WebView 尚未建立")
            return
        }
        val body = script()
        val run = {
            view.evaluateJavascript(body) { result -> log("指令 $name → $result") }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run() else view.post(run)
    }

    // ---------- JS 回呼 ----------

    /**
     * JS 每 200ms 回報一次位置。
     *
     * **單調夾制**：位置突然倒退（廣告開始、或播放器重置）時忽略，
     * 讓歌詞時間軸停在原處。使用者自己拖曳時 [seekPending] 會放行。
     */
    private fun handleTick(positionMs: Long, durationMs: Long?, state: Int) {
        val sanitized = positionMs.coerceAtLeast(0L)

        if (!seekPending && sanitized < lastPositionMs - REWIND_TOLERANCE_MS) {
            // 廣告把時間拉回 0 → 凍結歌詞；只更新播放狀態
            _state.value = _state.value.copy(
                isPlaying = state == STATE_PLAYING,
                isBuffering = state == STATE_BUFFERING,
            )
            return
        }

        if (seekPending && kotlin.math.abs(sanitized - lastPositionMs) > REWIND_TOLERANCE_MS) {
            return
        }
        seekPending = false

        lastPositionMs = sanitized
        _state.value = _state.value.copy(
            isReady = true,
            isPlaying = state == STATE_PLAYING,
            isBuffering = state == STATE_BUFFERING,
            positionMs = sanitized,
            durationMs = durationMs?.takeIf { it > 0 } ?: _state.value.durationMs,
            error = null,
        )

        if (tickLogs < MAX_TICK_LOGS) {
            tickLogs++
            log("tick #$tickLogs：getCurrentTime=${js("%.2f", sanitized / 1000.0)}s " +
                "duration=${js("%.1f", (durationMs ?: 0) / 1000.0)}s state=$state")
        }
    }

    private inner class Bridge {

        /** 找到 `<video>` 了：回報尺寸（給 Compose 算長寬比）並確保解除靜音。 */
        @JavascriptInterface
        fun onVideoFound(width: Int, height: Int) {
            log("找到 <video>：${width}x$height")
            _state.value = _state.value.copy(
                isReady = true,
                videoAspectRatio = if (width > 0 && height > 0) {
                    width.toFloat() / height.toFloat()
                } else {
                    null
                },
            )
            val shouldPlay = pendingLoad?.autoPlay == true
            evaluateCommand("unmute") { """
                (function(){
                  var v = document.querySelector('video');
                  if (!v) return 'no-video';
                  v.muted = false;
                  if ($shouldPlay && v.paused) {
                    var p = v.play();
                    if (p && p.catch) { p.catch(function(e){ Bridge.onLog('autoplay rejected: ' + e); }); }
                  }
                  return 'ready state=' + v.readyState + ' muted=' + v.muted + ' paused=' + v.paused;
                })()
            """ }
        }

        @JavascriptInterface
        fun onTick(
            positionSeconds: Double,
            durationSeconds: Double,
            state: Int,
            volume: Double,
            muted: Int,
            readyState: Int,
        ) {
            if (muted == 1 && !triedUnmute) {
                triedUnmute = true
                android.util.Log.w(TAG, "影片是靜音的（muted=1），嘗試解除靜音")
                evaluateCommand("unmute") { """
                    (function(){ var v = document.querySelector('video');
                      if (!v) return 'no-video'; v.muted = false; return 'muted=' + v.muted; })()
                """ }
            }
            handleTick(
                positionMs = (positionSeconds * 1000).toLong(),
                durationMs = if (durationSeconds > 0) (durationSeconds * 1000).toLong() else null,
                state = state,
            )
        }

        /** YouTube 不讓我們播：記下狀態與原因，讓上層決定換版本或放棄。 */
        @JavascriptInterface
        fun onUnavailable(status: String, reason: String, screen: String) {
            val blocked = status == "UNPLAYABLE" || status == "LOGIN_REQUIRED" ||
                status == "ERROR" || status == "ERROR_PAGE" ||
                reason.contains("embed", ignoreCase = true) ||
                screen.contains("embed", ignoreCase = true)

            log("播放器回報不可用：status=$status reason=$reason blocked=$blocked")

            _state.value = _state.value.copy(
                isPlaying = false,
                isBuffering = false,
                embedBlocked = blocked,
                error = if (blocked) "此版本不允許嵌入" else "無法播放（$status）",
            )
        }

        @JavascriptInterface
        fun onLog(message: String) {
            log("JS: $message")
        }
    }

    private fun log(message: String) {
        android.util.Log.d(TAG, message)
    }

    /**
     * 用 **Locale.US** 格式化小數。
     *
     * `String.format` 預設用系統語系——在德文/法文環境會產生 `12,50`，
     * 送進 JS 會變成語法錯誤。這種錯只會在某些語系的手機上出現。
     */
    private fun js(template: String, vararg args: Any?): String =
        java.lang.String.format(java.util.Locale.US, template, *args)

    private companion object {
        const val TAG = "LyrianPlayer"

        /** 一般 Chrome 的 UA。刻意不含 `Version/4.0`（那是 WebView 的預設值）。 */
        const val DESKTOP_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36"

        /** 淡入淡出的總時長（擁有者指定 0.3 秒）。 */
        const val FADE_MS = 300L

        const val FADE_STEPS = 20

        /** 位置「倒退」多少毫秒以上才視為異常（廣告／重置）。 */
        const val REWIND_TOLERANCE_MS = 1_500L

        /** 位置回報只印前幾筆，避免 log 被灌爆。 */
        const val MAX_TICK_LOGS = 5

        // HTMLMediaElement 狀態（= 舊 IFrame API 的 YT.PlayerState）
        const val STATE_PLAYING = 1
        const val STATE_BUFFERING = 3
    }
}
