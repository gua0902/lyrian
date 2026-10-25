package com.antigravity.lyrian.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 播放設定：**自動播放**、**音量淡入淡出**、**預設歌詞偏移**。
 *
 * ## 三個值分別接到哪裡
 *
 * | 設定 | 接到 |
 * |---|---|
 * | [autoPlay] | `PracticeViewModel.prepareAudio()` → `playCandidate(autoPlay = …)`；關掉時連三點都不顯示 |
 * | [fadeAudio] | `startFadeIn()` 與交界暫停的 `fadeOut()`；關掉時直接播放／直接停 |
 * | [defaultOffsetMs] | 進歌時的歌詞偏移（**每首歌調過就以那首歌為準**，見 [offsetFor]） |
 *
 * ## 為什麼預設偏移要跟每首歌的偏移分開
 *
 * 偏移的成因是「這個音源的開頭靜音比歌詞檔長」——**每個音源都不同**。
 * 所以正確的模型是：預設值只是新歌的起點，使用者一旦在某一首調過，
 * 那一首就永遠用自己的值（`PlaybackOffsets`），預設值改動不會蓋掉它。
 */
@Singleton
class PlaybackSettings @Inject constructor(
    @ApplicationContext context: Context,
    private val offsets: PlaybackOffsets,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _autoPlay = MutableStateFlow(prefs.getBoolean(KEY_AUTO_PLAY, true))
    private val _fadeAudio = MutableStateFlow(prefs.getBoolean(KEY_FADE, true))
    private val _defaultOffsetMs = MutableStateFlow(
        prefs.getLong(KEY_DEFAULT_OFFSET, 0L).coerceIn(-MAX_DEFAULT_OFFSET_MS, MAX_DEFAULT_OFFSET_MS),
    )

    /** 進入練習頁就自動開始播放。預設開啟（擁有者要求「進頁即載入」）。 */
    val autoPlay: StateFlow<Boolean> = _autoPlay.asStateFlow()

    val autoPlayValue: Boolean get() = _autoPlay.value

    /** 播放／暫停時做 0.5 秒的音量淡入淡出。預設開啟。 */
    val fadeAudio: StateFlow<Boolean> = _fadeAudio.asStateFlow()

    val fadeAudioValue: Boolean get() = _fadeAudio.value

    /** 新歌的歌詞偏移預設值（毫秒）。 */
    val defaultOffsetMs: StateFlow<Long> = _defaultOffsetMs.asStateFlow()

    val defaultOffsetMsValue: Long get() = _defaultOffsetMs.value

    fun setAutoPlay(enabled: Boolean) {
        if (enabled == _autoPlay.value) return
        _autoPlay.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_PLAY, enabled).apply()
    }

    fun setFadeAudio(enabled: Boolean) {
        if (enabled == _fadeAudio.value) return
        _fadeAudio.value = enabled
        prefs.edit().putBoolean(KEY_FADE, enabled).apply()
    }

    fun setDefaultOffsetMs(value: Long) {
        val clamped = value.coerceIn(-MAX_DEFAULT_OFFSET_MS, MAX_DEFAULT_OFFSET_MS)
        if (clamped == _defaultOffsetMs.value) return
        _defaultOffsetMs.value = clamped
        prefs.edit().putLong(KEY_DEFAULT_OFFSET, clamped).apply()
    }

    /**
     * 這首歌實際要用的偏移。
     *
     * 每首歌調過 → 用那一首的；沒調過 → 用預設值。
     */
    fun offsetFor(songId: Long): Long =
        if (offsets.hasOffset(songId)) offsets.offsetMs(songId) else defaultOffsetMsValue

    /** 目前有幾首歌存了自訂偏移（設定頁的「重設」按鈕要用）。 */
    fun customOffsetCount(): Int = offsets.savedCount()

    /** 清掉所有歌曲的自訂偏移；預設值不動。 */
    fun clearCustomOffsets() = offsets.clearAll()

    companion object {
        private const val PREFS_NAME = "lyrian_prefs"
        private const val KEY_AUTO_PLAY = "playback_autoplay"
        private const val KEY_FADE = "playback_fade"
        private const val KEY_DEFAULT_OFFSET = "playback_default_offset"

        /** 預設偏移可調範圍 ±2 秒。超過就代表歌詞配錯歌，不是「微調」。 */
        const val MAX_DEFAULT_OFFSET_MS = 2_000L
    }
}
