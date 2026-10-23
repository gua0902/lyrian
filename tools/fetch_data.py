"""Download the raw data sources into build/datagen/raw and print a quick look."""
import os
import urllib.request

RAW = os.path.join("build", "datagen", "raw")
os.makedirs(RAW, exist_ok=True)

SOURCES = {
    "cefrj.csv": "https://raw.githubusercontent.com/openlanguageprofiles/olp-en-cefrj/master/cefrj-vocabulary-profile-1.5.csv",
    "octanove.csv": "https://raw.githubusercontent.com/openlanguageprofiles/olp-en-cefrj/master/octanove-vocabulary-profile-c1c2-1.0.csv",
    "lemma.en.txt": "https://raw.githubusercontent.com/skywind3000/ECDICT/master/lemma.en.txt",
}


def fetch(name, url):
    path = os.path.join(RAW, name)
    if os.path.exists(path) and os.path.getsize(path) > 0:
        print(f"cached  {name} ({os.path.getsize(path)/1e6:.2f}MB)")
        return path
    print(f"fetch   {name} ...")
    with urllib.request.urlopen(url, timeout=120) as r, open(path, "wb") as f:
        f.write(r.read())
    print(f"done    {name} ({os.path.getsize(path)/1e6:.2f}MB)")
    return path


for n, u in SOURCES.items():
    fetch(n, u)

for n in ("cefrj.csv", "octanove.csv", "lemma.en.txt"):
    path = os.path.join(RAW, n)
    print(f"\n===== {n} =====")
    with open(path, encoding="utf-8") as f:
        for i, line in enumerate(f):
            if i >= 6:
                break
            print(" ", line.rstrip()[:160])
