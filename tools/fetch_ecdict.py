"""Download ECDICT csv (66MB) once into build/datagen/raw."""
import os
import sys
import urllib.request

RAW = os.path.join("build", "datagen", "raw")
os.makedirs(RAW, exist_ok=True)
path = os.path.join(RAW, "ecdict.csv")

if os.path.exists(path) and os.path.getsize(path) > 60_000_000:
    print("already have", os.path.getsize(path))
    sys.exit(0)

url = "https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv"
print("downloading", url)
with urllib.request.urlopen(url, timeout=600) as r, open(path, "wb") as f:
    total = 0
    while True:
        chunk = r.read(1 << 20)
        if not chunk:
            break
        f.write(chunk)
        total += len(chunk)
        if total % (10 << 20) < (1 << 20):
            print(f"  {total/1e6:.0f}MB")
print("done", os.path.getsize(path))
