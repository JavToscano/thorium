#!/usr/bin/env python3
"""Builds catalog.db, the metadata-only game catalog bundled with Thorium.

Input: libretro-database "metadat/no-intro" DAT files (clrmamepro format).
Output: one SQLite file with titles, regions and hashes. It holds no download links.

Usage: python3 -I build_catalog.py <dat-dir> <output.db>

The title rules below must stay identical to TitleNormalizer.kt;
title_fixtures.tsv is checked by the tests on both sides.
"""
import os
import re
import sqlite3
import sys

SCHEMA_VERSION = 1

# DAT file name (without extension) -> Thorium platform id.
PLATFORMS = {
    "Nintendo - Nintendo Entertainment System": "nes",
    "Nintendo - Super Nintendo Entertainment System": "snes",
    "Nintendo - Nintendo 64": "n64",
    "Nintendo - Game Boy": "gb",
    "Nintendo - Game Boy Color": "gbc",
    "Nintendo - Game Boy Advance": "gba",
    "Nintendo - Nintendo DS": "nds",
    "Nintendo - Nintendo 3DS": "3ds",
    "Sega - Mega Drive - Genesis": "genesis",
}

_DISC = re.compile(r"[\s(\[_-]*\b(?:disc|disk|cd)\s*0*(\d+)\b[)\]]?", re.IGNORECASE)
_BRACKETS = re.compile(r"\([^)]*\)|\[[^\]]*\]")
_SPACES = re.compile(r"\s+")
_NON_ALNUM = re.compile(r"[^a-z0-9]+")

_ROM = re.compile(
    r'rom \( name "(?P<file>.*?)" size (?P<size>\d+)'
    r'(?: crc (?P<crc>[0-9A-Fa-f]{8}))?(?: md5 (?P<md5>[0-9A-Fa-f]{32}))?')
_QUOTED = re.compile(r'^\s*(?P<field>name|region|serial) "(?P<value>.*)"\s*$')


def parse_name(file_name):
    """Returns (title, match key, disc) for a file name without extension."""
    m = _DISC.search(file_name)
    disc = int(m.group(1)) if m else None
    name = _DISC.sub(" ", file_name)
    name = _BRACKETS.sub(" ", name)
    name = name.replace("_", " ")
    name = _SPACES.sub(" ", name).strip().rstrip("- ")
    title = name or file_name.strip()
    key = _NON_ALNUM.sub("", title.lower())
    return title, key or file_name.lower(), disc


def parse_dat(text):
    """Yields dicts with name, region, serial, size, crc and md5 for each game with a ROM."""
    game = None
    for line in text.splitlines():
        if line.startswith("game ("):
            game = {}
        elif game is not None and line.startswith(")"):
            if "size" in game:
                yield game
            game = None
        elif game is not None:
            q = _QUOTED.match(line)
            if q and q.group("field") not in game:
                game[q.group("field")] = q.group("value")
                continue
            r = _ROM.search(line)
            if r and "size" not in game:
                game["size"] = int(r.group("size"))
                game["crc"] = int(r.group("crc"), 16) if r.group("crc") else None
                game["md5"] = bytes.fromhex(r.group("md5")) if r.group("md5") else None


def build(dat_dir, output):
    if os.path.exists(output):
        os.remove(output)
    db = sqlite3.connect(output)
    db.executescript("""
        CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
        CREATE TABLE games(
            id INTEGER PRIMARY KEY,
            platform TEXT NOT NULL,
            name TEXT NOT NULL,
            title TEXT NOT NULL,
            key TEXT NOT NULL,
            region TEXT,
            serial TEXT,
            size INTEGER NOT NULL,
            crc INTEGER,
            md5 BLOB
        );
    """)
    counts = {}
    version = ""
    for dat_name, platform in PLATFORMS.items():
        path = os.path.join(dat_dir, dat_name + ".dat")
        with open(path, encoding="utf-8", errors="replace") as f:
            text = f.read()
        m = re.search(r'version "([^"]*)"', text)
        if m:
            version = max(version, m.group(1))
        rows = []
        for g in parse_dat(text):
            title, key, _ = parse_name(g["name"])
            rows.append((platform, g["name"], title, key, g.get("region"), g.get("serial"),
                         g["size"], g["crc"], g["md5"]))
        db.executemany(
            "INSERT INTO games(platform,name,title,key,region,serial,size,crc,md5) VALUES(?,?,?,?,?,?,?,?,?)",
            rows)
        counts[platform] = len(rows)
    db.executescript("""
        CREATE INDEX games_crc ON games(crc);
        CREATE INDEX games_platform_key ON games(platform, key);
    """)
    db.executemany("INSERT INTO meta VALUES(?,?)", [
        ("schema", str(SCHEMA_VERSION)),
        ("source", "libretro-database metadat/no-intro " + version),
        ("license", "CC-BY-SA-4.0 (libretro-database); data originates from No-Intro"),
    ])
    db.execute("PRAGMA user_version = %d" % SCHEMA_VERSION)
    db.commit()
    db.execute("VACUUM")
    db.close()
    return counts


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    result = build(sys.argv[1], sys.argv[2])
    print(sum(result.values()), "games", result)
