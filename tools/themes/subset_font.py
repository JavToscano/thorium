#!/usr/bin/env python3
"""Cuts M PLUS Rounded 1c (SIL OFL 1.1) down to the characters Thorium themes need.

The full font is 3.4 MB per weight because of its thousands of kanji; a theme only needs Latin
letters, kana and a few kanji, which come to a few dozen KB. The OFL allows this (the font declares
no Reserved Font Name) as long as the license text travels with it.

Usage: python3 subset_font.py <folder with MPLUSRounded1c-{Regular,Bold}.ttf and OFL.txt> <output folder>
Needs fontTools (pip install fonttools).
"""
import os
import shutil
import sys

from fontTools import subset

# ASCII, Latin-1 (Spanish accents and punctuation), typographic punctuation, all kana,
# and the kanji used by the tab labels (気 入 設 定).
RANGES = list(range(0x20, 0x7F)) + list(range(0xA0, 0x100)) + [
    0x2018, 0x2019, 0x201C, 0x201D, 0x2013, 0x2014, 0x2022, 0x2026, 0x20AC, 0x2605, 0x2192,
] + list(range(0x3040, 0x3100)) + [0x3001, 0x3002, 0x300C, 0x300D] + [ord(c) for c in "気入設定"]


def main(src, out):
    os.makedirs(out, exist_ok=True)
    for weight in ("Regular", "Bold"):
        options = subset.Options()
        options.layout_features = ["*"]
        options.name_IDs = ["*"]
        options.notdef_outline = True
        options.hinting = False
        font = subset.load_font(os.path.join(src, f"MPLUSRounded1c-{weight}.ttf"), options)
        s = subset.Subsetter(options)
        s.populate(unicodes=RANGES)
        s.subset(font)
        target = os.path.join(out, f"MPLUSRounded1c-{weight}-subset.ttf")
        subset.save_font(font, target, options)
        print(target, os.path.getsize(target) // 1024, "KB")
    shutil.copy(os.path.join(src, "OFL.txt"), os.path.join(out, "OFL.txt"))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
