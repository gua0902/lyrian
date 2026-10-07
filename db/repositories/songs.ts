/**
 * 歌曲與歌詞快取。
 *
 * `song` / `lyric_line` 是 **LRCLIB 資料的快取**，不是使用者資料——
 * 重複練習同一首歌時不必再打 API。
 */
import type { LyricLine } from '@/features/practice';

import { withTransaction } from '../transaction';
import type { SqlDatabase } from '../types';

export interface SaveSongInput {
  lrclibId: number;
  title: string;
  artist: string;
  album: string | null;
  durationMs: number | null;
  coverUrl: string | null;
  genre: string | null;
  difficulty: string | null;
  now: number;
}

export interface SongRow {
  id: number;
  lrclib_id: number | null;
  title: string;
  artist: string;
  album: string | null;
  duration_ms: number | null;
  cover_url: string | null;
  genre: string | null;
  difficulty: string | null;
}

/** 以 `lrclib_id` 為鍵 upsert，回傳本地 `song.id`。 */
export async function saveSong(database: SqlDatabase, input: SaveSongInput): Promise<number> {
  const row = await database.getFirstAsync<{ id: number }>(
    `INSERT INTO song (lrclib_id, title, artist, album, duration_ms, cover_url, genre, difficulty, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
     ON CONFLICT(lrclib_id) DO UPDATE SET
       title       = excluded.title,
       artist      = excluded.artist,
       album       = excluded.album,
       duration_ms = excluded.duration_ms,
       cover_url   = excluded.cover_url,
       genre       = excluded.genre,
       difficulty  = excluded.difficulty
     RETURNING id`,
    input.lrclibId,
    input.title,
    input.artist,
    input.album,
    input.durationMs,
    input.coverUrl,
    input.genre,
    input.difficulty,
    input.now
  );

  if (!row) throw new Error('儲存歌曲失敗：沒有取得 id');
  return row.id;
}

export async function findSongByLrclibId(
  database: SqlDatabase,
  lrclibId: number
): Promise<SongRow | null> {
  return database.getFirstAsync<SongRow>('SELECT * FROM song WHERE lrclib_id = ?', lrclibId);
}

/**
 * 覆寫一首歌的歌詞行。
 *
 * 先刪再寫（而非逐行 upsert），因為重新抓取時行數可能改變，
 * 留下舊行會產生過時的歌詞。整段包在交易中。
 */
export async function saveLyricLines(
  database: SqlDatabase,
  songId: number,
  lines: readonly LyricLine[]
): Promise<void> {
  await withTransaction(database, async () => {
    await database.runAsync('DELETE FROM lyric_line WHERE song_id = ?', songId);

    for (const line of lines) {
      await database.runAsync(
        `INSERT INTO lyric_line (song_id, line_index, text, start_ms, end_ms)
         VALUES (?, ?, ?, ?, ?)`,
        songId,
        line.index,
        line.text,
        line.startMs,
        line.endMs
      );
    }
  });
}

export async function getLyricLines(database: SqlDatabase, songId: number): Promise<LyricLine[]> {
  const rows = await database.getAllAsync<{
    line_index: number;
    text: string;
    start_ms: number | null;
    end_ms: number | null;
  }>('SELECT line_index, text, start_ms, end_ms FROM lyric_line WHERE song_id = ? ORDER BY line_index', songId);

  return rows.map((row) => ({
    index: row.line_index,
    text: row.text,
    startMs: row.start_ms ?? 0,
    endMs: row.end_ms,
  }));
}
