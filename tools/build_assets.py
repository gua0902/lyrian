"""Build Lyrian's offline language data from public sources.

Inputs (downloaded by ``tools/fetch_data.py`` / ``tools/fetch_ecdict.py``)::

    build/datagen/raw/cefrj.csv       CEFR-J Vocabulary Profile 1.5 (A1-B2)
    build/datagen/raw/octanove.csv    Octanove Vocabulary Profile C1-C2
    build/datagen/raw/lemma.en.txt    BNC lemma list (lemma -> inflections, with counts)
    build/datagen/raw/ecdict.csv      ECDICT (MIT) - Chinese glosses, IPA, BNC ranks

Outputs (committed, consumed by the app at runtime)::

    app/src/main/assets/data/levels.tsv   word<TAB>level<TAB>source-code
    app/src/main/assets/data/lemma.tsv    inflection<TAB>lemma
    app/src/main/assets/data/freq.tsv     the 2000 most frequent word forms (BNC)
    app/src/main/assets/data/dict/*.json  dictionary shards keyed by first letter

All Chinese glosses are converted to Traditional here, at build time, so the app
never needs an OpenCC runtime (and never downloads a dictionary).

Run::

    python tools/build_assets.py            # write assets
    python tools/build_assets.py --stats    # only print sizes
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import sys
from collections import defaultdict

RAW = os.path.join("build", "datagen", "raw")
ASSETS = os.path.join("app", "src", "main", "assets", "data")

# Only single alphabetic words take part in level lookup / blanking: the blank
# matcher works on one token at a time, so multi-word headwords are useless here.
WORD_RE = re.compile(r"^[a-z][a-z'-]*$")
CJK_RE = re.compile(r"[\u3400-\u9fff]")
# ECDICT stores line breaks inside the translation column as a literal backslash-n.
BR_RE = re.compile(r"\\n")

# How many BNC-ranked words to pull from ECDICT into the dictionary shards.
# Tuned so the whole asset folder stays well under the 5 MB budget.
BNC_LIMIT = 14000

LEVEL_ORDER = ["A1", "A2", "B1", "B2", "C1", "C2"]
LEVEL_RANK = {lv: i for i, lv in enumerate(LEVEL_ORDER)}

# Source codes, kept as one character to keep levels.tsv small.
SRC_CEFRJ = "j"
SRC_OCTANOVE = "o"
SRC_FREQ = "f"
_SRC_LABEL = {
    SRC_CEFRJ: "CEFR-J",
    SRC_OCTANOVE: "Octanove C1/C2",
    SRC_FREQ: "詞頻推估",
}


def read_cefrj(path: str) -> dict[str, str]:
    """CEFR-J profile: one row per sense. Keep the *lowest* level for a word.

    Keeping the lowest is the conservative choice - if a word is A1 in any
    sense, it is a beginner word and must not be blanked for a B1 learner.
    """
    levels: dict[str, str] = {}
    with open(path, encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            level = (row.get("CEFR") or "").strip()
            if level not in LEVEL_RANK:
                continue
            for head in (row.get("headword") or "").split("/"):
                word = head.strip().lower()
                if not WORD_RE.match(word):
                    continue
                if word not in levels or LEVEL_RANK[level] < LEVEL_RANK[levels[word]]:
                    levels[word] = level
    return levels


def read_octanove(path: str) -> dict[str, str]:
    levels: dict[str, str] = {}
    with open(path, encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            level = (row.get("CEFR") or "").strip()
            if level not in LEVEL_RANK:
                continue
            for head in (row.get("headword") or "").split("/"):
                word = head.strip().lower()
                if not WORD_RE.match(word):
                    continue
                if word not in levels or LEVEL_RANK[level] < LEVEL_RANK[levels[word]]:
                    levels[word] = level
    return levels


def read_lemmas(path: str) -> tuple[dict[str, str], dict[str, int]]:
    """Parse ``lemma/count -> inflection,inflection``.

    Returns ``(inflection -> lemma, lemma -> bnc count)``.

    A token that is *itself* a lemma keeps its self-mapping: the raw file lists
    ``it/1213224 -> its,they`` before ``they/841960 -> their,them``, so a naive
    first-wins pass would lemmatise "they" to "it".
    """
    entries: list[tuple[str, int, list[str]]] = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith(";"):
                continue
            head, _, tail = line.partition("->")
            lemma, _, count = head.partition("/")
            lemma = lemma.strip().lower()
            if not WORD_RE.match(lemma):
                continue
            try:
                freq = int(count.strip())
            except ValueError:
                freq = 0
            forms = [t.strip().lower() for t in tail.split(",") if t.strip()]
            entries.append((lemma, freq, forms))

    self_set = {lemma for lemma, _, _ in entries}
    lemma_to_count = {lemma: freq for lemma, freq, _ in entries}

    mapping: dict[str, str] = {}
    for lemma, _, forms in entries:
        for form in forms:
            if not WORD_RE.match(form):
                continue
            if form in self_set or form == lemma:
                continue
            mapping.setdefault(form, lemma)

    return mapping, lemma_to_count


def read_exchange_lemmas(path: str) -> dict[str, str]:
    """Pull the base form out of ECDICT's ``exchange`` column.

    ECDICT's exchange key semantics (the thing that bit us once):
    ``0:`` = this word's **base form**, ``1:`` = **what kind of inflection this
    word itself is** (``1:s`` plural, ``1:pd`` past + past participle, ...).

    So ``years   | 0:year/1:s`` means "years is the plural of year".
    Reading ``1:`` as the lemma produced `years -> s`, `dreaming -> i`,
    `met -> pd` -- nonsense that then showed up in the level column.
    """
    mapping: dict[str, str] = {}
    with open(path, encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            exchange = row.get("exchange") or ""
            if "0:" not in exchange:
                continue
            word = (row.get("word") or "").strip().lower()
            if not WORD_RE.match(word):
                continue
            for part in exchange.split("/"):
                if not part.startswith("0:"):
                    continue
                lemma = part[2:].strip().lower()
                if not WORD_RE.match(lemma) or lemma == word:
                    continue
                mapping.setdefault(word, lemma)
    return mapping


def read_casing(path: str) -> set[str]:
    """找出「字典裡只有大寫形式」的字（專有名詞的判據之一）。

    回傳小寫字串的集合。判斷方式：
      - 出現過全小寫的條目 → 不是專有名詞
      - 只出現過首字母大寫的條目 → 是（`California` 就是這種）

    ⚠️ 必須在**正規化之前**看原始大小寫，所以這是獨立一趟掃描。
    """
    lowercase_seen: set[str] = set()
    capitalized: set[str] = set()

    with open(path, encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            raw = (row.get("word") or "").strip()
            if not WORD_RE.match(raw.lower()):
                continue
            if raw == raw.lower():
                lowercase_seen.add(raw)
            elif raw[0].isupper():
                capitalized.add(raw.lower())

    return capitalized - lowercase_seen


def read_ecdict(path: str):
    """Stream ECDICT rows that are single words with a Chinese gloss."""
    with open(path, encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            word = (row.get("word") or "").strip().lower()
            if not WORD_RE.match(word):
                continue
            translation = (row.get("translation") or "").strip()
            if not translation or not CJK_RE.search(translation):
                continue
            bnc = (row.get("bnc") or "").strip()
            rank = int(bnc) if bnc.isdigit() else 0
            yield word, row, translation, rank


POS_PREFIX_RE = re.compile(r"^([a-z]+\.)\s*(.*)$", re.S)

# 每個字最多保留幾個詞性、每行最長幾個字。放寬會讓分片體積翻倍。
MAX_SENSE_LINES = 4
MAX_SENSE_CHARS = 64


def split_pos_and_gloss(word: str, translation: str) -> tuple[str, str, list[str]]:
    """Return ``(pos abbreviation, gloss, all pos abbreviations seen)``.

    ECDICT writes one sense per line, each optionally prefixed by a part of
    speech (``n. 撣?, ??``). The first line gives the headline pos + gloss;
    a second sense is appended because one line is often too narrow.
    """
    text = BR_RE.sub("\n", translation)
    lines = [p.strip() for p in text.split("\n") if p.strip()]

    pos_items: list[str] = []
    kept: list[str] = []

    for line in lines[:MAX_SENSE_LINES]:
        match = POS_PREFIX_RE.match(line)
        if match:
            pos_items.append(match.group(1))
        normalized = re.sub(r"\s+", " ", line).strip()
        if len(normalized) > MAX_SENSE_CHARS:
            normalized = normalized[:MAX_SENSE_CHARS].rstrip(" ;,，") + "…"
        kept.append(normalized)

    return (pos_items[0] if pos_items else ""), "\n".join(kept), pos_items


# ECDICT transcribes for Chinese learners: the schwa is stored as the *Cyrillic*
# small schwa (it looks identical to the Latin one) and stress is a plain
# apostrophe. Bake real IPA symbols into the asset so the word card is right.
PHONETIC_FIXES = str.maketrans({"ә": "ə", "‘": "ˈ", "’": "ˈ"})
STRESS_RE = re.compile(r"'(?=[a-zæɑɒɔəɜɪʊʌθðʃʒŋ])")


def normalize_phonetic(text: str) -> str:
    return STRESS_RE.sub("ˈ", text.translate(PHONETIC_FIXES)).strip()


def build_entry(word: str, row: dict, translation: str, level: str, to_trad) -> dict:
    """組一筆字典條目。

    欄位刻意壓到一個字母：這些字會重複 29,000 次，鍵名長度直接吃掉體積。

    | 鍵 | 內容 |
    |---|---|
    | `w` | 單字 |
    | `p` | 詞性縮寫摘要（`n./v.`），列表列用 |
    | `c` | **完整釋義**，一行一個詞性，保留 `n. `、`[醫]` 這類前綴——分組與領域標註由讀取端處理 |
    | `i` | IPA |
    | `l` | CEFR 等級（只有真資料才填，推估的留空）|
    | `x` | ECDICT 的詞形變化原始字串，例如 `p:passed/d:passed/i:passing/3:passes` |
    """
    pos, gloss, pos_items = split_pos_and_gloss(word, translation)
    unique_pos = list(dict.fromkeys(pos_items))[:2]
    phonetic = normalize_phonetic((row.get("phonetic") or "").strip())
    return {
        "w": word,
        "p": "/".join(unique_pos) if unique_pos else pos,
        "c": to_trad(gloss),
        "i": f"/{phonetic}/" if phonetic else "",
        "l": level,
        "x": (row.get("exchange") or "").strip(),
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--stats", action="store_true", help="print sizes, write nothing")
    ap.add_argument("--bnc-limit", type=int, default=BNC_LIMIT,
                    help="include ECDICT rows with a BNC rank up to this")
    ap.add_argument("--sample", default="",
                    help="comma separated words to dump after building")
    args = ap.parse_args()
    limit = args.bnc_limit

    cefrj = read_cefrj(os.path.join(RAW, "cefrj.csv"))
    octanove = read_octanove(os.path.join(RAW, "octanove.csv"))
    bnc_lemmas, lemma_counts = read_lemmas(os.path.join(RAW, "lemma.en.txt"))
    exchange = read_exchange_lemmas(os.path.join(RAW, "ecdict.csv"))

    # ECDICT 的 exchange 是「明確宣告」的詞形關係，比 BNC 統計出來的 lemma 表可靠，
    # 所以它優先；BNC 的表只補 ECDICT 沒有的部分（含不規則變化）。
    lemma_map: dict[str, str] = dict(exchange)
    for form, lemma in bnc_lemmas.items():
        lemma_map.setdefault(form, lemma)

    print(f"CEFR-J words      : {len(cefrj)}")
    print(f"Octanove words    : {len(octanove)}")
    print(f"lemma pairs (BNC) : {len(lemma_counts)}")
    print(f"lemma pairs (map) : {len(lemma_map)}")

    from opencc import OpenCC  # imported late: only needed when writing

    cc = OpenCC("s2twp")

    def to_trad(text: str) -> str:
        return cc.convert(text) if CJK_RE.search(text) else text

    # ---------- levels ----------
    levels: dict[str, tuple[str, str]] = {}
    for word, lv in cefrj.items():
        levels[word] = (lv, SRC_CEFRJ)
    for word, lv in octanove.items():
        # CEFR-J is authoritative for A1-B2; Octanove only adds C1/C2 words.
        levels.setdefault(word, (lv, SRC_OCTANOVE))

    # 專有名詞：字典裡只有大寫形式（`California`）→ 挖空時永久排除
    proper_nouns = read_casing(os.path.join(RAW, "ecdict.csv"))
    print(f"proper-noun-only  : {len(proper_nouns)}")

    # ---------- dictionary rows ----------
    #
    # Two passes on purpose: the C1/C2 level of a word can only be derived from
    # its frequency, and the dictionary entry has to carry that level. Building
    # the shards first would leave every frequency-derived word with `l: ""`
    # (that bug shipped once - see the samples in docs/DEVLOG.md).
    candidates: dict[str, tuple[dict, str, int]] = {}
    freq_rows: dict[str, int] = {}

    for word, row, translation, rank in read_ecdict(os.path.join(RAW, "ecdict.csv")):
        if word in candidates:
            continue
        if word not in levels and not (0 < rank <= limit):
            continue
        candidates[word] = (row, translation, rank)
        if rank > 0:
            freq_rows[word] = rank

    # ---------- frequency-derived C1/C2 for words CEFR-J never listed ----------
    derived = 0
    for word, rank in freq_rows.items():
        if word in levels:
            continue
        level = "C1" if rank <= 8000 else "C2"
        levels[word] = (level, SRC_FREQ)
        derived += 1

    # ---------- dictionary shards ----------
    #
    # Only *real* CEFR levels go into the shard (`l`). A frequency-derived level
    # would make an inflected form look like a headword: `met` would then show
    # up as a C2 word instead of "past tense of meet". Leaving `l` empty lets
    # the app prefer the lemma's entry.
    shards: dict[str, list[dict]] = defaultdict(list)
    for word, (row, translation, _rank) in candidates.items():
        level, origin = levels.get(word, ("", ""))
        shard_level = level if origin in (SRC_CEFRJ, SRC_OCTANOVE) else ""
        entry = build_entry(word, row, translation, shard_level, to_trad)
        shards[word[0]].append(entry)
    seen = set(candidates)

    # ---------- top-2000 frequency list ----------
    top = sorted(freq_rows.items(), key=lambda kv: kv[1])[:2000]
    top_words = [w for w, _ in top]

    os.makedirs(ASSETS, exist_ok=True)

    def write(name: str, text: str) -> int:
        path = os.path.join(ASSETS, name)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        data = text.encode("utf-8")
        if not args.stats:
            with open(path, "wb") as f:
                f.write(data)
        return len(data)

    # 第四欄是 BNC 詞頻排名（0 = 未知）。挖空要用它排序：
    # 「同級取詞頻更低者」需要一個可比較的數字，不能只有「是否前 2000」的布林值。
    # 第五欄標記「字典裡只有大寫形式」的專有名詞（1 = 是）。
    level_lines = sorted(
        f"{w}\t{lv}\t{src}\t{freq_rows.get(w, 0)}\t{1 if w in proper_nouns else 0}"
        for w, (lv, src) in levels.items()
    )
    size_levels = write("levels.tsv", "\n".join(level_lines) + "\n")

    # Only keep lemma pairs whose lemma is actually useful to us (it has a level
    # or a dictionary entry) - 85k pairs would triple the asset size for nothing.
    useful = {
        f"{form}\t{lemma}"
        for form, lemma in lemma_map.items()
        if lemma in levels or lemma in seen
    }
    size_lemma = write("lemma.tsv", "\n".join(sorted(useful)) + "\n")
    size_freq = write("freq.tsv", "\n".join(top_words) + "\n")

    size_dict = 0
    for letter in sorted(shards):
        rows = sorted(shards[letter], key=lambda e: e["w"])
        payload = json.dumps(rows, ensure_ascii=False, separators=(",", ":"))
        size_dict += write(f"dict/{letter}.json", payload)

    total = size_levels + size_lemma + size_freq + size_dict
    entries = sum(len(v) for v in shards.values())

    print(f"\ndict entries      : {entries}")
    print(f"level rows        : {len(level_lines)}  (freq-derived {derived})")
    print(f"lemma rows        : {len(useful)}")
    print(f"levels.tsv        : {size_levels/1024:8.1f} KB")
    print(f"lemma.tsv         : {size_lemma/1024:8.1f} KB")
    print(f"freq.tsv          : {size_freq/1024:8.1f} KB")
    print(f"dict/*.json       : {size_dict/1024:8.1f} KB")
    print(f"TOTAL             : {total/1024/1024:8.2f} MB")
    if args.stats:
        print("(--stats: nothing written)")

    if args.sample:
        by_word = {e["w"]: e for rows in shards.values() for e in rows}
        print("\n== samples ==")
        for word in args.sample.split(","):
            word = word.strip().lower()
            entry = by_word.get(word)
            lemma = lemma_map.get(word)
            lv = levels.get(word) or levels.get(lemma or "")
            print(f"  {word:14s} lemma={lemma or '-':14s} level={lv} entry={entry}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
