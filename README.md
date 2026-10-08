# Thorium

**A game library and launcher for the AYN Thor, made for its two screens and its physical controls.**

Thorium finds the games you already have on your Thor, shows them in a library you can browse without touching the screen, and uses the second display as a live detail panel for whatever you have selected.

> **Early development.** Browsing your library, favorites and the dual-screen panel work today. **Launching games is not available yet**; see the [roadmap](#roadmap).

---

## Features

- **Made for the controls.** Everything works with the D-pad and buttons. No touch needed.
- **Uses both screens.** Browse on the top screen; the bottom screen shows the game you have selected. It also works on a single screen, and you can turn the second screen off in Settings.
- **Finds your games.** It looks in folders named after consoles (`gba`, `3ds`, `switch`...) on your SD card and internal storage, and keeps your library up to date when you rescan.
- **Favorites and recently added.** Home, Systems and Favorites tabs.
- **Set up your folders.** Pick where your games live, and Thorium can create the per-console folders for you.
- **English and Spanish.**

## Roadmap

| | |
|---|---|
| Library, favorites, dual-screen panel, settings | **Available** |
| Launching games with the emulator of your choice | Next |
| Cover art and game information | Planned |
| Your own sources: NAS, SMB, HTTP, RomM | Planned |
| Download manager | Planned |

Emulators will be configurable per console, with no emulator hard-coded. Thorium does not include a game catalog: anything it downloads comes from sources you set up yourself.

## Controls

| Button | Action |
|---|---|
| D-pad | Move |
| A | Select |
| B | Back |
| L1 / R1 | Switch tab |
| Y | Add or remove favorite |
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

You do not need Android Studio. You need JDK 17, the Android SDK (platform 37, build-tools 36.0.0) and `adb`.

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

Run the tests with `./gradlew test`.

</details>

## Legal

Thorium is a library manager and launcher. It does not include or distribute games or game catalogs. Only use games you are legally entitled to use. Thorium is an independent project and is not affiliated with AYN, any console maker or any emulator project.

## License

[MIT](LICENSE)
