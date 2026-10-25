"""Fetch a few real LRCLIB records for the module-4 blanking report.

Writes ``build/datagen/lyrics/<n>.json`` (git-ignored). The report itself is
produced by ``tools/report/BlankReportTest.kt`` -- see docs/DEVLOG.md.

Lyrics are copyrighted; only the *derived* table (which word was blanked, its
lemma, level and source) is written into the devlog, never the lyrics.
"""

import json
import os
import sys
import urllib.parse
import urllib.request

OUT = os.path.join("build", "datagen", "lyrics")
os.makedirs(OUT, exist_ok=True)

SONGS = [
    ("Adele", "Hello"),
    ("Olivia Rodrigo", "expectations"),
    ("Coldplay", "Yellow"),
]

# 擁有者要求「用另外三首歌」驗收新選詞系統（寫成 4.json / 5.json / 6.json）
EXTRA_SONGS = [
    ("Adele", "Someone Like You"),
    ("Coldplay", "The Scientist"),
    ("Olivia Rodrigo", "drivers license"),
]

UA = "LyrianApp/1.0.0 (https://github.com/antigravity/lyrian)"


def fetch(artist, track):
    query = urllib.parse.urlencode({"artist_name": artist, "track_name": track})
    req = urllib.request.Request(f"https://lrclib.net/api/search?{query}", headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=40) as r:
        return json.load(r)


def write_all(songs, start=1):
    for i, (artist, track) in enumerate(songs, start=start):
        try:
            rows = fetch(artist, track)
        except Exception as e:  # noqa: BLE001
            print(f"FAIL {artist} - {track}: {e}")
            continue
        if not rows:
            print(f"empty {artist} - {track}")
            continue
        # prefer a record that actually has structured/synced lyrics
        rows.sort(key=lambda r: (r.get("lyricsfile") is None, r.get("syncedLyrics") is None))
        best = rows[0]
        path = os.path.join(OUT, f"{i}.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(best, f, ensure_ascii=False)
        print(
            i,
            best["trackName"],
            "-",
            best["artistName"],
            "id=",
            best["id"],
            "lyricsfile=",
            bool(best.get("lyricsfile")),
            "synced=",
            bool(best.get("syncedLyrics")),
            "plain=",
            bool(best.get("plainLyrics")),
        )


if __name__ == "__main__":
    write_all(SONGS, start=1)
    write_all(EXTRA_SONGS, start=len(SONGS) + 1)
    sys.exit(0)
