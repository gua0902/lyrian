import { formatDuration, toDayKey } from '../time';

describe('formatDuration', () => {
  it('格式化為 mm:ss', () => {
    expect(formatDuration(296_000)).toBe('04:56');
    expect(formatDuration(0)).toBe('00:00');
    expect(formatDuration(59_000)).toBe('00:59');
    expect(formatDuration(60_000)).toBe('01:00');
  });

  it('無條件捨去小數秒', () => {
    expect(formatDuration(295_502)).toBe('04:55');
  });

  it('超過一小時時分鐘數繼續累加', () => {
    expect(formatDuration(3_725_000)).toBe('62:05');
  });

  it('無效值回傳 null', () => {
    expect(formatDuration(null)).toBeNull();
    expect(formatDuration(undefined)).toBeNull();
    expect(formatDuration(-1)).toBeNull();
    expect(formatDuration(Number.NaN)).toBeNull();
    expect(formatDuration(Number.POSITIVE_INFINITY)).toBeNull();
  });
});

describe('toDayKey', () => {
  it('格式化為 YYYY-MM-DD', () => {
    expect(toDayKey(new Date(2026, 9, 7))).toBe('2026-10-07');
  });

  it('月份與日期補零', () => {
    expect(toDayKey(new Date(2026, 0, 1))).toBe('2026-01-01');
    expect(toDayKey(new Date(2026, 11, 31))).toBe('2026-12-31');
  });

  it('使用本地時區而非 UTC（避免跨日誤差）', () => {
    // 本地時間的當天 00:00 與 23:59 必須是同一天
    const startOfDay = new Date(2026, 9, 7, 0, 0, 0);
    const endOfDay = new Date(2026, 9, 7, 23, 59, 59);

    expect(toDayKey(startOfDay)).toBe('2026-10-07');
    expect(toDayKey(endOfDay)).toBe('2026-10-07');
  });

  it('不帶參數時回傳今天的鍵', () => {
    expect(toDayKey()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});
