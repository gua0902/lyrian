"""Explore ECDICT + CEFR-J so the asset builder can pick sane filters.

Not part of the build; run by hand when the upstream data changes.
"""

import csv
import os
import sys

RAW = os.path.join("build", "datagen", "raw")
ECDICT = os.path.join(RAW, "ecdict.csv")

FIELDS = ["word", "phonetic", "definition", "translation", "pos", "collins",
          "oxford", "tag", "bnc", "frq", "exchange", "detail", "audio"]


def main():
    print("== ecdict header ==")
    with open(ECDICT, encoding="utf-8", newline="") as f:
        reader = csv.reader(f)
        header = next(reader)
        print(" ", header)

    rows = 0
    with_translation = 0
    with_bnc = 0
    with_exchange = 0
    bnc_dist = {}
    samples = []
    tag_counts = {}

    with open(ECDICT, encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            rows += 1
            if rows <= 3:
                samples.append(dict(row))
            if row.get("translation"):
                with_translation += 1
            bnc = row.get("bnc") or ""
            if bnc.isdigit() and int(bnc) > 0:
                with_bnc += 1
            if row.get("exchange"):
                with_exchange += 1
            for t in (row.get("tag") or "").split():
                tag_counts[t] = tag_counts.get(t, 0) + 1
            if rows % 200000 == 0:
                print(f"  ... {rows}")

    print(f"rows={rows} translation={with_translation} bnc={with_bnc} exchange={with_exchange}")
    print("tags:", sorted(tag_counts.items(), key=lambda kv: -kv[1]))
    print("\n== samples ==")
    for s in samples:
        for k in ("word", "phonetic", "translation", "pos", "bnc", "frq", "tag", "exchange"):
            print(f"  {k:12s} = {s.get(k)!r}")
        print()

    # bnc rank coverage: how many rows fall inside each rank bucket
    buckets = [1000, 2000, 5000, 10000, 20000, 30000, 50000, 100000]
    counts = {b: 0 for b in buckets}
    with open(ECDICT, encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            bnc = row.get("bnc") or ""
            if not bnc.isdigit():
                continue
            v = int(bnc)
            for b in buckets:
                if v <= b:
                    counts[b] += 1
    print("\n== rows with bnc <= N ==")
    for b in buckets:
        print(f"  <= {b:6d}: {counts[b]}")


if __name__ == "__main__":
    sys.exit(main())
