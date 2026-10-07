/**
 * Lyrian — SQLite Schema（純 SQL 文字，與驅動無關）
 *
 * 依據：docs/ARCHITECTURE.md §8.3
 *
 * 本檔案刻意**不 import 任何資料庫驅動**，因此無論最終採用
 * expo-sqlite（Expo 原生）或其他驅動，這裡的 SQL 都可直接沿用。
 *
 * 資料表：
 *   song              歌曲快取
 *   lyric_line        歌詞行快取（含 start_ms / end_ms）
 *   vocab             單字庫（以 lemma 為單位去重）
 *   vocab_schedule    FSRS 排程狀態
 *   attempt           作答紀錄（所有練習模式的唯一收斂點）
 *   daily_stat        每日統計（「我的」頁）
 *   provider_config   BYOK provider 設定（金鑰本體存 SecureStore，此表只存別名）
 *
 * **刪除語意（重要）**：`song` 只是 LRCLIB 資料的快取，清掉它**不該弄丟使用者的資料**。
 * 因此 `lyric_line` 隨歌曲 CASCADE（純快取），但 `vocab` 與 `attempt` 只把參照設為 NULL
 * （字庫與練習紀錄屬於使用者，必須留下）。`vocab_schedule` 隨 vocab CASCADE。
 */

/** 目前 schema 版本，寫入 SQLite 的 `PRAGMA user_version`。每次變更結構時 +1。 */
export const SCHEMA_VERSION = 1;

/** 資料庫檔名。 */
export const DATABASE_NAME = 'lyrian.db';

/** 建表語句（全部 IF NOT EXISTS，可重複執行）。 */
export const CREATE_TABLES_SQL = `
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

-- 歌曲快取
CREATE TABLE IF NOT EXISTS song (
  id            INTEGER PRIMARY KEY,
  lrclib_id     INTEGER UNIQUE,
  title         TEXT NOT NULL,
  artist        TEXT NOT NULL,
  album         TEXT,
  duration_ms   INTEGER,
  cover_url     TEXT,
  genre         TEXT,
  difficulty    TEXT,
  created_at    INTEGER NOT NULL
);

-- 歌詞行快取（start_ms / end_ms 來自 LRCLIB 的 lyricsfile）
CREATE TABLE IF NOT EXISTS lyric_line (
  id          INTEGER PRIMARY KEY,
  song_id     INTEGER NOT NULL REFERENCES song(id) ON DELETE CASCADE,
  line_index  INTEGER NOT NULL,
  text        TEXT NOT NULL,
  start_ms    INTEGER,
  end_ms      INTEGER,
  UNIQUE(song_id, line_index)
);
CREATE INDEX IF NOT EXISTS idx_lyric_line_song ON lyric_line(song_id, line_index);

-- 單字庫（以 lemma 為單位去重，避免 loved/loving/loves 灌爆）
CREATE TABLE IF NOT EXISTS vocab (
  id                  INTEGER PRIMARY KEY,
  lemma               TEXT NOT NULL UNIQUE,
  surface_form        TEXT,
  translation         TEXT,
  phonetic            TEXT,
  pos                 TEXT,
  bnc                 INTEGER,
  frq                 INTEGER,
  first_song_id       INTEGER REFERENCES song(id) ON DELETE SET NULL,
  context_line        TEXT,
  context_translation TEXT,
  error_count         INTEGER NOT NULL DEFAULT 0,
  correct_streak      INTEGER NOT NULL DEFAULT 0,
  status              TEXT NOT NULL DEFAULT 'learning',
  created_at          INTEGER NOT NULL,
  updated_at          INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_vocab_status ON vocab(status);

-- FSRS 排程
CREATE TABLE IF NOT EXISTS vocab_schedule (
  vocab_id        INTEGER PRIMARY KEY REFERENCES vocab(id) ON DELETE CASCADE,
  stability       REAL,
  difficulty      REAL,
  due_at          INTEGER,
  last_review_at  INTEGER,
  reps            INTEGER NOT NULL DEFAULT 0,
  lapses          INTEGER NOT NULL DEFAULT 0,
  state           TEXT NOT NULL DEFAULT 'new'
);
CREATE INDEX IF NOT EXISTS idx_schedule_due ON vocab_schedule(due_at);

-- 作答紀錄（唯一收斂點）
CREATE TABLE IF NOT EXISTS attempt (
  id              INTEGER PRIMARY KEY,
  mode_id         TEXT NOT NULL,
  song_id         INTEGER REFERENCES song(id) ON DELETE SET NULL,
  line_index      INTEGER,
  vocab_id        INTEGER REFERENCES vocab(id) ON DELETE SET NULL,
  prompt          TEXT,
  user_answer     TEXT,
  correct_answer  TEXT,
  score           REAL,
  is_correct      INTEGER NOT NULL,
  duration_ms     INTEGER,
  created_at      INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_attempt_created ON attempt(created_at);
CREATE INDEX IF NOT EXISTS idx_attempt_mode ON attempt(mode_id);

-- 每日統計
CREATE TABLE IF NOT EXISTS daily_stat (
  day         TEXT PRIMARY KEY,
  score       INTEGER NOT NULL DEFAULT 0,
  attempts    INTEGER NOT NULL DEFAULT 0,
  correct     INTEGER NOT NULL DEFAULT 0,
  new_words   INTEGER NOT NULL DEFAULT 0
);

-- BYOK provider 設定（金鑰本體存 SecureStore，此表只存別名）
CREATE TABLE IF NOT EXISTS provider_config (
  id            INTEGER PRIMARY KEY,
  label         TEXT NOT NULL,
  base_url      TEXT NOT NULL,
  model         TEXT NOT NULL,
  key_ref       TEXT,
  priority      INTEGER NOT NULL DEFAULT 0,
  enabled       INTEGER NOT NULL DEFAULT 1,
  last_test_at  INTEGER,
  last_test_ok  INTEGER
);
`;

/**
 * 資料表中使用的固定字串值（避免各處硬寫）。
 */
export const VocabStatus = {
  learning: 'learning',
  reviewing: 'reviewing',
  mastered: 'mastered',
} as const;
export type VocabStatus = (typeof VocabStatus)[keyof typeof VocabStatus];

export const FsrsState = {
  new: 'new',
  learning: 'learning',
  review: 'review',
  relearning: 'relearning',
} as const;
export type FsrsState = (typeof FsrsState)[keyof typeof FsrsState];
