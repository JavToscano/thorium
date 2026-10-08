# Catalog builder

Builds `catalog.db`, the metadata-only game catalog bundled with Thorium (titles, regions, serials, CRC32 and MD5). It contains **no download links**.

## Source and license

Data comes from the [libretro-database](https://github.com/libretro/libretro-database) `metadat/no-intro` DAT files, which originate from [No-Intro](https://no-intro.org). The repository is published under CC-BY-SA-4.0. Whether the upstream No-Intro data adds its own terms has not been confirmed, so check this before publishing a release. Attribution must be shown in the app.

## Build

```sh
# 1. Download the DAT files listed in PLATFORMS (build_catalog.py) into an empty folder.
# 2. Build the database (reads the DATs as plain data; they are never executed).
python3 build_catalog.py <dat-dir> catalog.db
# 3. Run the tests.
python3 -m unittest test_build_catalog
```

Result with the 2026.08.01 DATs: 41,489 games on 9 platforms (nes, snes, n64, gb, gbc, gba, nds, 3ds, genesis), 7.5 MB, about 2.9 MB compressed.

## Schema (version 1)

`meta(key, value)` and `games(id, platform, name, title, key, region, serial, size, crc, md5)` with indexes on `crc` and on `(platform, key)`. `key` is the lower-case alphanumeric match key.

## Keeping the title rules in sync

`parse_name` mirrors `TitleNormalizer.kt`. `title_fixtures.tsv` is read by both this tool's tests and `TitleNormalizerTest`, so a change on one side fails the other.
