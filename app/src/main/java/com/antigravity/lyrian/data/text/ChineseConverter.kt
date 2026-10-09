package com.antigravity.lyrian.data.text

import android.content.Context
import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.zqc.opencc.android.lib.ConversionType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 中文簡體 → 繁體轉換（**台灣標準**）。
 *
 * **為什麼需要**：字典與歌詞來源常回傳簡體中文
 * （例如 ECDICT 的中文釋義、社群貢獻的歌詞），而本 App 的 UI 與使用者都是繁體。
 *
 * ## 兩個後端，各有分工
 *
 * | 後端 | 授權 | 用途 | 轉換設定 |
 * |---|---|---|---|
 * | [qichuan/android-opencc](https://github.com/qichuan/android-opencc) | MIT | **裝置上的正式路徑** | `s2twp` |
 * | [opencc4j](https://github.com/houbb/opencc4j) | Apache-2.0 | JVM 單元測試、原生程式庫載入失敗時的退路 | 字對字 `t2s`/`s2t` |
 *
 * ### 為什麼需要原生版
 *
 * opencc4j 是純 Java，只做**字對字**轉換，沒有台灣（TW）變體：
 * ```
 * 软件 → 軟件   （字形正確，但不是台灣用語，台灣要說「軟體」）
 * ```
 * 台灣用語的詞彙替換要 OpenCC 的 `s2twp` 設定檔，而 `s2twp` 的詞庫是
 * `TWPhrases.ocd2` + `STPhrases.ocd2`——opencc4j 未實作這條路徑。
 * android-opencc 是 OpenCC 的 JNI 封裝，帶完整字典，因此**只有它能給出台灣用語**。
 *
 * ### 為什麼還留著 opencc4j
 *
 * 1. **單元測試**：`libChineseConverter.so` 是 Android 原生程式庫，JVM 測試載不進來。
 *    沒有 opencc4j 的話，[ChineseConverterTest] 就得全部改成 instrumented 測試（需要裝置）。
 * 2. **安全退路**：見下方「載入失敗」。
 *
 * ## 載入失敗時的行為
 *
 * 原生路徑需要 [Context]（第一次呼叫時把 `assets/openccdata` 複製到 `filesDir`，
 * 約 1.1 MB）。若 [context] 為 null 或 `System.loadLibrary` 失敗
 * （不支援的 ABI、APK 被裁剪），**自動退回 opencc4j**，
 * 結果仍是正確的繁體字，只是少了台灣用語替換。**不會拋例外**。
 *
 * @param context 應用程式 Context；傳 null 表示強制走 opencc4j（單元測試用）
 */
@Singleton
class ChineseConverter @Inject constructor(
    @ApplicationContext private val context: Context?,
) {

    /**
     * 原生轉換器是否可用。
     *
     * `by lazy` 讓載入只嘗試一次——`System.loadLibrary` 失敗會拋
     * [UnsatisfiedLinkError]，那是 [Error] 不是 [Exception]，
     * 每次呼叫都重試既慢又沒意義。
     */
    private val nativeAvailable: Boolean by lazy {
        if (context == null) {
            false
        } else {
            runCatching { System.loadLibrary(NATIVE_LIBRARY) }
                .onFailure { android.util.Log.i(TAG, "原生 OpenCC 不可用，改用 opencc4j：" + it.message) }
                .isSuccess
        }
    }

    /**
     * 簡體轉繁體（**台灣標準**，`s2twp`）。
     *
     * @return 轉換後的字串；輸入為空時回傳空字串
     */
    fun toTraditional(text: String?): String {
        if (text.isNullOrBlank()) return text.orEmpty()

        if (nativeAvailable) {
            runCatching {
                com.zqc.opencc.android.lib.ChineseConverter.convert(
                    text,
                    ConversionType.S2TWP,
                    context,
                )
            }.onSuccess { converted ->
                if (converted != null) return converted
            }.onFailure {
                android.util.Log.w(TAG, "原生簡繁轉換失敗，退回 opencc4j", it)
            }
        }
        return toTraditionalJava(text)
    }

    /**
     * 繁體轉簡體。
     *
     * 目前沒有使用的地方，但保留給「使用者偏好簡體」的設定
     * （PRD 的「外觀與語言」有語言選項）。
     */
    fun toSimplified(text: String?): String {
        if (text.isNullOrBlank()) return text.orEmpty()

        if (nativeAvailable) {
            runCatching {
                com.zqc.opencc.android.lib.ChineseConverter.convert(
                    text,
                    ConversionType.T2S,
                    context,
                )
            }.onSuccess { converted ->
                if (converted != null) return converted
            }.onFailure {
                android.util.Log.w(TAG, "原生繁簡轉換失敗，退回 opencc4j", it)
            }
        }
        return toSimplifiedJava(text)
    }

    /**
     * 判斷是否含有簡體字。
     *
     * 用途：**避免對已經是繁體的內容做多餘轉換**。
     * 轉換本身會消耗 CPU 與記憶體，對乾淨的繁體字串是浪費。
     *
     * 做法：轉成繁體後若與原文相同，表示原本就是繁體（或無需轉換的字）。
     */
    fun containsSimplified(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return toTraditional(text) != text
    }

    /**
     * 只在需要時轉換。
     *
     * 這是**建議的呼叫方式**——先偵測再轉，避免對已是繁體的內容白做一次轉換。
     */
    fun toTraditionalIfNeeded(text: String?): String {
        if (text.isNullOrBlank()) return text.orEmpty()
        return if (containsSimplified(text)) toTraditional(text) else text
    }

    // ---------- opencc4j 退路（internal 以便單元測試直接驗證）----------

    /**
     * 純 Java 的簡→繁（字對字，**不含台灣用語**）。
     *
     * 這是 [toTraditional] 在原生路徑不可用時的實作，也是 JVM 單元測試
     * 唯一驗證得到的那一條路徑——見類別註解。
     */
    internal fun toTraditionalJava(text: String): String =
        runCatching { ZhConverterUtil.toTraditional(text) }
            .getOrElse {
                // 轉換失敗不該讓整個流程掛掉——原樣回傳是安全的退路
                android.util.Log.w(TAG, "opencc4j 簡繁轉換失敗，原樣回傳", it)
                text
            }

    /** 純 Java 的繁→簡。 */
    internal fun toSimplifiedJava(text: String): String =
        runCatching { ZhConverterUtil.toSimple(text) }
            .getOrElse {
                android.util.Log.w(TAG, "opencc4j 繁簡轉換失敗，原樣回傳", it)
                text
            }

    private companion object {
        const val TAG = "LyrianChineseConverter"
        const val NATIVE_LIBRARY = "ChineseConverter"
    }
}
