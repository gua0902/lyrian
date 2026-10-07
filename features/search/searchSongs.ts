/**
 * 搜尋編排：LRCLIB 找歌詞 → iTunes 補封面/曲風 → 計算可挖空行數。
 *
 * 設計原則（見 docs/ARCHITECTURE.md §3）：
 * 1. **iTunes 只負責加分資訊**，它失敗不該讓搜尋整體失敗。
 * 2. **iTunes 只打一次**，用一次查詢覆蓋整批 LRCLIB 結果，而不是每首歌各打一次。
 * 3. 純函式（`toSongSearchResult` / `dedupeResults`）與 I/O 分離，方便單元測試。
 */
import { countBlankableLines, parseLyrics, type RawLyrics } from '@/features/practice';

import { searchItunes, artworkUrlAtSize, translateGenre } from './itunes';
import { searchLrclib } from './lrclib';
import { normalizeArtist, normalizeForMatch, pickBestItunesMatch } from './normalize';
import type { FetchLike, ItunesTrack, LrclibSearchResult, SongSearchResult } from './types';

const defaultFetch: FetchLike = (url, init) => fetch(url, init);

/** 把一筆 LRCLIB 結果轉成 UI 要的形狀，並用 iTunes 資料補封面與曲風。 */
export function toSongSearchResult(
  entry: LrclibSearchResult,
  itunesTracks: readonly ItunesTrack[]
): SongSearchResult {
  const artist = normalizeArtist(entry.artistName);
  const match = pickBestItunesMatch({ title: entry.trackName, artist }, itunesTracks);

  const lyrics: RawLyrics = {
    lyricsfile: entry.lyricsfile,
    syncedLyrics: entry.syncedLyrics,
    plainLyrics: entry.plainLyrics,
  };

  return {
    lrclibId: entry.id,
    title: entry.trackName,
    artist,
    album: entry.albumName,
    // LRCLIB 的 duration 單位是「秒」（浮點數），換成毫秒
    durationMs: entry.duration !== null ? Math.round(entry.duration * 1000) : null,
    coverUrl: artworkUrlAtSize(match?.artworkUrl100 ?? null),
    genre: translateGenre(match?.primaryGenreName ?? null),
    blankCount: countBlankableLines(parseLyrics(lyrics)),
    lyrics,
  };
}

/**
 * 依「標題 + 歌手」去重。
 *
 * LRCLIB 由社群貢獻，同一首歌常有多筆（實測 `artistName` 會是 `"Adele, Adele"` 與
 * `"Adele/Adele"`）。經 `normalizeArtist` 後這些會視為同一筆。
 */
export function dedupeResults(results: readonly SongSearchResult[]): SongSearchResult[] {
  const seen = new Set<string>();
  const unique: SongSearchResult[] = [];

  for (const result of results) {
    const key = `${normalizeForMatch(result.title)}|${normalizeForMatch(result.artist)}`;
    if (seen.has(key)) continue;

    seen.add(key);
    unique.push(result);
  }

  return unique;
}

export interface SearchSongsDeps {
  /** 可注入的 fetch，供測試使用 */
  fetchImpl?: FetchLike;
}

/**
 * 搜尋可練習的歌曲。
 *
 * 回傳的結果**只包含確實能挖空的歌**（`blankCount > 0`）——
 * 純演奏曲或沒有合格歌詞的項目對 `fill-choice` 沒有意義。
 */
export async function searchSongs(
  input: { artist: string; track: string },
  deps: SearchSongsDeps = {}
): Promise<SongSearchResult[]> {
  const fetchImpl = deps.fetchImpl ?? defaultFetch;

  const entries = await searchLrclib(input, fetchImpl);
  if (entries.length === 0) return [];

  // 一次 iTunes 查詢覆蓋整批，避免 N 次請求
  const itunesTracks = await searchItunes(`${input.artist} ${input.track}`, fetchImpl);

  return dedupeResults(entries.map((entry) => toSongSearchResult(entry, itunesTracks))).filter(
    (result) => result.blankCount > 0
  );
}
