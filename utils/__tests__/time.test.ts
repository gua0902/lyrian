import { formatDuration } from '../time';

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
