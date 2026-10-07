/**
 * 作答紀錄。
 *
 * `attempt` 是所有練習模式的**唯一收斂點**（docs/ARCHITECTURE.md §3）——
 * 字庫、分數、統計都從這裡衍生。
 */
import type { PracticeModeId } from '@/features/practice';

import type { SqlDatabase } from '../types';

export interface RecordAttemptInput {
  modeId: PracticeModeId;
  songId: number | null;
  lineIndex: number | null;
  vocabId: number | null;
  prompt: string;
  userAnswer: string;
  correctAnswer: string;
  score: number;
  isCorrect: boolean;
  durationMs: number | null;
  now: number;
}

export interface AttemptRow {
  id: number;
  mode_id: string;
  song_id: number | null;
  vocab_id: number | null;
  prompt: string | null;
  user_answer: string | null;
  correct_answer: string | null;
  score: number | null;
  is_correct: number;
  duration_ms: number | null;
  created_at: number;
}

/** 寫入一筆作答紀錄，回傳 `attempt.id`。 */
export async function recordAttempt(
  database: SqlDatabase,
  input: RecordAttemptInput
): Promise<number> {
  const result = await database.runAsync(
    `INSERT INTO attempt
       (mode_id, song_id, line_index, vocab_id, prompt, user_answer, correct_answer, score, is_correct, duration_ms, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    input.modeId,
    input.songId,
    input.lineIndex,
    input.vocabId,
    input.prompt,
    input.userAnswer,
    input.correctAnswer,
    input.score,
    // SqlParam 不含 boolean，轉成 0/1
    input.isCorrect ? 1 : 0,
    input.durationMs,
    input.now
  );

  return result.lastInsertRowId;
}

/** 取最近的作答紀錄（新到舊）。 */
export async function listRecentAttempts(
  database: SqlDatabase,
  limit = 50
): Promise<AttemptRow[]> {
  return database.getAllAsync<AttemptRow>(
    'SELECT * FROM attempt ORDER BY created_at DESC, id DESC LIMIT ?',
    limit
  );
}

/** 計算某首歌的正確率（供「我的」統計使用）。 */
export async function getAttemptStats(
  database: SqlDatabase
): Promise<{ total: number; correct: number }> {
  const row = await database.getFirstAsync<{ total: number; correct: number }>(
    'SELECT COUNT(*) AS total, COALESCE(SUM(is_correct), 0) AS correct FROM attempt'
  );

  return { total: row?.total ?? 0, correct: row?.correct ?? 0 };
}
