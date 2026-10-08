package com.antigravity.lyrian

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Lyrian 的 Application 進入點。
 *
 * Hilt 需要這個標註才能產生依賴圖的根。
 */
@HiltAndroidApp
class LyrianApplication : Application()
