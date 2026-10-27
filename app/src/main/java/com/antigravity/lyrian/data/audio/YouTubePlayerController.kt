package com.antigravity.lyrian.data.audio

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Looper
import android.view.View
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
    /** 播放器回報「播完了」（HTMLMediaElement 的 `ended`）。 */
    val ended: Boolean = false,
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

    /**
     * 已經 `detach()`、但還沒 `destroy()` 的 WebView。
     *
     * `detach()` 發生在畫面被處置時（可能是設定變更重建），那時不能 destroy——
     * 新的 `attach()` 要用 `pendingLoad` 接續播放。真正的 destroy 在
     * [release]（離開練習頁）或下一次 [attach]（舊的已被新的一個取代）。
     */
    private var pendingDestroy: WebView? = null

    /** 目前要載入的影片；WebView 重建（旋轉）後用它恢復。 */
    private var pendingLoad: LoadRequest? = null

    private var lastPositionMs = 0L

    /** 目前音量（0–100），淡入淡出時以它為起點。 */
    private var currentVolume = 100

    /**
     * 漸層音量的世代號。
     *
     * 每次開始新的漸層就 +1，舊的迴圈一看到號碼變了立刻停手——
     * 這就是「漸出與漸入不會互相覆蓋」的實作。
     */
    private var rampToken = 0

    /**
     * 換版本後要接續的播放位置（毫秒）。
     *
     * 換源時把記錄的時間帶進來；等播放器回報**真實時長**之後才做校正
     * （新影片比記錄的時間短 → 停在時長前 1 秒，而不是歸零）。
     */
    private var pendingResumeMs: Long? = null

    /** 使用者剛剛拖過進度條：下一次回報的倒退是合理的。 */
    private var seekPending = false

    /** 已經注入過腳本（每次換頁都要重新注入）。 */
    private var injected = false

    /** 已回報過幾次 tick（只印前幾筆，避免 log 被灌爆）。 */
    private var tickLogs = 0

    /** 靜音自救只做一次，避免每 200ms 重送同一道指令。 */
    private var triedUnmute = false

    /**
     * 「影片已就緒」的處理（解除靜音、必要時自動播放）只做一次。
     *
     * 尺寸回報會因為 `loadedmetadata`／`resize`／輪詢而**多次**發生
     * （畫面比例可能中途才知道），每次重跑 `play()` 會打斷使用者自己按的暫停。
     */
    private var videoReadyHandled = false

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

        // 上一次 detach() 留下的舊 WebView：新的已經要接手，舊的一定要 destroy，
        // 否則每次設定變更就漏一個（含 100ms 的 JS timer 與影片解碼器）。
        pendingDestroy?.takeIf { it !== view }?.let { old ->
            pendingDestroy = null
            runCatching {
                (old.parent as? android.view.ViewGroup)?.removeView(old)
                old.destroy()
            }.onFailure { log("銷毀舊 WebView 失敗：$it") }
        }

        webView = view
        injected = false

        // 透明底 + 硬體層。
        //
        // 黑底是「有聲音但畫面全黑」的常見來源之一：WebView 的背景會蓋在
        // video 合成層下面，一旦畫面來不及輸出（或比例對不上），看到的就整片黑。
        // 改成透明之後，底下那張封面圖會透出來，過渡期看到的是封面而不是黑區。
        view.setBackgroundColor(Color.TRANSPARENT)
        // 明確指定硬體層：影片解碼後的畫面靠 GPU 合成，軟體層會直接變黑。
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)

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

                // 樣式**不能**只靠 onPageFinished 當下注入：那一刻播放器 DOM
                // 往往還沒組完（`#movie_player` 是腳本後來插進去的），
                // 選擇器會全部命中 0 個。延遲 300ms 再套一次，命中率才穩定。
                view?.postDelayed({ applyOverlayCss(view) }, CSS_INJECT_DELAY_MS)

                // 不接電腦也要拿得到的黑屏證據：1.5 秒後回報 video 的實際樣貌。
                view?.postDelayed({ reportDiagnostics(view) }, DIAGNOSE_DELAY_MS)
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
            // 重新綁定（設定變更重建 Activity）時從**目前位置**接續，
            // 而不是從 `request.startAtMs`（那是第一次載入時的起點）。
            // 舊版用 startAtMs，於是切一次深色模式就會被拉回歌曲開頭。
            val resumeMs = lastPositionMs.coerceAtLeast(request.startAtMs)
            log("attach：恢復播放 ${request.videoId} @${resumeMs}ms")
            view.post { load(request.videoId, resumeMs, request.autoPlay) }
        } ?: run {
            log("attach：尚無影片，等待 prepareAudio()")
        }
    }

    /**
     * 畫面結束時解除綁定。**不 destroy**——設定變更（深色模式、字級）會重建
     * Activity，新的 `attach()` 需要 `pendingLoad` 才能接續播放。
     * 真正的釋放交給 [release]。
     */
    fun detach() {
        val view = webView ?: return
        view.removeJavascriptInterface("Bridge")
        pendingDestroy = view
        webView = null
        injected = false
    }

    /**
     * 真的釋放 WebView（離開練習頁時由 `ViewModel.onCleared` 呼叫）。
     *
     * WebView 內部有 JS `setInterval`（每 100ms 回報一次）與 Java-JS 橋接，
     * 只把參考設成 null **不會**停掉它們：舊版每次進出練習頁都漏掉一個
     * WebView（連同它的影片解碼器）。一定要先從 parent 移除再 `destroy()`。
     */
    fun release() {
        stop()
        val doomed = listOfNotNull(webView, pendingDestroy).distinct()
        webView = null
        pendingDestroy = null
        injected = false
        doomed.forEach { view ->
            runCatching {
                (view.parent as? android.view.ViewGroup)?.removeView(view)
                view.destroy()
            }.onFailure { log("destroy WebView 失敗：$it") }
        }
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
        videoReadyHandled = false
        // 不是從 0 開始才需要校正；從 0 開始就不用管
        pendingResumeMs = startAtMs.takeIf { it > 0 }
        _state.value = PlayerState(positionMs = startAtMs)

        val start = if (startAtMs > 0) "&start=${startAtMs / 1000}" else ""
        // `origin` 必須與 Referer 對應的同一個來源，否則 YouTube 會以「缺少用戶端身分」拒絕
        // （2025-07 起新增的錯誤 153）。Referer 由 attach() 的
        // `loadUrl(url, extraHeaders = "Referer: ...")` 帶，兩者都指向本機的 app 來源。
        //
        // `controls=0&modestbranding=1&disablekb=1&fs=0` 等參數是**官方文件列的**
        // 嵌入選項：關掉控制列、隱藏品牌、關鍵盤、關全螢幕。它們能少掉一半的
        // YouTube 浮層；剩下的一半（標題、分享、結束畫面）由注入的 CSS 負責。
        val url = "https://www.youtube.com/embed/$videoId" +
            "?autoplay=${if (autoPlay) 1 else 0}" +
            "&playsinline=1&enablejsapi=1&rel=0" +
            "&controls=0&modestbranding=1&showinfo=0" +
            "&iv_load_policy=3&disablekb=1&fs=0" +
            "&origin=$APP_ORIGIN$start"

        val view = webView
        if (view == null) {
            log("load：WebView 還沒建立，先記住 $videoId，attach 後再載入")
            return
        }

        log("load：$url")
        view.post { view.loadUrl(url, mapOf("Referer" to "$APP_ORIGIN/")) }
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

    /** 音量淡入，從 0 回到滿音量。 */
    suspend fun fadeIn(durationMs: Long = FADE_MS) = rampVolume(from = 0, to = 100, durationMs)

    /**
     * 從**目前音量**平滑回到滿音量（不經 0）。
     *
     * 用在「漸出被中途取消、但還在播」的情況：直接設回 100 會有一聲爆音，
     * 從現值拉回去才不會有斷點。
     */
    suspend fun fadeToFull(durationMs: Long = FADE_MS) =
        rampVolume(from = null, to = 100, durationMs)

    /**
     * 立刻設定音量（不漸層）。
     *
     * 用在「補答後恢復播放」的第一步：**先歸零才 play()**，
     * 然後才交給 [fadeIn] 從 0 拉上來——否則會有一聲滿音量的破口。
     */
    fun setVolumePercent(percent: Int) {
        currentVolume = percent.coerceIn(0, 100)
        rampToken++
        setVolume(currentVolume)
    }

    /**
     * 音量漸層的唯一實作（漸出／漸入共用）。
     *
     * `rampToken`：每一次新的漸層都會讓舊的**自動停手**。若不做這件事，
     * 一邊漸出（暫停）一邊漸入（補答）就會互相覆蓋，最後音量停在半空中。
     */
    private suspend fun rampVolume(from: Int?, to: Int, durationMs: Long) {
        val token = ++rampToken
        val start = from ?: currentVolume
        for (step in 0..FADE_STEPS) {
            if (token != rampToken) return
            val fraction = step.toFloat() / FADE_STEPS
            currentVolume = start + ((to - start) * fraction).toInt()
            setVolume(currentVolume)
            delay((durationMs / FADE_STEPS).coerceAtLeast(1L))
        }
        if (token != rampToken) return
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
        videoReadyHandled = false
        pendingResumeMs = null
        rampToken++          // 任何進行中的音量漸層立刻停手
        _state.value = PlayerState()
        evaluateCommand("stop") { """
            (function(){
              var v = document.querySelector('video');
              if (v) { v.pause(); }
              return 'stopped';
            })()
        """ }
    }

    // ---------- JS 注入 ----------

    /**
     * 要隱藏的 YouTube 浮層。
     *
     * ## 只列「不會蓋住影片畫面」的浮層
     *
     * 這裡的每一條都是**浮在 video 上層的面板**，藏掉它們不會動到 video 本身。
     *
     * ## 漸層**不用** `display:none`
     *
     * `.ytp-gradient-top` / `.ytp-gradient-bottom` 是控制列的背景漸層，它們
     * 與 video 處於同一個合成堆疊。上一版對它用 `display:none`，結果整個
     * video 的合成層被連帶跳過——症狀就是「有聲音、UI 看得到、畫面全黑」。
     * 現在改用 `opacity:0`：一樣看不見，但不改動版面結構。
     */
    private val HIDE_PANEL_CSS = CSS_HIDE_SELECTORS.joinToString(",") + "{display:none!important}"

    /** 漸層：只降透明度，**不可以** display:none（見上面的註解）。 */
    private val GRADIENT_CSS = ".ytp-gradient-top,.ytp-gradient-bottom{opacity:0!important}"

    /**
     * 頁面基礎樣式。
     *
     *  - 全部背景透明：任何縫隙都會露出底下的封面圖，而不是黑色
     *  - `object-fit:cover` + 100%：容器已經等於影片比例，所以既不裁切也不留邊
     *
     * 上一版是 `object-fit:contain;background:#000`——contain 在比例不符的容器裡
     * 就是「上下（或左右）補黑邊」，正是擁有者看到的黑框。
     */
    private val BASE_CSS =
        "html,body,#movie_player,.html5-video-container{background:transparent!important;" +
            "margin:0;padding:0;overflow:hidden}" +
            "video{object-fit:cover!important;width:100%!important;height:100%!important;" +
            "background:transparent!important}"

    /**
     * 注入監測腳本。
     *
     * 每 100ms 回報一次 `<video>` 的狀態；找不到 `<video>` 時改查
     * `ytInitialPlayerResponse` 的 `playabilityStatus`，用來判斷「不能嵌入」。
     *
     * 另外做三件事：
     *  1. `loadedmetadata` / `resize` → 立刻回報真實的 `videoWidth × videoHeight`
     *     （Compose 要靠它算容器比例；只靠輪詢會慢半拍）
     *  2. 定義 `window.__lyrianApplyCss()`，供 onPageFinished 與 MutationObserver 呼叫
     *  3. `MutationObserver` 監看播放器被重建時把樣式補回去
     */
    private fun injectBridgeScript(view: WebView?) {
        view ?: return
        if (injected) return
        injected = true

        val selectorsJs = CSS_HIDE_SELECTORS.joinToString(",") { "'$it'" }
        val disableCss = if (DEBUG_DISABLE_CSS) "true" else "false"

        val script = """
            (function(){
              if (window.__lyrian) return 'already';
              window.__lyrian = true;

              var SELECTORS = [$selectorsJs];
              var DISABLE = $disableCss;
              var BASE = '$BASE_CSS';
              var HIDE = '$HIDE_PANEL_CSS';
              var GRAD = '$GRADIENT_CSS';

              // 套用樣式並回報每個選擇器**實際命中幾個元素**。
              // 命中 0 就代表選擇器對不上（YouTube 改版），這是最好用的線索。
              window.__lyrianApplyCss = function(){
                var st = document.getElementById('lyrian-style');
                if (!st) {
                  st = document.createElement('style');
                  st.id = 'lyrian-style';
                  (document.head || document.documentElement).appendChild(st);
                }
                st.textContent = DISABLE ? '' : (BASE + HIDE + GRAD);
                var hits = [];
                for (var i = 0; i < SELECTORS.length; i++) {
                  hits.push(SELECTORS[i] + '=' + document.querySelectorAll(SELECTORS[i]).length);
                }
                Bridge.onCssHits(hits.join(' '));
                return hits.length;
              };
              window.__lyrianApplyCss();

              // 播放器重建（換影片、廣告結束）時 style 節點可能被整個換掉 →
              // 補回去。只在節點真的不見時才重套，避免自己觸發自己無限迴圈。
              try {
                var pending = null;
                var obs = new MutationObserver(function(){
                  if (pending) return;
                  pending = setTimeout(function(){
                    pending = null;
                    if (!document.getElementById('lyrian-style')) window.__lyrianApplyCss();
                  }, 200);
                });
                obs.observe(document.documentElement, {childList: true, subtree: true});
              } catch (e) { Bridge.onLog('observer failed: ' + e); }

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

              function reportSize(){
                var v = document.querySelector('video');
                if (!v) return;
                Bridge.onVideoFound(v.videoWidth || 0, v.videoHeight || 0);
              }
              function hookVideo(v){
                v.addEventListener('loadedmetadata', reportSize);
                v.addEventListener('resize', reportSize);
                reportSize();
              }
              var hooked = false;

              setInterval(function(){
                var v = document.querySelector('video');
                if (!v) {
                  if (!reported) { reported = reportUnavailable(); }
                  return;
                }
                if (!hooked) { hooked = true; hookVideo(v); }
                Bridge.onTick(
                  v.currentTime || 0,
                  v.duration || 0,
                  v.paused ? 2 : 1,
                  v.volume,
                  v.muted ? 1 : 0,
                  v.readyState,
                  v.ended ? 1 : 0
                );
              }, 100);

              return 'injected';
            })()
        """.trimIndent()

        view.evaluateJavascript(script) { result -> log("注入監測腳本：$result") }
    }

    /** 補套一次樣式（頁面剛載入、播放器重建後各需要一次）。 */
    private fun applyOverlayCss(view: WebView?) {
        view ?: return
        val run = {
            view.evaluateJavascript(
                "(window.__lyrianApplyCss ? window.__lyrianApplyCss() : -1)",
            ) { result -> log("套用樣式：$result") }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run() else view.post(run)
    }

    /**
     * 回報 video 的實際渲染狀況（黑屏排查用）。
     *
     * 這一行 log 就能區分三種黑屏原因：
     * ```
     * D/LyrianPlayer: diagnose: {"size":"1280x720","rect":[96,54],"opacity":"1", ...}
     * ```
     *  - `size` 是 0x0 → 根本沒有解碼出畫面（不是 CSS 問題）
     *  - `rect` 與容器差很多 → 版面問題
     *  - `opacity`/`transform` 異常，或 `style:false` → CSS／注入問題
     */
    private fun reportDiagnostics(view: WebView?) {
        view ?: return
        val script = """
            (function(){
              var v = document.querySelector('video');
              var st = document.getElementById('lyrian-style');
              if (!v) {
                Bridge.onDiagnostics('no-video style=' + (!!st));
                return 'no-video';
              }
              var r = v.getBoundingClientRect();
              var cs = getComputedStyle(v);
              Bridge.onDiagnostics(JSON.stringify({
                size: (v.videoWidth || 0) + 'x' + (v.videoHeight || 0),
                rect: [Math.round(r.width), Math.round(r.height)],
                opacity: cs.opacity,
                visibility: cs.visibility,
                display: cs.display,
                transform: cs.transform,
                objectFit: cs.objectFit,
                readyState: v.readyState,
                paused: v.paused,
                style: !!st
              }));
              return 'diag';
            })()
        """.trimIndent()
        evaluateCommand("diagnose", { script })
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
    private fun handleTick(positionMs: Long, durationMs: Long?, state: Int, ended: Boolean = false) {
        val sanitized = positionMs.coerceAtLeast(0L)

        if (!seekPending && sanitized < lastPositionMs - REWIND_TOLERANCE_MS) {
            // 廣告把時間拉回 0 → 凍結歌詞；只更新播放狀態
            _state.value = _state.value.copy(
                isPlaying = state == STATE_PLAYING,
                isBuffering = state == STATE_BUFFERING,
                ended = ended,
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
            ended = ended,
        )

        // ---- 換版本的位置校正（擁有者指定）----
        // 新影片時長比記錄的時間短 → 停在「時長 - 1 秒」，不要歸零。
        val resume = pendingResumeMs
        val duration = durationMs?.takeIf { it > 0 }
        if (resume != null && duration != null) {
            pendingResumeMs = null
            val target = if (resume > duration - RESUME_TAIL_MARGIN_MS) {
                (duration - RESUME_TAIL_MARGIN_MS).coerceAtLeast(0L)
            } else {
                resume
            }
            log("換版本接續：記錄 ${resume}ms、新時長 ${duration}ms → seek ${target}ms")
            seekTo(target)
        }

        if (tickLogs < MAX_TICK_LOGS) {
            tickLogs++
            log("tick #$tickLogs：getCurrentTime=${js("%.2f", sanitized / 1000.0)}s " +
                "duration=${js("%.1f", (durationMs ?: 0) / 1000.0)}s state=$state ended=$ended")
        }
    }

    private inner class Bridge {

        /** 找到 `<video>` 了：回報尺寸（給 Compose 算長寬比）並確保解除靜音。 */
        @JavascriptInterface
        fun onVideoFound(width: Int, height: Int) {
            val ratio = if (width > 0 && height > 0) {
                width.toFloat() / height.toFloat()
            } else {
                null
            }

            if (ratio != null && ratio != _state.value.videoAspectRatio) {
                log("影片尺寸 ${width}x$height → 比例 ${js("%.3f", ratio)}")
            }

            _state.value = _state.value.copy(
                isReady = true,
                videoAspectRatio = ratio ?: _state.value.videoAspectRatio,
            )

            // 解除靜音／自動播放只做一次。
            // 尺寸可能中途才確定（loadedmetadata → resize → 輪詢），
            // 每次都重跑會把使用者自己按下的暫停又推回播放。
            if (videoReadyHandled) return
            videoReadyHandled = true

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

        /** 樣式命中數量（每個選擇器抓到幾個元素）。 */
        @JavascriptInterface
        fun onCssHits(summary: String) {
            log("css hits: $summary")
        }

        /** video 的實際渲染狀況（黑屏排查）。 */
        @JavascriptInterface
        fun onDiagnostics(json: String) {
            log("diagnose: $json")
        }

        @JavascriptInterface
        fun onTick(
            positionSeconds: Double,
            durationSeconds: Double,
            state: Int,
            volume: Double,
            muted: Int,
            readyState: Int,
            ended: Int,
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
                ended = ended == 1,
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

        /**
         * YouTube 需要的「用戶端身分」（app 的 origin）。
         *
         * `loadUrl` 會帶 `Referer: https://lyrian.app/`，embed URL 也帶 `origin=`，
         * **兩者必須一致**——這是錯誤 153 的修法（A-1）。
         */
        const val APP_ORIGIN = "https://lyrian.app"

        /** 要藏起來的 YouTube 浮層（只列浮在 video 上層的面板）。 */
        val CSS_HIDE_SELECTORS = listOf(
            ".ytp-chrome-top",              // 上排控制列（標題、分享）
            ".ytp-chrome-bottom",           // 下排控制列（進度、音量、齒輪）
            ".ytp-large-play-button",       // 中央大播放鍵
            ".ytp-pause-overlay",           // 暫停時蓋在畫面中央的「更多影片」
            ".ytp-share-button",
            ".ytp-watermark",
            ".ytp-title",                   // 左上角「在 YouTube 中開啟」
            ".ytp-youtube-button",
            ".ytp-cued-thumbnail-overlay",  // 尚未播放時的封面 + 播放鍵
            ".ytp-cards-teaser",            // 右上角卡片提示
            ".ytp-ce-element",              // 資訊卡
            ".ytp-endscreen-content",       // 結束畫面推薦
            ".ytp-show-cards-title",
        )

        /**
         * 除錯開關：只保留 autoplay 監測、**完全不注入樣式**。
         *
         * 黑屏排查步驟（擁有者指定的第 6 點）：把它改成 true 重跑一次。
         * 有畫面 → 是某條 CSS 造成合成層異常，再逐條加回定位。
         */
        const val DEBUG_DISABLE_CSS = false

        /** 頁面載入完成後延遲多久再套樣式（播放器 DOM 這時才組得完）。 */
        const val CSS_INJECT_DELAY_MS = 300L

        /** 頁面載入完成後延遲多久回報渲染診斷。 */
        const val DIAGNOSE_DELAY_MS = 1_500L

        /**
         * 一般 Chrome 的 UA。刻意不含 `Version/4.0`（那是 WebView 的預設值）。
         *
         * 另外帶上 `wv` 標記會被 YouTube 當成受限的 WebView；
         * 這裡用一般 Mobile Chrome 的 UA，讓播放器給完整能力（A-3 的黑邊也少）。
         */
        const val DESKTOP_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36"

        /** 淡入淡出的總時長（擁有者指定 0.5 秒）。 */
        const val FADE_MS = 500L

        /** 分幾步調整音量；10 步 × 50ms 在聽感上是連續的，又不會把 JS 打爆。 */
        const val FADE_STEPS = 10

        /** 位置「倒退」多少毫秒以上才視為異常（廣告／重置）。 */
        const val REWIND_TOLERANCE_MS = 1_500L

        /** 換版本接續時，最多只能停在「新影片時長 - 這個值」。 */
        const val RESUME_TAIL_MARGIN_MS = 1_000L

        /** 位置回報只印前幾筆，避免 log 被灌爆。 */
        const val MAX_TICK_LOGS = 5

        // HTMLMediaElement 狀態（= 舊 IFrame API 的 YT.PlayerState）
        const val STATE_PLAYING = 1
        const val STATE_BUFFERING = 3
    }
}
