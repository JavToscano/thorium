# Thorium

**A game library and launcher for the AYN Thor, made for its two screens and its physical controls.**

Thorium finds the games you already have on your Thor, shows them in a library you can browse without touching the screen, and uses the second display as a live detail panel for whatever you have selected.

> **Early development.** Browsing your library, cover art, downloads from your own sources and launching games work today, but so far launching has only been tested with the Azahar 3DS emulator; see the [roadmap](#roadmap).

---

## Features

- **Made for the controls.** Everything works with the D-pad and buttons. No touch needed.
- **Uses both screens.** Browse on the top screen; the bottom screen shows the game you have selected. It also works on a single screen, and you can turn the second screen off in Settings.
- **Finds your games.** It looks in folders named after consoles (`gba`, `3ds`, `switch`...) on your SD card and internal storage, and keeps your library up to date when you rescan.
- **Favorites and recently added.** Home, Systems and Favorites tabs.
- **Set up your folders.** Pick where your games live, and Thorium can create the per-console folders for you.
- **Cover art.** Thorium recognises your games against a bundled catalog of game names and checksums (no games, no download links) and shows their box art, title screen and a gameplay snap, downloaded from the libretro thumbnail server. You can turn the downloads off in Settings.
- **Play.** The Play button starts a game in an installed emulator. Emulators are described by data files, not hard-coded. **Azahar (3DS)** is supported so far.
- **Your own sources and downloads.** Add web servers, JSON catalogs, Internet Archive collections or local folders, browse them from the Downloads tab and download straight into the right console folder, with pause, resume, retries and ZIP/7z extraction. Thorium does not include any source or game catalog.
- **English and Spanish.**

## Roadmap

| | |
|---|---|
| Library, favorites, dual-screen panel, settings | **Available** |
| Cover art, regions and game recognition | **Available** |
| Download manager and your own sources (HTTP, JSON catalog, Internet Archive, local folder) | **Available** |
| Launching games (Azahar for 3DS) | **Available** |
| More emulators, choosing between emulators, play history | Next |
| More sources: SMB, FTP, RomM | Planned |

Emulators are configured per console, with none hard-coded. Anything Thorium downloads comes from sources you set up yourself.

## Controls

| Button | Action |
|---|---|
| D-pad | Move |
| A | Select |
| B | Back |
| L1 / R1 | Switch tab |
| Y | Add or remove favorite (filter a source listing; remove an item in lists) |
| START | Menu |

## Getting started

Thorium needs an **AYN Thor** (or another Android 10+ device) and the **All files access** permission, which it asks for the first time you open it. It only looks inside folders named after consoles and never uploads anything.

1. Open Thorium and allow **All files access** (press **A**, turn on the switch, then press **B** to come back).
2. Put your games in folders named after the console, on the SD card or internal storage, such as `gba/`, `3ds/` or `switch/`.
   - To have the folders created for you, go to **Settings → Game folders → Add folder...** and pick a folder.
3. Choose **Rescan library** in the START menu after adding games.

Supported folder names: `nes`, `snes`, `n64`, `gb`, `gbc`, `gba`, `nds`, `3ds`, `gc`, `wii`, `switch`, `ps1` (or `psx`), `ps2`, `psp`, `genesis`, `dreamcast`.

No ready-made APK is published yet, so for now you need to build it yourself (below).

<details>
<summary><strong>Build from source</strong></summary>

You do not need Android Studio. You need JDK 17, the Android SDK (platform 37, build-tools 36.0.0), `adb` and `python3`. The first build needs internet access: it downloads the No-Intro DAT files (pinned by checksum) and turns them into the bundled catalog database.

On macOS with Homebrew:

```bash
brew install openjdk@17
brew install --cask android-commandlinetools android-platform-tools

export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools

yes | sdkmanager --sdk_root=$ANDROID_HOME --licenses
sdkmanager --sdk_root=$ANDROID_HOME "platforms;android-37.0" "build-tools;36.0.0" "platform-tools"
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

Then build and install on your Thor (with USB debugging enabled):

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run the tests with `./gradlew test` (and `python3 -m unittest test_build_catalog` inside `tools/catalog`).

</details>

## Legal

Thorium is a library manager and launcher. It does not include or distribute games, download links or ROM sources; it only bundles names, regions and checksums of known games so it can recognise the ones you have. Only use games you are legally entitled to use. Thorium is an independent project and is not affiliated with AYN, any console maker or any emulator project.

## Credits

- Game names, regions and checksums come from the [No-Intro](https://no-intro.org) DAT files, distributed by [libretro-database](https://github.com/libretro/libretro-database) under [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). The catalog Thorium builds from them keeps that license.
- Cover art, title screens and snaps are served by the libretro thumbnail server and belong to their respective rights holders.
- Open-source components: Jetpack Compose, Room, Kotlin and kotlinx, OkHttp and Apache Commons Compress (Apache License 2.0); XZ for Java (0BSD).

## License

[MIT](LICENSE) for the Thorium code. The generated game catalog is covered by CC BY-SA 4.0, as described above.
