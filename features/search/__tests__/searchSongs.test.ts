import { dedupeResults, searchSongs, toSongSearchResult } from '../searchSongs';
import type { FetchLike, ItunesTrack, LrclibSearchResult, SongSearchResult } from '../types';

function jsonResponse(payload: unknown, ok = true, status = 200) {
  return { ok, status, json: async () => payload };
}

function lrclibEntry(overrides: Partial<LrclibSearchResult> = {}): LrclibSearchResult {
  return {
    id: 197097,
    trackName: 'Hello',
    artistName: 'Adele, Adele',
    albumName: '25',
    duration: 296.0,
    instrumental: false,
    plainLyrics: null,
    syncedLyrics: null,
    lyricsfile: 'lines:\n- text: I keep a little hope inside my heart\n  start_ms: 0\n  end_ms: 1000\n',
    ...overrides,
  };
}

function itunesTrack(overrides: Partial<ItunesTrack> = {}): ItunesTrack {
  return {
    trackName: 'Hello',
    artistName: 'Adele',
    collectionName: '25',
    primaryGenreName: 'Pop',
    trackTimeMillis: 295_502,
    artworkUrl100: 'https://example.com/abc/100x100bb.jpg',
    ...overrides,
  };
}

describe('toSongSearchResult', () => {
  it('把 LRCLIB 的秒數 duration 換成毫秒', () => {
    const result = toSongSearchResult(lrclibEntry(), []);
    expect(result.durationMs).toBe(296_000);
  });

  it('正規化歌手名稱', () => {
    expect(toSongSearchResult(lrclibEntry(), []).artist).toBe('Adele');
  });

  it('從 iTunes 配對補上封面（放大到 600）與曲風', () => {
    const result = toSongSearchResult(lrclibEntry(), [itunesTrack()]);
    expect(result.coverUrl).toBe('https://example.com/abc/600x600bb.jpg');
    expect(result.genre).toBe('流行');
  });

  it('沒有 iTunes 配對時封面與曲風為 null，其餘仍可用', () => {
    const result = toSongSearchResult(lrclibEntry(), []);
    expect(result.coverUrl).toBeNull();
    expect(result.genre).toBeNull();
    expect(result.title).toBe('Hello');
    expect(result.blankCount).toBeGreaterThan(0);
  });

  it('計算可挖空的行數（對應參考圖的「N 個填空」）', () => {
    const lyricsfile =
      'lines:\n' +
      '- text: I keep a little hope inside my heart\n  start_ms: 0\n  end_ms: 1000\n' +
      '- text: a b c\n  start_ms: 1000\n  end_ms: 2000\n' +
      '- text: A brand-new day, a brand-new start\n  start_ms: 2000\n  end_ms: 3000\n';

    const result = toSongSearchResult(lrclibEntry({ lyricsfile }), []);
    expect(result.blankCount).toBe(2);
  });

  it('沒有歌詞時 blankCount 為 0', () => {
    const result = toSongSearchResult(
      lrclibEntry({ lyricsfile: null, syncedLyrics: null, plainLyrics: null }),
      []
    );
    expect(result.blankCount).toBe(0);
  });

  it('保留原始歌詞供選歌後直接使用，避免二次請求', () => {
    const result = toSongSearchResult(lrclibEntry(), []);
    expect(result.lyrics.lyricsfile).toContain('I keep a little hope');
  });
});

describe('dedupeResults', () => {
  function result(overrides: Partial<SongSearchResult>): SongSearchResult {
    return {
      lrclibId: 1,
      title: 'Hello',
      artist: 'Adele',
      album: null,
      durationMs: null,
      coverUrl: null,
      genre: null,
      blankCount: 5,
      lyrics: {},
      ...overrides,
    };
  }

  it('依標題 + 歌手去重（大小寫與標點不影響）', () => {
    const deduped = dedupeResults([
      result({ lrclibId: 1, title: 'Hello' }),
      result({ lrclibId: 2, title: 'hello' }),
      result({ lrclibId: 3, title: 'Yellow', artist: 'Coldplay' }),
    ]);

    expect(deduped.map((r) => r.lrclibId)).toEqual([1, 3]);
  });

  it('標題相同但歌手不同時保留兩筆', () => {
    const deduped = dedupeResults([
      result({ lrclibId: 1, title: 'Hello', artist: 'Adele' }),
      result({ lrclibId: 2, title: 'Hello', artist: 'Lionel Richie' }),
    ]);
    expect(deduped).toHaveLength(2);
  });

  it('忽略版本註記造成的重複', () => {
    const deduped = dedupeResults([
      result({ lrclibId: 1, title: 'Hello' }),
      result({ lrclibId: 2, title: 'Hello (Remastered 2015)' }),
    ]);
    expect(deduped).toHaveLength(1);
  });
});

describe('searchSongs', () => {
  it('串起 LRCLIB 與 iTunes，只打一次 iTunes', async () => {
    const calls: string[] = [];
    const fetchImpl: FetchLike = async (url) => {
      calls.push(url);
      if (url.includes('lrclib.net')) {
        return jsonResponse([
          { id: 1, trackName: 'Hello', artistName: 'Adele, Adele', duration: 296, lyricsfile: 'lines:\n- text: I keep a little hope\n  start_ms: 0\n' },
          { id: 2, trackName: 'Hello', artistName: 'Adele/Adele', duration: 296, lyricsfile: 'lines:\n- text: I keep a little hope\n  start_ms: 0\n' },
          { id: 3, trackName: 'Yellow', artistName: 'Coldplay', duration: 266, lyricsfile: 'lines:\n- text: Your skin and bones\n  start_ms: 0\n' },
        ]);
      }
      if (url.includes('itunes.apple.com')) {
        return jsonResponse({
          results: [
            { trackName: 'Hello', artistName: 'Adele', primaryGenreName: 'Pop', artworkUrl100: 'https://x/100x100bb.jpg' },
            { trackName: 'Yellow', artistName: 'Coldplay', primaryGenreName: 'Alternative', artworkUrl100: 'https://y/100x100bb.jpg' },
          ],
        });
      }
      return jsonResponse({}, false, 404);
    };

    const results = await searchSongs({ artist: 'Adele', track: 'Hello' }, { fetchImpl });

    // 兩筆重複的 Adele 被合併，Yellow 標題不符於 artist+track 但仍在 LRCLIB 結果中
    expect(results.map((r) => r.lrclibId)).toEqual([1, 3]);
    expect(calls.filter((url) => url.includes('itunes.apple.com'))).toHaveLength(1);
  });

  it('過濾掉無法挖空的歌（純演奏／沒有合格歌詞）', async () => {
    const fetchImpl: FetchLike = async (url) => {
      if (url.includes('lrclib.net')) {
        return jsonResponse([
          { id: 1, trackName: 'Instrumental', artistName: 'X', lyricsfile: null, syncedLyrics: null, plainLyrics: null },
          { id: 2, trackName: 'Real Song', artistName: 'Y', lyricsfile: 'lines:\n- text: wonderful morning\n  start_ms: 0\n' },
        ]);
      }
      return jsonResponse({ results: [] });
    };

    const results = await searchSongs({ artist: 'X', track: 'Y' }, { fetchImpl });
    expect(results.map((r) => r.lrclibId)).toEqual([2]);
  });

  it('LRCLIB 沒有結果時不呼叫 iTunes', async () => {
    const calls: string[] = [];
    const fetchImpl: FetchLike = async (url) => {
      calls.push(url);
      return jsonResponse([]);
    };

    expect(await searchSongs({ artist: 'Nobody', track: 'Nothing' }, { fetchImpl })).toEqual([]);
    expect(calls.filter((url) => url.includes('itunes.apple.com'))).toHaveLength(0);
  });

  it('LRCLIB 失敗時往外丟，讓 UI 能顯示錯誤', async () => {
    const fetchImpl: FetchLike = async () => jsonResponse({}, false, 500);
    await expect(searchSongs({ artist: 'A', track: 'B' }, { fetchImpl })).rejects.toThrow();
  });

  it('iTunes 失敗時仍回傳結果，只是沒有封面', async () => {
    const fetchImpl: FetchLike = async (url) => {
      if (url.includes('lrclib.net')) {
        return jsonResponse([
          { id: 1, trackName: 'Hello', artistName: 'Adele', duration: 296, lyricsfile: 'lines:\n- text: I keep a little hope\n  start_ms: 0\n' },
        ]);
      }
      throw new Error('itunes down');
    };

    const results = await searchSongs({ artist: 'Adele', track: 'Hello' }, { fetchImpl });
    expect(results).toHaveLength(1);
    expect(results[0].coverUrl).toBeNull();
  });
});
