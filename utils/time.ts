/**
 * 時間格式化工具。
 */

/**
 * 把毫秒格式化為 `mm:ss`；無法使用時回傳 null（讓呼叫端決定要不要顯示）。
 *
 * @example
 * formatDuration(296_000)  // '04:56'
 * formatDuration(null)     // null
 */
export function formatDuration(ms: number | null | undefined): string | null {
  if (ms === null || ms === undefined || !Number.isFinite(ms) || ms < 0) return null;

  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;

  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

/**
 * 轉成 `YYYY-MM-DD`（**本地時區**）。
 *
 * `daily_stat.day` 的主鍵，也是「連續學習天數」的計算基礎。
 * 刻意不用 `toISOString()`——那會轉成 UTC，導致跨時區時日期跑掉。
 *
 * @example
 * toDayKey(new Date(2026, 9, 7))  // '2026-10-07'
 */
export function toDayKey(date: Date = new Date()): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
