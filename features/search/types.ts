/**
 * 搜尋功能的領域型別。
 *
 * 資料來源（見 docs/ARCHITECTURE.md §4）：
 * - **LRCLIB** 提供歌詞 + 時間軸，但**沒有封面**。
 * - **iTunes Search API** 補上封面與曲風（免 key）。
 */
import type { RawLyrics } from '@/features/practice';

/** 搜尋結果中的一首歌。 */
export interface SongSearchResult {
  /** LRCLIB 的 id，作為後續取歌詞與快取的鍵 */
  lrclibId: number;
  title: string;
  /** 已正規化（去掉 `Adele, Adele` / `Adele/Adele` 這類重複） */
  artist: string;
  album: string | null;
  durationMs: number | null;
  /** 來自 iTunes；LRCLIB 沒有封面 */
  coverUrl: string | null;
  /** 來自 iTunes 的 `primaryGenreName`，已翻譯為繁中 */
  genre: string | null;
  /** 可挖空的行數（對應參考圖的「10 個填空」） */
  blankCount: number;
  /** 原始歌詞，選歌後可直接使用，避免二次請求 */
  lyrics: RawLyrics;
}

/** LRCLIB `/api/search` 的原始回傳形狀（只列出本專案用到的欄位）。 */
export interface LrclibSearchResult {
  id: number;
  trackName: string;
  artistName: string;
  albumName: string | null;
  duration: number | null;
  instrumental: boolean;
  plainLyrics: string | null;
  syncedLyrics: string | null;
  lyricsfile: string | null;
}

/** iTunes Search API 的原始回傳形狀（只列出本專案用到的欄位）。 */
export interface ItunesTrack {
  trackName: string;
  artistName: string;
  collectionName: string | null;
  primaryGenreName: string | null;
  trackTimeMillis: number | null;
  artworkUrl100: string | null;
}

/** 可注入的 fetch，方便測試。 */
export type FetchLike = (url: string, init?: { headers?: Record<string, string> }) => Promise<{
  ok: boolean;
  status: number;
  json: () => Promise<unknown>;
}>;
