import {
  artworkUrlAtSize,
  buildItunesSearchUrl,
  parseItunesTracks,
  searchItunes,
  translateGenre,
} from '../itunes';
import { buildLrclibSearchUrl, parseLrclibSearchResults, searchLrclib } from '../lrclib';
import type { FetchLike } from '../types';

function jsonResponse(payload: unknown, ok = true, status = 200) {
  return { ok, status, json: async () => payload };
}

/** 取自 LRCLIB 實測的欄位形狀。 */
const LRCLIB_ENTRY = {
  id: 197097,
  name: 'Hello',
  trackName: 'Hello',
  artistName: 'Adele, Adele',
  albumName: '25',
  duration: 296.0,
  instrumental: false,
  hasWordSync: false,
  plainLyrics: 'Hello, it is me',
  syncedLyrics: '[00:06.22] Hello, it is me',
  lyricsfile: "lines:\n- text: Hello, it is me\n  start_ms: 6220\n  end_ms: 11840\n",
};

describe('buildLrclibSearchUrl', () => {
  it('兩欄都有時用 artist_name + track_name', () => {
    const url = buildLrclibSearchUrl({ artist: 'Adele', track: 'Hello' });
    expect(url).toContain('artist_name=Adele');
    expect(url).toContain('track_name=Hello');
    expect(url).not.toContain('q=');
  });

  it('只有一欄時退回通用 q', () => {
    expect(buildLrclibSearchUrl({ artist: 'Adele', track: '' })).toContain('q=Adele');
    expect(buildLrclibSearchUrl({ artist: '', track: 'Hello' })).toContain('q=Hello');
  });

  it('正確編碼特殊字元', () => {
    const url = buildLrclibSearchUrl({ artist: 'AC/DC', track: 'Back in Black' });
    expect(url).toContain('artist_name=AC%2FDC');
  });
});

describe('parseLrclibSearchResults', () => {
  it('解析有效項目', () => {
    const results = parseLrclibSearchResults([LRCLIB_ENTRY]);
    expect(results).toHaveLength(1);
    expect(results[0].id).toBe(197097);
    expect(results[0].artistName).toBe('Adele, Adele');
    expect(results[0].duration).toBe(296);
    expect(results[0].instrumental).toBe(false);
  });

  it('丟棄缺少必要欄位的項目，但不弄壞整批', () => {
    const results = parseLrclibSearchResults([
      LRCLIB_ENTRY,
      { id: 1, artistName: 'No Title' },
      { trackName: 'No Id', artistName: 'X' },
      null,
      'not an object',
    ]);
    expect(results).toHaveLength(1);
  });

  it('非陣列輸入回傳空陣列', () => {
    expect(parseLrclibSearchResults(null)).toEqual([]);
    expect(parseLrclibSearchResults({})).toEqual([]);
  });

  it('缺少可選欄位時為 null', () => {
    const [result] = parseLrclibSearchResults([{ id: 1, trackName: 'T', artistName: 'A' }]);
    expect(result.albumName).toBeNull();
    expect(result.duration).toBeNull();
    expect(result.lyricsfile).toBeNull();
  });
});

describe('searchLrclib', () => {
  it('成功時回傳解析後的結果', async () => {
    const fetchImpl: FetchLike = async () => jsonResponse([LRCLIB_ENTRY]);
    const results = await searchLrclib({ artist: 'Adele', track: 'Hello' }, fetchImpl);
    expect(results).toHaveLength(1);
  });

  it('帶上 LRCLIB 要求的 User-Agent', async () => {
    let seenHeaders: Record<string, string> | undefined;
    const fetchImpl: FetchLike = async (_url, init) => {
      seenHeaders = init?.headers;
      return jsonResponse([]);
    };
    await searchLrclib({ artist: 'A', track: 'B' }, fetchImpl);
    expect(seenHeaders?.['User-Agent']).toContain('LyrianApp');
  });

  it('非 200 時丟出錯誤', async () => {
    const fetchImpl: FetchLike = async () => jsonResponse({}, false, 503);
    await expect(searchLrclib({ artist: 'A', track: 'B' }, fetchImpl)).rejects.toThrow('503');
  });
});

describe('buildItunesSearchUrl', () => {
  it('帶上 entity=song 與 limit', () => {
    const url = buildItunesSearchUrl('Adele Hello', 25);
    expect(url).toContain('entity=song');
    expect(url).toContain('limit=25');
    expect(url).toContain('term=Adele+Hello');
  });
});

describe('parseItunesTracks', () => {
  it('解析有效曲目', () => {
    const tracks = parseItunesTracks({
      resultCount: 1,
      results: [
        {
          trackName: 'Hello',
          artistName: 'Adele',
          collectionName: '25',
          primaryGenreName: 'Pop',
          trackTimeMillis: 295502,
          artworkUrl100: 'https://example.com/100x100bb.jpg',
        },
      ],
    });

    expect(tracks).toHaveLength(1);
    expect(tracks[0].primaryGenreName).toBe('Pop');
    expect(tracks[0].trackTimeMillis).toBe(295502);
  });

  it('丟棄缺少必要欄位的項目', () => {
    expect(parseItunesTracks({ results: [{ trackName: 'X' }, { artistName: 'Y' }, null] })).toEqual([]);
  });

  it('形狀不符時回傳空陣列', () => {
    expect(parseItunesTracks(null)).toEqual([]);
    expect(parseItunesTracks({})).toEqual([]);
    expect(parseItunesTracks({ results: 'nope' })).toEqual([]);
  });
});

describe('artworkUrlAtSize', () => {
  it('把 100x100bb 換成 600x600bb', () => {
    expect(artworkUrlAtSize('https://example.com/abc/100x100bb.jpg')).toBe(
      'https://example.com/abc/600x600bb.jpg'
    );
  });

  it('可指定尺寸', () => {
    expect(artworkUrlAtSize('https://example.com/100x100bb.jpg', 200)).toBe(
      'https://example.com/200x200bb.jpg'
    );
  });

  it('null 或無法辨識格式時安全處理', () => {
    expect(artworkUrlAtSize(null)).toBeNull();
    expect(artworkUrlAtSize('https://example.com/cover.jpg')).toBe('https://example.com/cover.jpg');
  });
});

describe('translateGenre', () => {
  it('常見曲風翻成繁中', () => {
    expect(translateGenre('Pop')).toBe('流行');
    expect(translateGenre('Rock')).toBe('搖滾');
  });

  it('沒有對照表時原樣回傳', () => {
    expect(translateGenre('Some Obscure Genre')).toBe('Some Obscure Genre');
  });

  it('null 回傳 null', () => {
    expect(translateGenre(null)).toBeNull();
  });
});

describe('searchItunes', () => {
  it('成功時回傳曲目', async () => {
    const fetchImpl: FetchLike = async () =>
      jsonResponse({ results: [{ trackName: 'Hello', artistName: 'Adele' }] });
    expect(await searchItunes('Adele Hello', fetchImpl)).toHaveLength(1);
  });

  it('失敗時回傳空陣列而不丟錯（封面是加分資訊，不該拖垮搜尋）', async () => {
    const notOk: FetchLike = async () => jsonResponse({}, false, 500);
    expect(await searchItunes('Adele Hello', notOk)).toEqual([]);

    const throws: FetchLike = async () => {
      throw new Error('network down');
    };
    expect(await searchItunes('Adele Hello', throws)).toEqual([]);
  });

  it('空字串不發請求', async () => {
    let called = 0;
    const fetchImpl: FetchLike = async () => {
      called += 1;
      return jsonResponse({ results: [] });
    };
    expect(await searchItunes('   ', fetchImpl)).toEqual([]);
    expect(called).toBe(0);
  });
});
