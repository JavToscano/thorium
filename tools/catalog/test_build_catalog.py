import os
import sqlite3
import tempfile
import unittest

import build_catalog as b

HERE = os.path.dirname(os.path.abspath(__file__))

DAT = '''clrmamepro (
	name "Nintendo - Game Boy Advance"
	version "2026.08.01"
)

game (
	name "Super Mario Advance (USA, Europe)"
	region "USA"
	serial "AGBE"
	rom ( name "Super Mario Advance (USA, Europe).gba" size 4194304 crc 4A6A6D4C md5 55354D9E3BC9C1FA682B5110E5ED1544 sha1 6E4E9BE9A07580EF267BE9C2EA1BD0730B3BE44A serial "AGBE" )
)
game (
	name "No Hash (Proto)"
	rom ( name "No Hash (Proto).gba" size 100 )
)
game (
	name "Broken entry without a rom"
	region "World"
)
'''


class TitleRules(unittest.TestCase):
    def test_fixtures(self):
        with open(os.path.join(HERE, "title_fixtures.tsv"), encoding="utf-8") as f:
            for line in f:
                if line.startswith("#") or not line.strip("\n"):
                    continue
                name, title, key, disc = (line.rstrip("\n").split("\t") + [""])[:4]
                got = b.parse_name(name)
                self.assertEqual((title, key, int(disc) if disc else None), got, name)


class Parsing(unittest.TestCase):
    def test_parse_dat(self):
        games = list(b.parse_dat(DAT))
        self.assertEqual(2, len(games))
        first = games[0]
        self.assertEqual("USA", first["region"])
        self.assertEqual("AGBE", first["serial"])
        self.assertEqual(0x4A6A6D4C, first["crc"])
        self.assertEqual(16, len(first["md5"]))
        self.assertIsNone(games[1]["crc"])
        self.assertIsNone(games[1].get("region"))

    def test_build_and_query(self):
        with tempfile.TemporaryDirectory() as d:
            for name in b.PLATFORMS:
                text = DAT if name.endswith("Game Boy Advance") else "game (\n)\n"
                with open(os.path.join(d, name + ".dat"), "w", encoding="utf-8") as f:
                    f.write(text)
            out = os.path.join(d, "catalog.db")
            counts = b.build(d, out)
            self.assertEqual(2, counts["gba"])
            db = sqlite3.connect(out)
            self.assertEqual(b.SCHEMA_VERSION, db.execute("PRAGMA user_version").fetchone()[0])
            row = db.execute("SELECT title, region FROM games WHERE platform='gba' AND key='supermarioadvance'").fetchone()
            self.assertEqual(("Super Mario Advance", "USA"), row)
            row = db.execute("SELECT name FROM games WHERE crc=?", (0x4A6A6D4C,)).fetchone()
            self.assertEqual("Super Mario Advance (USA, Europe)", row[0])


if __name__ == "__main__":
    unittest.main()
