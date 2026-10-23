package com.antigravity.lyrian.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 單字朗讀，用**系統 TTS**。
 *
 * 擁有者指定「發音用系統 TTS 朗讀，不額外下載音檔」——所以這裡只用
 * `TextToSpeech` 引擎，不打包任何音檔。
 *
 * ## 為什麼是 `@Singleton` 而且延後初始化
 *
 * `TextToSpeech` 的建構要綁定引擎服務，第一次要幾百毫秒。若在畫面建立時就初始化，
 * 每次進單字頁都會頓一下。因此：
 *  - 第一次真的按下朗讀才初始化
 *  - 之後整個 App 共用同一個引擎
 *
 * 初始化失敗（裝置沒有 TTS 引擎）時**靜默失敗**，不讓使用者看到崩潰。
 */
@Singleton
class Speaker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var engine: TextToSpeech? = null
    private var ready = false

    /** 朗讀一個英文字。 */
    fun speak(text: String) {
        val stripped = text.trim()
        if (stripped.isEmpty()) return

        val current = engine
        if (current != null && ready) {
            current.speak(stripped, TextToSpeech.QUEUE_FLUSH, null, stripped)
            return
        }

        // 引擎還沒好（第一次，或正在初始化中）→ 初始化後再唸
        engine = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                android.util.Log.w(TAG, "系統沒有可用的 TTS 引擎")
                return@TextToSpeech
            }
            engine?.language = Locale.US
            ready = true
            engine?.speak(stripped, TextToSpeech.QUEUE_FLUSH, null, stripped)
        }
    }

    /** 釋放引擎。目前只在程式結束時由系統回收，不主動呼叫。 */
    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
    }

    private companion object {
        const val TAG = "LyrianSpeaker"
    }
}
