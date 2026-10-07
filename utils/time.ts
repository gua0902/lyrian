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
