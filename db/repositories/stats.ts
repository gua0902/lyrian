/**
 * 每日統計（「我的」頁的累積總分、學習天數）。
 */
import type { SqlDatabase } from '../types';

export interface DailyStatDelta {
  /** `YYYY-MM-DD`（本地時區），見 `utils/time.ts` 的 `toDayKey` */
  day: string;
  score?: number;
  attempts?: number;
  correct?: number;
  newWords?: number;
}

/** 累加當日統計；該日尚無紀錄時建立一列。 */
export async function bumpDailyStat(
  database: SqlDatabase,
  delta: DailyStatDelta
): Promise<void> {
  await database.runAsync(
    `INSERT INTO daily_stat (day, score, attempts, correct, new_words)
     VALUES (?, ?, ?, ?, ?)
     ON CONFLICT(day) DO UPDATE SET
       score     = daily_stat.score + excluded.score,
       attempts  = daily_stat.attempts + excluded.attempts,
       correct   = daily_stat.correct + excluded.correct,
       new_words = daily_stat.new_words + excluded.new_words`,
    delta.day,
    delta.score ?? 0,
    delta.attempts ?? 0,
    delta.correct ?? 0,
    delta.newWords ?? 0
  );
}

export interface DailyStatRow {
  day: string;
  score: number;
  attempts: number;
  correct: number;
  new_words: number;
}

/** 取得一日的統計。 */
export async function getDailyStat(
  database: SqlDatabase,
  day: string
): Promise<DailyStatRow | null> {
  return database.getFirstAsync<DailyStatRow>('SELECT * FROM daily_stat WHERE day = ?', day);
}

/** 累積總分（「我的」頁的「累積總分」）。 */
export async function getTotalScore(database: SqlDatabase): Promise<number> {
  const row = await database.getFirstAsync<{ total: number }>(
    'SELECT COALESCE(SUM(score), 0) AS total FROM daily_stat'
  );
  return row?.total ?? 0;
}

/**
 * 連續學習天數。
 *
 * 從今天（或最近有紀錄的那天）往回數，直到遇到中斷的一天。
 * 若今天還沒有紀錄，會從昨天開始算，避免「今天還沒練」就把連續天數歸零。
 */
export async function getStudyStreak(
  database: SqlDatabase,
  today: string
): Promise<number> {
  const rows = await database.getAllAsync<{ day: string }>(
    'SELECT day FROM daily_stat ORDER BY day DESC'
  );
  if (rows.length === 0) return 0;

  const days = new Set(rows.map((row) => row.day));
  const cursor = new Date(`${today}T00:00:00`);

  // 今天沒有紀錄時，允許從昨天開始算
  if (!days.has(toIsoDay(cursor))) {
    cursor.setDate(cursor.getDate() - 1);
  }

  let streak = 0;
  while (days.has(toIsoDay(cursor))) {
    streak += 1;
    cursor.setDate(cursor.getDate() - 1);
  }
  return streak;
}

function toIsoDay(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
