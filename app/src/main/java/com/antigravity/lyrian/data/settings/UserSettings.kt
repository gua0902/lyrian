package com.antigravity.lyrian.data.settings

import android.content.Context
import com.antigravity.lyrian.domain.model.CefrLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 使用者的學習設定。
 *
 * 目前只有一項：**挖空的最低 CEFR 等級**（擁有者指定，預設 B1，選項 A1–B2）。
 *
 * ## 為什麼用 SharedPreferences 而不是 DataStore
 *
 * 這是一個單一旗標，而且要在 ViewModel 的建構期就**同步**讀到值——練習頁的
 * 第一次組題不能等是非同步的 Flow。DataStore 的 API 從頭到尾都是 suspend/Flow，
 * 為了這一個值反而要多一層。`MainActivity` 的 onboarding 旗標也已經用同一個檔案。
 */
@Singleton
class UserSettings @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _minLevel = MutableStateFlow(
        CefrLevel.fromLabel(prefs.getString(KEY_MIN_LEVEL, null))?.takeIf {
            it in CefrLevel.SELECTABLE
        } ?: CefrLevel.DEFAULT,
    )

    /** 挖空的最低等級。設定頁改了之後，下一次組題立即生效。 */
    val minLevel: StateFlow<CefrLevel> = _minLevel.asStateFlow()

    /** 目前值；給不能收集 Flow 的同步路徑用。 */
    val minLevelValue: CefrLevel get() = _minLevel.value

    fun setMinLevel(level: CefrLevel) {
        if (level == _minLevel.value) return
        _minLevel.value = level
        prefs.edit().putString(KEY_MIN_LEVEL, level.label).apply()
    }

    private companion object {
        const val PREFS_NAME = "lyrian_prefs"
        const val KEY_MIN_LEVEL = "min_cefr_level"
    }
}
