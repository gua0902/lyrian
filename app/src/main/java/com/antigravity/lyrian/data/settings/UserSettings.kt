package com.antigravity.lyrian.data.settings

import android.content.Context
import com.antigravity.lyrian.domain.practice.BlankCountPolicy
import com.antigravity.lyrian.domain.practice.BlankDifficulty
import com.antigravity.lyrian.domain.practice.BlankSelector
import com.antigravity.lyrian.domain.practice.FillChoiceBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 使用者的學習設定。
 *
 * 兩項，都只影響**選詞**、不影響播放：
 *  - [difficulty] 挖空難度（基礎／進階／挑戰）——決定「哪些字值得挖」
 *  - [maxBlanks] 題數上限（滑桿 20–40）——實際題數由 [BlankCountPolicy] 決定
 *
 * ## 題數不是直接等於滑桿值
 *
 * 擁有者 2026-10 核定「保量」：每首歌至少 20 題，句子多時按 0.6 倍拉高，
 * 但不會超過這裡的上限。因此滑桿是**上限**，不是目標。
 *
 * ## 為什麼用 SharedPreferences 而不是 DataStore
 *
 * 這兩個值要在 ViewModel 的建構期就**同步**讀到——練習頁的第一次組題
 * 不能等非同步的 Flow。DataStore 的 API 從頭到尾都是 suspend/Flow，
 * 為了兩個值反而要多一層。`MainActivity` 的 onboarding 旗標也已經用同一個檔案。
 */
@Singleton
class UserSettings @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _difficulty = MutableStateFlow(readDifficulty())
    private val _maxBlanks = MutableStateFlow(readMaxBlanks())

    /** 挖空難度。預設「進階優先」（B1 及以上）。 */
    val difficulty: StateFlow<BlankDifficulty> = _difficulty.asStateFlow()

    val difficultyValue: BlankDifficulty get() = _difficulty.value

    /** 題數上限。 */
    val maxBlanks: StateFlow<Int> = _maxBlanks.asStateFlow()

    val maxBlanksValue: Int get() = _maxBlanks.value

    fun setDifficulty(value: BlankDifficulty) {
        if (value == _difficulty.value) return
        _difficulty.value = value
        prefs.edit().putString(KEY_DIFFICULTY, value.name).apply()
    }

    fun setMaxBlanks(value: Int) {
        val clamped = value.coerceIn(MIN_BLANKS, BlankSelector.MAX_BLANKS)
        if (clamped == _maxBlanks.value) return
        _maxBlanks.value = clamped
        prefs.edit().putInt(KEY_MAX_BLANKS, clamped).apply()
    }

    private fun readDifficulty(): BlankDifficulty {
        prefs.getString(KEY_DIFFICULTY, null)?.let { return BlankDifficulty.fromName(it) }

        // 舊版存的是 CEFR 等級（A1–B2）；這裡做一次無痛轉移，
        // 否則更新後使用者的設定會悄悄回到預設值。
        return when (prefs.getString(KEY_LEGACY_MIN_LEVEL, null)?.uppercase()) {
            "A1", "A2" -> BlankDifficulty.BASIC
            "B2" -> BlankDifficulty.CHALLENGE
            "B1" -> BlankDifficulty.ADVANCED
            else -> BlankDifficulty.DEFAULT
        }
    }

    private fun readMaxBlanks(): Int =
        prefs.getInt(KEY_MAX_BLANKS, FillChoiceBuilder.DEFAULT_MAX_BLANKS)
            .coerceIn(MIN_BLANKS, BlankSelector.MAX_BLANKS)

    companion object {
        /**
         * 滑桿下限＝保量下限（20）。
         *
         * 舊版是 5；2026-10 的保量政策把「最少幾題」拉高到 20，
         * 滑桿若還允許 5，使用者會以為可以要求 5 題，但實際永遠出 20 題。
         */
        const val MIN_BLANKS = BlankCountPolicy.MIN_BLANKS

        private const val PREFS_NAME = "lyrian_prefs"
        private const val KEY_DIFFICULTY = "blank_difficulty"
        private const val KEY_MAX_BLANKS = "blank_max"
        private const val KEY_LEGACY_MIN_LEVEL = "min_cefr_level"
    }
}
