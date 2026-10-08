package com.antigravity.lyrian.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轉成 `YYYY-MM-DD`（**本地時區**）。
 *
 * `daily_stat.day` 的主鍵，也是「連續學習天數」的計算基礎。
 * 刻意不用 `toISOString()` 那類 UTC 轉換——那會導致跨時區時日期跑掉。
 */
fun toDayKey(date: Date = Date()): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
