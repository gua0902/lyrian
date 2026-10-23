package com.antigravity.lyrian.data.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient

/**
 * 播放狀態。
 *
 * @property isPlaying 播放器是否正在播放
 * @property positionMs 目前播放位置；未載入時為 0
 * @property durationMs 總長；未知時為 null
 * @property isBuffering 正在緩衝（顯示轉圈）
 * @property error 播放失敗的原因；null 表示正常
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val isBuffering: Boolean = false,
    val error: String? = null,
)

/**
 * 音訊播放器（Media3 / ExoPlayer）。
 *
 * ## 為什麼是 `@Singleton` 而不是綁在 ViewModel 上
 *
 * 播放器持有原生解碼器與網路連線，建立成本高。綁在 ViewModel 上會在每次
 * 旋轉螢幕時重建一次，造成**音樂中斷**。放在單例並由 Activity 的生命週期驅動，
 * 旋轉時音樂不中斷——這正是擁有者要的「播放鍵同時控制播放器與歌詞滾動」的前提。
 *
 * ## 為什麼**沒有**用 MediaSessionService（背景播放）
 *
 * Media3 的背景播放需要 `MediaSessionService` + 前景服務通知，
 * 而本 App 的練習是**強互動**的：使用者要看著歌詞作答。
 * 切到別的 App 時暫停播放是合理的，也省下一個常駐通知。
 * 若日後要做背景播放，再補 `media3-session` 的 service（依賴已加）。
 */
@Singleton
class AudioPlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {

    private var player: ExoPlayer? = null

    /**
     * 目前載入的串流 URL。
     *
     * 用來讓 [prepare] 具**冪等性**——重複準備同一個 URL 不該重新載入，
     * 否則每次回到練習頁都會中斷播放並重新緩衝。
     */
    private var currentUrl: String? = null

    private val _state = MutableStateFlow(PlaybackState())

    /** 對外公開的播放狀態。 */
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** 播放器事件（給需要即時反應的 UI 用）。 */
    val events: Flow<PlayerEvent> get() = callbackFlow {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                trySend(PlayerEvent.IsPlayingChanged(isPlaying))
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                trySend(PlayerEvent.PlaybackStateChanged(playbackState))
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                trySend(PlayerEvent.Error(error.message ?: "播放失敗"))
            }
        }

        // 播放器可能還沒建立（使用者尚未按下播放）
        player?.addListener(listener)

        awaitClose { player?.removeListener(listener) }
    }

    /** 播放器目前是否已載入媒體。 */
    val hasMedia: Boolean get() = (player?.mediaItemCount ?: 0) > 0

    /**
     * 目前播放位置（毫秒）。
     *
     * Media3 刻意**不**提供位置變化的 callback（位置是連續值，每帧回報太吵），
     * 所以呼叫端要自己以固定間隔取樣。
     */
    fun currentPositionMs(): Long = player?.currentPosition?.coerceAtLeast(0L) ?: 0L

    /**
     * 載入一個音訊串流。**不會自動播放**。
     *
     * 具冪等性：同一個 URL 重複呼叫不會重新載入（見 [currentUrl]）。
     *
     * @param url 直接的音訊串流 URL（來自 [YouTubeMusicSource.getStreamUrl]）
     */
    fun prepare(url: String) {
        val exo = obtainPlayer()
        if (currentUrl == url && exo.mediaItemCount > 0) return

        currentUrl = url
        _state.value = PlaybackState(isBuffering = true, error = null)
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
    }

    fun play() {
        player?.playWhenReady = true
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    /**
     * 音量淡出後暫停。
     *
     * 擁有者指定：答案是「錯過了就暫停」，但硬切會讓人以為 App 掛掉，
     * 所以先 0.3 秒把音量收到 0 再 `pause()`。
     *
     * 暫停後音量會**還原成 1**——這樣下一次 [fadeInAndPlay] 從 0 開始淡入時
     * 不會被上一次的殘留音量影響；而暫停期間本來就沒有聲音，還原無副作用。
     */
    suspend fun fadeOutAndPause(durationMs: Long = DEFAULT_FADE_MS) {
        val exo = player ?: return
        rampVolume(exo, from = exo.volume, to = 0f, durationMs = durationMs)
        exo.pause()
        exo.volume = 1f
    }

    /**
     * 從目前位置淡入並播放。
     *
     * ⚠️ **不重頭播**：只呼叫 `play()`，不做 `seekTo(0)`——
     * 擁有者明確要求「從暫停時的位置恢復播放」。
     */
    suspend fun fadeInAndPlay(durationMs: Long = DEFAULT_FADE_MS) {
        val exo = player ?: return
        exo.volume = 0f
        exo.playWhenReady = true
        exo.play()
        rampVolume(exo, from = 0f, to = 1f, durationMs = durationMs)
    }

    /** 線性調整音量。步進式而非一次性設定，才能真的「淡」出來。 */
    private suspend fun rampVolume(exo: ExoPlayer, from: Float, to: Float, durationMs: Long) {
        for (step in 0..FADE_STEPS) {
            // 播放器可能在淡入淡出途中被釋放（離開畫面）
            if (player !== exo) return
            val fraction = step.toFloat() / FADE_STEPS
            exo.volume = from + (to - from) * fraction
            delay((durationMs / FADE_STEPS).coerceAtLeast(1L))
        }
    }

    /** 播放／暫停切換。回傳切換後的狀態。 */
    fun togglePlayPause() {
        val exo = player ?: return
        if (exo.isPlaying) pause() else play()
    }

    /** 跳到指定位置（毫秒）。 */
    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0L))
    }

    /** 停止並清除目前媒體。 */
    fun stop() {
        player?.stop()
        currentUrl = null
        _state.value = PlaybackState()
    }

    /** 釋放原生資源。由擁有者依生命週期呼叫（見 `PracticeViewModel.onCleared`）。 */
    fun release() {
        player?.release()
        player = null
        currentUrl = null
        _state.value = PlaybackState()
    }

    /**
     * 取得（必要時建立）播放器。
     *
     * 用 OkHttp 當資料來源：串流 URL 需要自訂 header，
     * 而且能共用 App 既有的 [OkHttpClient]（連線池、逾時設定）。
     */
    private fun obtainPlayer(): ExoPlayer {
        player?.let { return it }

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)

        val exo = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _state.value = _state.value.copy(isBuffering = true)
                    }
                    Player.STATE_READY -> {
                        _state.value = _state.value.copy(
                            isBuffering = false,
                            durationMs = exo.duration.takeIf { it != C.TIME_UNSET },
                        )
                    }
                    Player.STATE_ENDED -> {
                        _state.value = _state.value.copy(
                            isPlaying = false,
                            isBuffering = false,
                        )
                    }
                    else -> {
                        _state.value = _state.value.copy(isBuffering = false)
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _state.value = _state.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    error = error.message ?: "播放失敗",
                )
            }
        })

        player = exo
        return exo
    }

    /** 播放器事件。 */
    sealed interface PlayerEvent {
        data class IsPlayingChanged(val isPlaying: Boolean) : PlayerEvent
        data class PlaybackStateChanged(val playbackState: Int) : PlayerEvent
        data class Error(val message: String) : PlayerEvent
    }

    private companion object {
        /** 擁有者指定：淡出／淡入各 0.3 秒。 */
        const val DEFAULT_FADE_MS = 300L

        /** 淡入淡出的步數。20 步 ≈ 每 15ms 一格，聽起來是連續的。 */
        const val FADE_STEPS = 20
    }
}
