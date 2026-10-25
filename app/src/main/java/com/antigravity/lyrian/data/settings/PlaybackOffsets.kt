package com.antigravity.lyrian.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 每首歌的歌詞偏移量（毫秒）。
 *
 * ## 為什麼需要
 *
 * 歌詞時間戳來自 LRCLIB，音源來自 YouTube——**兩個不同的來源**。
 * 即使是同一首歌，官方上傳版本的開頭靜音長度也常常和歌詞檔差半秒到兩秒。
 * 使用者可以自己按 ±0.5 秒微調，調完**存在本地**，下次進同一首歌仍然有效。
 *
 * ## 正負號
 *
 * `offset > 0` 代表**歌詞延後**（歌聲比歌詞早），`offset < 0` 代表歌詞提早。
 * 計算方式：`歌詞位置 = 播放位置 + offset`。
 */
@Singleton
class PlaybackOffsets @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 讀取某首歌的偏移；沒調過就是 0。 */
    fun offsetMs(songId: Long): Long =
        prefs.getLong(key(songId), 0L).coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)

    /**
     * 這首歌是否**明確調過**偏移。
     *
     * 與 [offsetMs] 的差別：沒調過（回傳 false）時才應該套用「預設偏移」；
     * 使用者按過「歸零」也算調過（值是 0，但那是他的選擇）。
     */
    fun hasOffset(songId: Long): Boolean = prefs.contains(key(songId))

    /** 目前存了幾首歌的自訂偏移。 */
    fun savedCount(): Int = prefs.all.keys.count { it.startsWith(KEY_PREFIX) }

    /** 清掉所有歌曲的偏移（設定頁的「重設」）。 */
    fun clearAll() {
        val keys = prefs.all.keys.filter { it.startsWith(KEY_PREFIX) }
        if (keys.isEmpty()) return
        prefs.edit().apply {
            keys.forEach { remove(it) }
        }.apply()
    }

    /**
     * 設定偏移。會夾在 ±[MAX_OFFSET_MS] 之內——超過這個範圍代表歌詞根本配錯歌，
     * 不是「微調」能救的。
     */
    fun setOffsetMs(songId: Long, value: Long) {
        prefs.edit()
            .putLong(key(songId), value.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS))
            .apply()
    }

    private fun key(songId: Long) = "$KEY_PREFIX$songId"

    companion object {
        /** 與其他設定共用同一個 prefs 檔（`MainActivity` 的 onboarding 旗標也在這裡）。 */
        private const val PREFS_NAME = "lyrian_prefs"

        /** 每首歌的鍵前綴；[clearAll] 靠它辨識哪些鍵是偏移。 */
        private const val KEY_PREFIX = "lyric_offset_"

        /** 微調的級距：0.5 秒（擁有者指定）。 */
        const val STEP_MS = 500L

        /** 可調範圍 ±5 秒。 */
        const val MAX_OFFSET_MS = 5_000L
    }
}
