/**
 * LRCLIB 搜尋用戶端。
 *
 * 端點：`GET /api/search`（見 https://lrclib.net/docs）。
 * 實測（2026-10）：一次回傳最多 20 筆，**每筆都含完整歌詞**，且**沒有任何封面欄位**。
 */
import type { FetchLike, LrclibSearchResult } from './types';

export const LRCLIB_BASE_URL = 'https://lrclib.net';

/** LRCLIB 要求可識別的 User-Agent。 */
export const LRCLIB_USER_AGENT = 'LyrianApp/1.0.0 (https://github.com/example/lyrian)';

/** 只取前 N 筆：每筆都帶完整歌詞，取太多會浪費頻寬。 */
export const LRCLIB_RESULT_LIMIT = 10;

export interface LrclibSearchParams {
  artist: string;
  track: string;
}

/**
 * 建立搜尋 URL。
 * 兩欄都有時用 `artist_name` + `track_name`（精準）；只有一欄時退回通用 `q`。
 */
export function buildLrclibSearchUrl(params: LrclibSearchParams): string {
  const artist = params.artist.trim();
  const track = params.track.trim();

  const query = new URLSearchParams();
  if (artist && track) {
    query.set('artist_name', artist);
    query.set('track_name', track);
  } else {
    query.set('q', artist || track);
  }

  return `${LRCLIB_BASE_URL}/api/search?${query.toString()}`;
}

function readString(record: Record<string, unknown>, key: string): string | null {
  const value = record[key];
  return typeof value === 'string' ? value : null;
}

/** 解析並過濾 `/api/search` 的回傳。格式不符的項目會被丟棄，不讓它弄壞整個清單。 */
export function parseLrclibSearchResults(payload: unknown): LrclibSearchResult[] {
  if (!Array.isArray(payload)) return [];

  const results: LrclibSearchResult[] = [];

  for (const entry of payload) {
    if (!entry || typeof entry !== 'object') continue;

    const record = entry as Record<string, unknown>;
    const id = typeof record.id === 'number' ? record.id : null;
    const trackName = readString(record, 'trackName');
    const artistName = readString(record, 'artistName');

    if (id === null || !trackName || !artistName) continue;

    results.push({
      id,
      trackName,
      artistName,
      albumName: readString(record, 'albumName'),
      duration: typeof record.duration === 'number' ? record.duration : null,
      instrumental: record.instrumental === true,
      plainLyrics: readString(record, 'plainLyrics'),
      syncedLyrics: readString(record, 'syncedLyrics'),
      lyricsfile: readString(record, 'lyricsfile'),
    });
  }

  return results;
}

/** 搜尋 LRCLIB。失敗時丟出帶狀態碼的錯誤，由呼叫端決定如何呈現。 */
export async function searchLrclib(
  params: LrclibSearchParams,
  fetchImpl: FetchLike
): Promise<LrclibSearchResult[]> {
  const response = await fetchImpl(buildLrclibSearchUrl(params), {
    headers: { 'User-Agent': LRCLIB_USER_AGENT },
  });

  if (!response.ok) {
    throw new Error(`LRCLIB 搜尋失敗（${response.status}）`);
  }

  const payload = await response.json();
  return parseLrclibSearchResults(payload).slice(0, LRCLIB_RESULT_LIMIT);
}
