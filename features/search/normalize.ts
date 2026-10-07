/**
 * 名稱正規化與配對。
 *
 * LRCLIB 的 `artistName` 很髒——實測會出現 `"Adele, Adele"`、`"Adele/Adele"` 這種重複，
 * 而 iTunes 回傳的是乾淨的 `"Adele"`。要讓兩邊配得起來，必須先正規化。
 */
import type { ItunesTrack } from './types';

/** 分隔多位歌手的符號與連接詞。用字串建立以避免跳脫混淆。 */
const ARTIST_SEPARATORS = new RegExp('[,/;&]|\\s+(?:feat\\.?|ft\\.?|with)\\s+', 'gi');

/**
 * 清理歌手名稱：去重、統一以 `, ` 分隔。
 *
 * @example
 * normalizeArtist('Adele, Adele')  // 'Adele'
 * normalizeArtist('Adele/Adele')  // 'Adele'
 * normalizeArtist('A, B, A')      // 'A, B'
 */
export function normalizeArtist(raw: string): string {
  const trimmed = raw.trim();
  if (!trimmed) return '';

  const seen = new Set<string>();
  const unique: string[] = [];

  for (const part of trimmed.split(ARTIST_SEPARATORS)) {
    const name = part.trim();
    if (!name) continue;

    const key = name.toLowerCase();
    if (seen.has(key)) continue;

    seen.add(key);
    unique.push(name);
  }

  return unique.length > 0 ? unique.join(', ') : trimmed;
}

/**
 * 用於比對的寬鬆形式：轉小寫、去掉括號版本註記（remaster / live 等）、只留英數。
 */
export function normalizeForMatch(value: string): string {
  return value
    .toLowerCase()
    .replace(/\([^)]*\)|\[[^\]]*\]/g, ' ')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

/**
 * 計算 iTunes 曲目與 LRCLIB 結果的配對分數（0 = 不匹配，4 = 完全匹配）。
 * 標題必須相符（完全相同或互相包含），歌手則作為加權。
 */
export function scoreItunesMatch(
  target: { title: string; artist: string },
  track: ItunesTrack
): number {
  const targetTitle = normalizeForMatch(target.title);
  const targetArtist = normalizeForMatch(target.artist);
  const title = normalizeForMatch(track.trackName);
  const artist = normalizeForMatch(track.artistName);

  if (!targetTitle || !title) return 0;

  const titleExact = title === targetTitle;
  const titleContains =
    !titleExact && (title.includes(targetTitle) || targetTitle.includes(title));
  if (!titleExact && !titleContains) return 0;

  const artistExact = Boolean(targetArtist) && artist === targetArtist;
  const artistContains =
    !artistExact &&
    Boolean(targetArtist) &&
    (artist.includes(targetArtist) || targetArtist.includes(artist));

  if (titleExact && artistExact) return 4;
  if (titleExact && artistContains) return 3;
  if (titleContains && artistExact) return 2;
  if (titleContains && artistContains) return 1;
  return 0;
}

/** 從 iTunes 結果中挑出分數最高的配對；沒有可用配對時回傳 null。 */
export function pickBestItunesMatch(
  target: { title: string; artist: string },
  tracks: readonly ItunesTrack[]
): ItunesTrack | null {
  let best: ItunesTrack | null = null;
  let bestScore = 0;

  for (const track of tracks) {
    const score = scoreItunesMatch(target, track);
    if (score > bestScore) {
      bestScore = score;
      best = track;
    }
  }

  return bestScore > 0 ? best : null;
}
