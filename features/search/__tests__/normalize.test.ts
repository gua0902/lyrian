import {
  normalizeArtist,
  normalizeForMatch,
  pickBestItunesMatch,
  scoreItunesMatch,
} from '../normalize';
import type { ItunesTrack } from '../types';

function track(overrides: Partial<ItunesTrack> & { trackName: string; artistName: string }): ItunesTrack {
  return {
    collectionName: null,
    primaryGenreName: null,
    trackTimeMillis: null,
    artworkUrl100: null,
    ...overrides,
  };
}

describe('normalizeArtist', () => {
  it('去掉重複的歌手（LRCLIB 實測資料）', () => {
    expect(normalizeArtist('Adele, Adele')).toBe('Adele');
    expect(normalizeArtist('Adele/Adele')).toBe('Adele');
  });

  it('保留不同的多位歌手', () => {
    expect(normalizeArtist('A, B, A')).toBe('A, B');
  });

  it('忽略大小寫差異的重複', () => {
    expect(normalizeArtist('Adele, adele')).toBe('Adele');
  });

  it('處理 feat. 連接詞', () => {
    expect(normalizeArtist('A feat. B')).toBe('A, B');
    expect(normalizeArtist('A ft. B')).toBe('A, B');
  });

  it('處理分號與 & 分隔', () => {
    expect(normalizeArtist('A; B')).toBe('A, B');
    expect(normalizeArtist('A & B')).toBe('A, B');
  });

  it('空字串安全', () => {
    expect(normalizeArtist('')).toBe('');
    expect(normalizeArtist('   ')).toBe('');
  });
});

describe('normalizeForMatch', () => {
  it('轉小寫並去標點', () => {
    expect(normalizeForMatch("Hello, It's Me!")).toBe('hello it s me');
  });

  it('去掉括號版本註記', () => {
    expect(normalizeForMatch('Hello (Remastered 2015)')).toBe('hello');
    expect(normalizeForMatch('Yellow [Live]')).toBe('yellow');
  });

  it('空字串安全', () => {
    expect(normalizeForMatch('')).toBe('');
  });
});

describe('scoreItunesMatch', () => {
  const target = { title: 'Hello', artist: 'Adele' };

  it('標題與歌手完全相同時分數最高', () => {
    expect(scoreItunesMatch(target, track({ trackName: 'Hello', artistName: 'Adele' }))).toBe(4);
  });

  it('大小寫與標點差異不影響', () => {
    expect(scoreItunesMatch(target, track({ trackName: 'hello', artistName: 'ADELE' }))).toBe(4);
  });

  it('標題相同但歌手只是包含關係時次之', () => {
    const score = scoreItunesMatch(target, track({ trackName: 'Hello', artistName: 'Adele Tribute Band' }));
    expect(score).toBe(3);
  });

  it('標題不同時為 0', () => {
    expect(scoreItunesMatch(target, track({ trackName: 'Rolling in the Deep', artistName: 'Adele' }))).toBe(0);
  });

  it('標題與歌手都無關時為 0', () => {
    expect(scoreItunesMatch(target, track({ trackName: 'Yellow', artistName: 'Coldplay' }))).toBe(0);
  });
});

describe('pickBestItunesMatch', () => {
  it('挑出分數最高的配對', () => {
    const tracks = [
      track({ trackName: 'Hello', artistName: 'Some Cover Band' }),
      track({ trackName: 'Hello', artistName: 'Adele' }),
    ];
    expect(pickBestItunesMatch({ title: 'Hello', artist: 'Adele' }, tracks)?.artistName).toBe('Adele');
  });

  it('沒有可用配對時回傳 null', () => {
    expect(pickBestItunesMatch({ title: 'Hello', artist: 'Adele' }, [])).toBeNull();
    expect(
      pickBestItunesMatch({ title: 'Hello', artist: 'Adele' }, [
        track({ trackName: 'Yellow', artistName: 'Coldplay' }),
      ])
    ).toBeNull();
  });

  it('能配上帶版本註記的標題', () => {
    const tracks = [track({ trackName: 'Hello (Remastered)', artistName: 'Adele' })];
    expect(pickBestItunesMatch({ title: 'Hello', artist: 'Adele' }, tracks)?.trackName).toBe(
      'Hello (Remastered)'
    );
  });
});
