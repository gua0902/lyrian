/**
 * Schema 的真實 SQL 驗證。
 *
 * 這裡不 mock 任何東西——直接用 node:sqlite 開一個真的資料庫把 schema 建起來，
 * 確認欄位、約束、預設值與 FK 串聯刪除都照預期運作。
 */
import { migrate } from '../migrate';
import { CREATE_TABLES_SQL, SCHEMA_VERSION } from '../schema';
import { createNodeDatabase, type NodeSqlDatabase } from '../testing/createNodeDatabase';

const EXPECTED_TABLES = [
  'attempt',
  'daily_stat',
  'lyric_line',
  'provider_config',
  'song',
  'vocab',
  'vocab_schedule',
];

let db: NodeSqlDatabase;

beforeEach(() => {
  db = createNodeDatabase();
});

afterEach(() => {
  db.close();
});

async function applySchema(database: NodeSqlDatabase): Promise<void> {
  await database.execAsync(CREATE_TABLES_SQL);
}

async function insertSong(database: NodeSqlDatabase, lrclibId = 1): Promise<number> {
  const result = await database.runAsync(
    `INSERT INTO song (lrclib_id, title, artist, duration_ms, created_at)
     VALUES (?, ?, ?, ?, ?)`,
    lrclibId,
    'Hello',
    'Adele',
    296_000,
    Date.now()
  );
  return result.lastInsertRowId;
}

describe('schema', () => {
  it('可以乾淨地建立所有資料表', async () => {
    await applySchema(db);

    const rows = await db.getAllAsync<{ name: string }>(
      "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'"
    );

    expect(rows.map((row) => row.name).sort()).toEqual(EXPECTED_TABLES);
  });

  it('可重複套用而不出錯（IF NOT EXISTS）', async () => {
    await applySchema(db);
    await expect(applySchema(db)).resolves.toBeUndefined();
  });

  it('建立索引', async () => {
    await applySchema(db);

    const rows = await db.getAllAsync<{ name: string }>(
      "SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'idx_%'"
    );

    expect(rows.map((row) => row.name).sort()).toEqual([
      'idx_attempt_created',
      'idx_attempt_mode',
      'idx_lyric_line_song',
      'idx_schedule_due',
      'idx_vocab_status',
    ]);
  });
});

describe('刪除語意', () => {
  it('foreign_keys 確實已啟用（否則約束形同虛設）', async () => {
    await applySchema(db);
    const row = await db.getFirstAsync<{ foreign_keys: number }>('PRAGMA foreign_keys');
    expect(row?.foreign_keys).toBe(1);
  });

  it('刪除歌曲：歌詞行隨之刪除，但字庫與練習紀錄必須保留', async () => {
    await applySchema(db);
    const songId = await insertSong(db);

    await db.runAsync(
      'INSERT INTO lyric_line (song_id, line_index, text, start_ms) VALUES (?, ?, ?, ?)',
      songId,
      0,
      'Hello, it is me',
      0
    );

    const vocab = await db.runAsync(
      `INSERT INTO vocab (lemma, surface_form, first_song_id, context_line, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?)`,
      'hello',
      'Hello',
      songId,
      'Hello, it is me',
      Date.now(),
      Date.now()
    );
    await db.runAsync('INSERT INTO vocab_schedule (vocab_id) VALUES (?)', vocab.lastInsertRowId);
    await db.runAsync(
      `INSERT INTO attempt (mode_id, song_id, line_index, is_correct, created_at)
       VALUES (?, ?, ?, ?, ?)`,
      'fill-choice',
      songId,
      0,
      0,
      Date.now()
    );

    await db.runAsync('DELETE FROM song WHERE id = ?', songId);

    // 純快取：隨歌曲消失
    expect(await db.getAllAsync('SELECT * FROM lyric_line')).toHaveLength(0);

    // 使用者資料：保留，只把參照設為 NULL
    const keptVocab = await db.getFirstAsync<{ first_song_id: number | null }>(
      'SELECT first_song_id FROM vocab WHERE lemma = ?',
      'hello'
    );
    expect(keptVocab?.first_song_id).toBeNull();
    expect(await db.getAllAsync('SELECT * FROM vocab_schedule')).toHaveLength(1);

    const keptAttempt = await db.getFirstAsync<{ song_id: number | null }>(
      'SELECT song_id FROM attempt'
    );
    expect(keptAttempt?.song_id).toBeNull();
    expect(await db.getAllAsync('SELECT * FROM attempt')).toHaveLength(1);
  });

  it('刪除單字時，其 FSRS 排程一併刪除', async () => {
    await applySchema(db);
    const now = Date.now();

    const vocab = await db.runAsync(
      'INSERT INTO vocab (lemma, created_at, updated_at) VALUES (?, ?, ?)',
      'hope',
      now,
      now
    );
    await db.runAsync('INSERT INTO vocab_schedule (vocab_id) VALUES (?)', vocab.lastInsertRowId);

    await db.runAsync('DELETE FROM vocab WHERE id = ?', vocab.lastInsertRowId);

    expect(await db.getAllAsync('SELECT * FROM vocab_schedule')).toHaveLength(0);
  });
});

describe('約束與預設值', () => {
  it('vocab.lemma 不可重複', async () => {
    await applySchema(db);
    const now = Date.now();

    await db.runAsync(
      'INSERT INTO vocab (lemma, created_at, updated_at) VALUES (?, ?, ?)',
      'hope',
      now,
      now
    );

    await expect(
      db.runAsync('INSERT INTO vocab (lemma, created_at, updated_at) VALUES (?, ?, ?)', 'hope', now, now)
    ).rejects.toThrow();
  });

  it('lyric_line 的 (song_id, line_index) 不可重複', async () => {
    await applySchema(db);
    const songId = await insertSong(db);

    await db.runAsync(
      'INSERT INTO lyric_line (song_id, line_index, text) VALUES (?, ?, ?)',
      songId,
      0,
      'a'
    );

    await expect(
      db.runAsync('INSERT INTO lyric_line (song_id, line_index, text) VALUES (?, ?, ?)', songId, 0, 'b')
    ).rejects.toThrow();
  });

  it('song.lrclib_id 不可重複', async () => {
    await applySchema(db);
    await insertSong(db, 42);
    await expect(insertSong(db, 42)).rejects.toThrow();
  });

  it('套用 vocab 與 schedule 的預設值', async () => {
    await applySchema(db);
    const now = Date.now();

    await db.runAsync(
      'INSERT INTO vocab (lemma, created_at, updated_at) VALUES (?, ?, ?)',
      'hope',
      now,
      now
    );
    const vocab = await db.getFirstAsync<{
      error_count: number;
      correct_streak: number;
      status: string;
    }>('SELECT error_count, correct_streak, status FROM vocab WHERE lemma = ?', 'hope');

    expect(vocab).toEqual({ error_count: 0, correct_streak: 0, status: 'learning' });

    const vocabId = (await db.getFirstAsync<{ id: number }>('SELECT id FROM vocab'))!.id;
    await db.runAsync('INSERT INTO vocab_schedule (vocab_id) VALUES (?)', vocabId);

    const schedule = await db.getFirstAsync<{ reps: number; lapses: number; state: string }>(
      'SELECT reps, lapses, state FROM vocab_schedule WHERE vocab_id = ?',
      vocabId
    );

    expect(schedule).toEqual({ reps: 0, lapses: 0, state: 'new' });
  });

  it('daily_stat 以 day 為主鍵', async () => {
    await applySchema(db);

    await db.runAsync("INSERT INTO daily_stat (day, score) VALUES ('2026-10-07', 10)");
    await expect(
      db.runAsync("INSERT INTO daily_stat (day, score) VALUES ('2026-10-07', 20)")
    ).rejects.toThrow();
  });
});

describe('migrate()', () => {
  it('第一次執行會建表並寫入 user_version', async () => {
    await migrate(db);

    const row = await db.getFirstAsync<{ user_version: number }>('PRAGMA user_version');
    expect(row?.user_version).toBe(SCHEMA_VERSION);

    const tables = await db.getAllAsync<{ name: string }>(
      "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'vocab'"
    );
    expect(tables).toHaveLength(1);
  });

  it('重複執行是安全的（不會重複建表或出錯）', async () => {
    await migrate(db);
    await expect(migrate(db)).resolves.toBeUndefined();

    const row = await db.getFirstAsync<{ user_version: number }>('PRAGMA user_version');
    expect(row?.user_version).toBe(SCHEMA_VERSION);
  });

  it('已是最新版本時不會重建資料表（既有資料保留）', async () => {
    await migrate(db);
    const now = Date.now();
    await db.runAsync(
      'INSERT INTO vocab (lemma, created_at, updated_at) VALUES (?, ?, ?)',
      'keep-me',
      now,
      now
    );

    await migrate(db);

    expect((await db.getAllAsync('SELECT * FROM vocab')).length).toBe(1);
  });
});
