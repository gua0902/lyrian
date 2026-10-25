package com.antigravity.lyrian.util

import java.util.Locale

/**
 * 位元組 → 人類可讀的大小（`1.2 MB`）。
 *
 * 用 1024 進位（Android 的 `Formatter.formatFileSize` 也是）而且**只留一位小數**：
 * 設定頁不需要「1.23 MB」這種精度，那只會讓數字更難掃讀。
 *
 * 純函式，可在 JVM 上單元測試（`SizesTest`）。
 */
object Sizes {

    private const val KB = 1024.0
    private const val MB = KB * 1024
    private const val GB = MB * 1024

    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "—"
        return when {
            bytes < KB -> "$bytes B"
            bytes < MB -> String.format(Locale.US, "%.0f KB", bytes / KB)
            bytes < GB -> String.format(Locale.US, "%.1f MB", bytes / MB)
            else -> String.format(Locale.US, "%.1f GB", bytes / GB)
        }
    }
}
