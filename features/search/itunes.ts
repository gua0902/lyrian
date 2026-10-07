/**
 * iTunes Search API 用戶端（免 key）。
 *
 * 用途：**補上 LRCLIB 沒有的封面與曲風**（見 docs/ARCHITECTURE.md §4.2）。
 * 實測（2026-10）回傳欄位含 `artworkUrl100`、`primaryGenreName`、`trackTimeMillis`。
 */
import type { FetchLike, ItunesTrack } from './types';

export const ITUNES_BASE_URL = 'https://itunes.apple.com';

/** 一次抓多筆，以便同一批 LRCLIB 結果都能配到封面（只需一次請求）。 */
export const ITUNES_SEARCH_LIMIT = 25;

/** 封面網址可換尺寸：`100x100bb` → `600x600bb`。 */
export const ARTWORK_DEFAULT_SIZE = 600;

/** 建立 iTunes 搜尋 URL。 */
export function buildItunesSearchUrl(term: string, limit: number = ITUNES_SEARCH_LIMIT): string {
  const query = new URLSearchParams({
    term: term.trim(),
    entity: 'song',
    limit: String(limit),
  });

  return `${ITUNES_BASE_URL}/search?${query.toString()}`;
}

function readString(record: Record<string, unknown>, key: string): string | null {
  const value = record[key];
  return typeof value === 'string' ? value : null;
}

/** 解析 iTunes 回傳，過濾掉缺少必要欄位的項目。 */
export function parseItunesTracks(payload: unknown): ItunesTrack[] {
  if (!payload || typeof payload !== 'object') return [];

  const results = (payload as Record<string, unknown>).results;
  if (!Array.isArray(results)) return [];

  const tracks: ItunesTrack[] = [];

  for (const entry of results) {
    if (!entry || typeof entry !== 'object') continue;

    const record = entry as Record<string, unknown>;
    const trackName = readString(record, 'trackName');
    const artistName = readString(record, 'artistName');
    if (!trackName || !artistName) continue;

    tracks.push({
      trackName,
      artistName,
      collectionName: readString(record, 'collectionName'),
      primaryGenreName: readString(record, 'primaryGenreName'),
      trackTimeMillis: typeof record.trackTimeMillis === 'number' ? record.trackTimeMillis : null,
      artworkUrl100: readString(record, 'artworkUrl100'),
    });
  }

  return tracks;
}

/** 把封面網址換成指定尺寸；無法辨識格式時原樣回傳。 */
export function artworkUrlAtSize(
  url: string | null,
  size: number = ARTWORK_DEFAULT_SIZE
): string | null {
  if (!url) return null;
  return url.replace(/\/\d+x\d+bb\./, `/${size}x${size}bb.`);
}

/**
 * iTunes 的曲風代碼對照為繁中。
 *
 * 注意：參考圖顯示的「生活英語」是**學習主題**，不是音樂曲風；
 * LRCLIB 與 iTunes 都沒有這個資料，因此這裡顯示的是真實的音樂曲風。
 */
const GENRE_LABELS: Record<string, string> = {
  Pop: '流行',
  Rock: '搖滾',
  Alternative: '另類',
  'Hip-Hop/Rap': '嘻哈',
  'R&B/Soul': '節奏藍調',
  Country: '鄉村',
  Jazz: '爵士',
  Classical: '古典',
  Electronic: '電子',
  Dance: '舞曲',
  'Singer/Songwriter': '創作歌手',
  Folk: '民謠',
  Metal: '金屬',
  'Easy Listening': '輕音樂',
  Soundtrack: '原聲帶',
  'Vocal': '人聲',
  Reggae: '雷鬼',
  Blues: '藍調',
  Latin: '拉丁',
  World: '世界音樂',
  'Children\'s Music': '兒童音樂',
  'Hip-Hop': '嘻哈',
  'Rap': '饒舌',
  Indie: '獨立',
  Punk: '龐克',
  'New Age': '新世紀',
  Inspirational: '勵志',
  'Alternative Rock': '另類搖滾',
  'Hard Rock': '硬式搖滾',
  'Adult Contemporary': '成人抒情',
};

/** 曲風代碼轉繁中；沒有對照時原樣回傳。 */
export function translateGenre(genre: string | null): string | null {
  if (!genre) return null;
  return GENRE_LABELS[genre] ?? genre;
}

/**
 * 搜尋 iTunes。失敗時**不丟錯**，回傳空陣列。
 *
 * 原因：iTunes 只負責封面與曲風這種加分資訊，不該讓它的失敗拖垮整個搜尋
 * （見 docs/ARCHITECTURE.md §3「AI 絕不在關鍵路徑上」的同一原則）。
 */
export async function searchItunes(
  term: string,
  fetchImpl: FetchLike,
  limit: number = ITUNES_SEARCH_LIMIT
): Promise<ItunesTrack[]> {
  if (!term.trim()) return [];

  try {
    const response = await fetchImpl(buildItunesSearchUrl(term, limit));
    if (!response.ok) return [];

    return parseItunesTracks(await response.json());
  } catch {
    return [];
  }
}
