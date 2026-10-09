package com.antigravity.lyrian.data.text

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [ChineseConverter] **原生路徑**的 instrumented 測試（需要裝置／模擬器）。
 *
 * ## 為什麼一定要在裝置上跑
 *
 * 原生路徑要靠 `libChineseConverter.so` + `assets/openccdata` 字典，
 * 兩者都只存在於 Android 執行環境；JVM 單元測試（[ChineseConverterTest]）
 * 只能驗證 opencc4j 退路。
 *
 * **這裡驗證的是 native 專屬的能力**：`s2twp`（台灣標準 + 台灣用語）。
 * 退路永遠給不出「軟體」，只有原生路徑可以。
 *
 * ```bash
 * gradlew.bat :app:connectedDebugAndroidTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ChineseConverterInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val converter = ChineseConverter(context = context)

    @Test
    fun `原生路徑可用`() {
        // 若載入失敗，toTraditional 會靜靜退回 opencc4j，
        // 因此「台灣用語」就是原生路徑確實在跑的最佳證據。
        assertEquals("軟體", converter.toTraditional("软件"))
    }

    @Test
    fun `台灣用語替換`() {
        // s2twp = 台灣正體 + 台灣詞彙
        assertEquals("資訊", converter.toTraditional("信息"))
        assertEquals("滑鼠", converter.toTraditional("鼠标"))
    }

    @Test
    fun `字形仍然正確`() {
        assertEquals("頭髮", converter.toTraditional("头发"))
        assertEquals("學習音樂", converter.toTraditional("学习音乐"))
    }

    @Test
    fun `繁體輸入維持不變`() {
        val traditional = "學習音樂與歌詞"
        assertEquals(traditional, converter.toTraditional(traditional))
    }

    @Test
    fun `重複呼叫穩定`() {
        // 第一次呼叫會把 assets/openccdata 複製到 filesDir；
        // 之後應該走已存在的路徑，結果必須一致。
        repeat(3) {
            assertEquals("軟體", converter.toTraditional("软件"))
        }
    }

    @Test
    fun `字典資料夾已就緒`() {
        val folder = java.io.File(context.filesDir, "openccdata")
        assertTrue("openccdata 應已複製到 filesDir", folder.isDirectory)
    }
}
