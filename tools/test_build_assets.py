"""驗證等級產生器的四層邏輯（不讀 66 MB 的 ECDICT）。

單位測試只覆蓋**規則本身**：標籤映射、BNC 分級、詞頻校正、UNKNOWN。
資料規模與實際命中請看 `python tools/build_assets.py --stats` 的輸出。

Run::

    python tools/test_build_assets.py
"""

from __future__ import annotations

import sys

from build_assets import (
    UNKNOWN_LEVEL,
    bnc_level,
    cap_by_frequency,
    tag_level,
)


def check(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def test_tag_mapping() -> None:
    """第 2 層：ECDICT 考試標籤 → CEFR。"""
    check(tag_level("zk") == "A1", "中考是 A1")
    check(tag_level("gk") == "A2", "高考是 A2")
    check(tag_level("cet4") == "B1", "四級是 B1")
    check(tag_level("cet6") == "B2", "六級是 B2")
    check(tag_level("ky") == "B2", "考研是 B2")
    check(tag_level("gre") == "C1", "GRE 是 C1")
    check(tag_level("") is None, "沒有標籤就回 None（交給下一層）")
    check(tag_level("unknown-tag") is None, "不認識的標籤要忽略")

    # 多個標籤取最低者：respect = gk cet4 cet6 ky toefl → A2
    check(tag_level("gk cet4 cet6 ky toefl") == "A2", "多標籤取最低")

    # toefl/ielts 的特別規則
    check(tag_level("toefl") == "B2", "只有托福是 B2")
    check(tag_level("toefl cet6") == "C1", "托福＋六級 → C1")
    check(tag_level("ielts gre") == "C1", "雅思＋GRE → C1")
    check(tag_level("zk toefl cet6") == "A1", "仍取最低（中考 A1）")


def test_bnc_bands() -> None:
    """第 3 層：BNC 排名分級。"""
    check(bnc_level(0) is None, "0 = 沒有排名")
    check(bnc_level(1) == "A2", "1-2000 → A2")
    check(bnc_level(2000) == "A2", "邊界 2000 → A2")
    check(bnc_level(2001) == "B1", "2001-4000 → B1")
    check(bnc_level(4000) == "B1", "邊界 4000 → B1")
    check(bnc_level(4001) == "B2", "4001-8000 → B2")
    check(bnc_level(8000) == "B2", "邊界 8000 → B2")
    check(bnc_level(8001) == "C1", "8001-15000 → C1")
    check(bnc_level(15000) == "C1", "邊界 15000 → C1")
    check(bnc_level(15001) == "C2", ">15000 → C2")
    check(bnc_level(14689) == "C1", "piercing 14689 → C1")
    check(bnc_level(10986) == "C1", "blah 10986 → C1")


def test_frequency_cap() -> None:
    """詞頻校正：只降不升，而且可以覆蓋前三層。"""
    check(cap_by_frequency("B1", 1476) == "A2", "respect: B1 + 排名 1476 → A2")
    check(cap_by_frequency("C1", 3000) == "B1", "常見卻被判 C1 → 壓到 B1")
    check(cap_by_frequency("A1", 500) == "A1", "A1 不會被拉高")
    check(cap_by_frequency("B2", 6591) == "B2", "排名 6591 不受影響（healing）")
    check(cap_by_frequency("C2", 0) == "C2", "沒有排名就不校正")
    check(cap_by_frequency(UNKNOWN_LEVEL, 100) == UNKNOWN_LEVEL, "UNKNOWN 不參與校正")


def main() -> int:
    tests = [
        test_tag_mapping,
        test_bnc_bands,
        test_frequency_cap,
    ]
    failed = 0
    for test in tests:
        try:
            test()
        except AssertionError as error:
            failed += 1
            print(f"FAIL {test.__name__}: {error}")
        else:
            print(f"ok   {test.__name__}")
    print(f"\n{len(tests) - failed}/{len(tests)} passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
