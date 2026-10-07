import {
  PLAIN_LYRICS_INTERVAL_MS,
  parseLrcTimestamp,
  parseLyrics,
  parseLyricsFile,
  parsePlainLyrics,
  parseSyncedLyrics,
} from '../lyrics';

/** 取自 LRCLIB `/api/get` 的真實 `lyricsfile` 形狀（2026-10 實測）。 */
const LYRICS_FILE_SAMPLE = `version: '1.0'
metadata:
  title: Hello
  artist: Adele
  album: '25'
  duration_ms: 295000
  instrumental: false
lines:
- text: Hello, it's me
  start_ms: 6220
  end_ms: 11840
- text: I was wondering if after all these years you'd like to meet
  start_ms: 11840
  end_ms: 17960
- text: ''
  start_ms: 17960
  end_ms: 18000
- text: To go over everything
  start_ms: 18000
  end_ms: 23470
`;

describe('parseLrcTimestamp', () => {
  it('解析 mm:ss', () => {
    expect(parseLrcTimestamp('01:23')).toBe(83_000);
  });

  it('解析 mm:ss.xx（兩位小數）', () => {
    expect(parseLrcTimestamp('00:06.22')).toBe(6_220);
  });

  it('解析三位小數', () => {
    expect(parseLrcTimestamp('01:02.345')).toBe(62_345);
  });

  it('支援以冒號分隔小數的變體', () => {
    expect(parseLrcTimestamp('00:06:22')).toBe(6_220);
  });

  it('支援超過 99 的分鐘數', () => {
    expect(parseLrcTimestamp('123:45')).toBe(123 * 60_000 + 45_000);
  });

  it('對非時間字串回傳 null', () => {
    expect(parseLrcTimestamp('ar:Adele')).toBeNull();
    expect(parseLrcTimestamp('')).toBeNull();
  });
});

describe('parseLyricsFile', () => {
  it('解析 text / start_ms / end_ms', () => {
    const lines = parseLyricsFile(LYRICS_FILE_SAMPLE);

    expect(lines).toHaveLength(3);
    expect(lines[0]).toEqual({
      index: 0,
      text: "Hello, it's me",
      startMs: 6_220,
      endMs: 11_840,
    });
    expect(lines[1].startMs).toBe(11_840);
    expect(lines[1].endMs).toBe(17_960);
    expect(lines[2].index).toBe(2);
  });

  it('不會把 metadata 當成歌詞', () => {
    const texts = parseLyricsFile(LYRICS_FILE_SAMPLE).map((line) => line.text);
    expect(texts.join(' ')).not.toContain('Adele');
    expect(texts.join(' ')).not.toContain('295000');
  });

  it('濾掉空文字行並重新連續編號', () => {
    const lines = parseLyricsFile(LYRICS_FILE_SAMPLE);
    expect(lines.map((line) => line.index)).toEqual([0, 1, 2]);
    expect(lines.every((line) => line.text.length > 0)).toBe(true);
  });

  it('依時間排序', () => {
    const source = `lines:
- text: later
  start_ms: 900
  end_ms: 1000
- text: earlier
  start_ms: 100
  end_ms: 200
`;
    expect(parseLyricsFile(source).map((line) => line.text)).toEqual(['earlier', 'later']);
  });

  it('去掉單引號並還原跳脫的單引號', () => {
    const source = `lines:
- text: 'Hello, it''s me'
  start_ms: 0
  end_ms: 10
`;
    expect(parseLyricsFile(source)[0].text).toBe("Hello, it's me");
  });

  it('去掉雙引號', () => {
    const source = `lines:
- text: "double quoted"
  start_ms: 0
  end_ms: 10
`;
    expect(parseLyricsFile(source)[0].text).toBe('double quoted');
  });

  it('忽略巢狀的逐字同步 words 陣列', () => {
    const source = `lines:
- text: keep me
  start_ms: 10
  end_ms: 20
  words:
  - text: keep
    start_ms: 10
    end_ms: 15
  - text: me
    start_ms: 15
    end_ms: 20
`;
    const lines = parseLyricsFile(source);
    expect(lines).toHaveLength(1);
    expect(lines[0].text).toBe('keep me');
  });

  it('也支援縮排兩格的清單樣式', () => {
    const source = `lines:
  - text: indented
    start_ms: 5
    end_ms: 9
`;
    expect(parseLyricsFile(source)).toHaveLength(1);
    expect(parseLyricsFile(source)[0].text).toBe('indented');
  });

  it('空輸入回傳空陣列', () => {
    expect(parseLyricsFile('')).toEqual([]);
  });

  it('沒有 end_ms 時為 null', () => {
    const source = `lines:
- text: no end
  start_ms: 42
`;
    expect(parseLyricsFile(source)[0].endMs).toBeNull();
  });
});

describe('parseSyncedLyrics', () => {
  it('解析標準 LRC，endMs 為 null', () => {
    const source = `[00:06.22] Hello, it's me
[00:11.84] I was wondering if after all these years you'd like to meet
`;
    const lines = parseSyncedLyrics(source);

    expect(lines).toHaveLength(2);
    expect(lines[0]).toEqual({
      index: 0,
      text: "Hello, it's me",
      startMs: 6_220,
      endMs: null,
    });
  });

  it('忽略 LRC 的 metadata 標籤', () => {
    const source = `[ar:Adele]
[ti:Hello]
[00:01.00] real lyric
`;
    const lines = parseSyncedLyrics(source);
    expect(lines).toHaveLength(1);
    expect(lines[0].text).toBe('real lyric');
  });

  it('一行多個時間標籤會產生多行', () => {
    const source = '[00:01.00][00:05.00] repeated\n';
    const lines = parseSyncedLyrics(source);

    expect(lines).toHaveLength(2);
    expect(lines.map((line) => line.startMs)).toEqual([1_000, 5_000]);
    expect(lines.every((line) => line.text === 'repeated')).toBe(true);
  });

  it('忽略沒有文字的純時間行', () => {
    expect(parseSyncedLyrics('[00:01.00]\n')).toEqual([]);
  });

  it('空輸入回傳空陣列', () => {
    expect(parseSyncedLyrics('')).toEqual([]);
  });
});

describe('parsePlainLyrics', () => {
  it('以固定間隔推算時間軸', () => {
    const lines = parsePlainLyrics('first\n\n  second  \nthird');

    expect(lines).toHaveLength(3);
    expect(lines[0].startMs).toBe(0);
    expect(lines[1].startMs).toBe(PLAIN_LYRICS_INTERVAL_MS);
    expect(lines[2].startMs).toBe(PLAIN_LYRICS_INTERVAL_MS * 2);
    expect(lines[1].text).toBe('second');
    expect(lines[0].endMs).toBe(PLAIN_LYRICS_INTERVAL_MS);
  });

  it('可自訂間隔', () => {
    expect(parsePlainLyrics('a\nb', 1_000)[1].startMs).toBe(1_000);
  });
});

describe('parseLyrics（來源優先序）', () => {
  it('優先使用 lyricsfile', () => {
    const lines = parseLyrics({
      lyricsfile: LYRICS_FILE_SAMPLE,
      syncedLyrics: '[00:01.00] from synced\n',
      plainLyrics: 'from plain',
    });
    expect(lines[0].text).toBe("Hello, it's me");
    expect(lines[0].endMs).not.toBeNull();
  });

  it('lyricsfile 無法解析時退回 syncedLyrics', () => {
    const lines = parseLyrics({
      lyricsfile: 'not a valid lyrics file',
      syncedLyrics: '[00:01.00] from synced\n',
      plainLyrics: 'from plain',
    });
    expect(lines[0].text).toBe('from synced');
    expect(lines[0].endMs).toBeNull();
  });

  it('前兩者都沒有時退回 plainLyrics', () => {
    const lines = parseLyrics({ plainLyrics: 'from plain' });
    expect(lines[0].text).toBe('from plain');
  });

  it('全部為空時回傳空陣列', () => {
    expect(parseLyrics({})).toEqual([]);
    expect(parseLyrics({ lyricsfile: null, syncedLyrics: null, plainLyrics: null })).toEqual([]);
  });
});
