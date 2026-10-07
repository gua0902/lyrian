/**
 * 單字庫。
 *
 * 以 **lemma 為單位去重**——同一個字的 `love` / `loved` / `loving` 不該在字庫裡各佔一筆。
 * 目前 lemma 由呼叫端提供（暫時等於正規化後的答案）；P1.5 接上 ECDICT 的 `exchange`
 * 欄位後會改用真正的詞形還原。
 */
import { withTransaction } from '../transaction';
import type { SqlDatabase } from '../types';

export interface AddVocabInput {
  lemma: string;
  surfaceForm: string | null;
  /** 中文釋義（ECDICT 尚未接上，目前為 null） */
  translation: string | null;
  contextLine: string | null;
  songId: number | null;
  now: number;
}

export interface VocabRow {
  id: number;
  lemma: string;
  surface_form: string | null;
  translation: string | null;
  context_line: string | null;
  first_song_id: number | null;
  error_count: number;
  correct_streak: number;
  status: string;
  created_at: number;
  updated_at: number;
}

/** 字庫清單項目，附上來源歌曲名稱（給參考圖的「Hello, Tomorrow」那行用）。 */
export interface VocabListItem extends VocabRow {
  song_title: string | null;
  song_artist: string | null;
}

export interface AddVocabResult {
  id: number;
  /** 這次呼叫是否**新建**了字庫項目（供每日「新單字」統計使用） */
  isNew: boolean;
}

/**
 * 把一個答錯的單字收進字庫。
 *
 * 已存在時：`error_count` +1、更新語境、狀態由 `mastered` 退回 `learning`
 * （答錯代表還沒真的學會），但**保留最初的 `first_song_id` 與 `created_at`**。
 * 同時確保有對應的 FSRS 排程列。
 */
export async function addVocabFromError(
  database: SqlDatabase,
  input: AddVocabInput
): Promise<AddVocabResult> {
  return withTransaction(database, async () => {
    const existing = await database.getFirstAsync<{ id: number }>(
      'SELECT id FROM vocab WHERE lemma = ?',
      input.lemma
    );

    const row = await database.getFirstAsync<{ id: number }>(
      `INSERT INTO vocab
         (lemma, surface_form, translation, first_song_id, context_line, error_count, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, 1, ?, ?)
       ON CONFLICT(lemma) DO UPDATE SET
         error_count  = vocab.error_count + 1,
         surface_form = excluded.surface_form,
         context_line = excluded.context_line,
         translation  = COALESCE(excluded.translation, vocab.translation),
         first_song_id = COALESCE(vocab.first_song_id, excluded.first_song_id),
         status = CASE WHEN vocab.status = 'mastered' THEN 'learning' ELSE vocab.status END,
         updated_at = excluded.updated_at
       RETURNING id`,
      input.lemma,
      input.surfaceForm,
      input.translation,
      input.songId,
      input.contextLine,
      input.now,
      input.now
    );

    if (!row) throw new Error('加入字庫失敗：沒有取得 id');

    await database.runAsync(
      'INSERT INTO vocab_schedule (vocab_id) VALUES (?) ON CONFLICT(vocab_id) DO NOTHING',
      row.id
    );

    return { id: row.id, isNew: existing === null };
  });
}

/** 取得字庫清單（新到舊），可依狀態篩選。 */
export async function listVocab(
  database: SqlDatabase,
  options: { status?: string; limit?: number } = {}
): Promise<VocabListItem[]> {
  const limit = options.limit ?? 100;

  if (options.status) {
    return database.getAllAsync<VocabListItem>(
      `SELECT v.*, s.title AS song_title, s.artist AS song_artist
         FROM vocab v LEFT JOIN song s ON s.id = v.first_song_id
        WHERE v.status = ?
        ORDER BY v.updated_at DESC
        LIMIT ?`,
      options.status,
      limit
    );
  }

  return database.getAllAsync<VocabListItem>(
    `SELECT v.*, s.title AS song_title, s.artist AS song_artist
       FROM vocab v LEFT JOIN song s ON s.id = v.first_song_id
      ORDER BY v.updated_at DESC
      LIMIT ?`,
    limit
  );
}

export interface VocabSummary {
  total: number;
  learning: number;
  reviewing: number;
  mastered: number;
}

/** 字庫統計（給「我的字庫」頁的橫幅與分段控制器）。 */
export async function getVocabSummary(database: SqlDatabase): Promise<VocabSummary> {
  const row = await database.getFirstAsync<{
    total: number;
    learning: number;
    reviewing: number;
    mastered: number;
  }>(
    `SELECT
       COUNT(*) AS total,
       COALESCE(SUM(status = 'learning'), 0)  AS learning,
       COALESCE(SUM(status = 'reviewing'), 0) AS reviewing,
       COALESCE(SUM(status = 'mastered'), 0)  AS mastered
     FROM vocab`
  );

  return {
    total: row?.total ?? 0,
    learning: row?.learning ?? 0,
    reviewing: row?.reviewing ?? 0,
    mastered: row?.mastered ?? 0,
  };
}
