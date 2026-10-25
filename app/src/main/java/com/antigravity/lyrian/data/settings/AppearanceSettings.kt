package com.antigravity.lyrian.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 外觀設定：**主題模式** 與 **動態配色**。
 *
 * 兩者都只影響 `LyrianTheme` 的參數，不碰任何資料：
 *
 * ```
 * LyrianTheme(darkTheme = themeMode.resolve(isSystemInDarkTheme()), dynamicColor = …)
 * ```
 *
 * ## 為什麼主題模式要自己存
 *
 * Android 10 以後系統有「深色模式」，但使用者常常想讓**這個 App** 固定深色，
 * 而系統維持淺色（例如睡前聽歌）。M3 的建議是提供三選一：
 * 跟隨系統／一律淺色／一律深色——而不是只有一個「深色模式」開關。
 *
 * ## 動態配色的限制
 *
 * 只有 Android 12（API 31）以上才有效。舊版裝置仍然可以切換這個值，
 * 但 `LyrianTheme` 會忽略它（退回單色系 fallback）。UI 端會依 `SDK_INT`
 * 顯示「此裝置不支援」，**不會假裝有效**。
 */
@Singleton
class AppearanceSettings @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readThemeMode())
    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, true))

    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    val themeModeValue: ThemeMode get() = _themeMode.value

    /** 動態配色（Material You）。預設開啟。 */
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    val dynamicColorValue: Boolean get() = _dynamicColor.value

    fun setThemeMode(value: ThemeMode) {
        if (value == _themeMode.value) return
        _themeMode.value = value
        prefs.edit().putString(KEY_THEME_MODE, value.key).apply()
    }

    fun setDynamicColor(enabled: Boolean) {
        if (enabled == _dynamicColor.value) return
        _dynamicColor.value = enabled
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    private fun readThemeMode(): ThemeMode {
        val stored = ThemeMode.fromKey(prefs.getString(KEY_THEME_MODE, null))
        // 舊版只有系統預設；沒有存過就維持跟隨系統，不猜使用者的偏好。
        return stored
    }

    companion object {
        private const val PREFS_NAME = "lyrian_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    }
}

/**
 * 主題模式。
 *
 * `key` 是寫進 SharedPreferences 的值——**不要用 `name`**（列舉重新命名會讓
 * 使用者的設定默默失效）。
 */
enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}
