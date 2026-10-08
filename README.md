# Thorium

**A controller-first game library and launcher built for the AYN Thor's two screens.**

Thorium is an Android frontend for retro and emulation handhelds. It scans the games you already own, builds a library you can browse entirely with the physical controls, and uses the Thor's second display as a live companion panel. It is not a generic Android app that happens to run on the Thor: the dual-screen layout and the button handling are designed around this device.

> **Status: early development.** The library, favorites, settings and dual-screen panel work today. Launching games, cover art and downloads are not implemented yet; see the [roadmap](#roadmap).

`Android 10+` · `Kotlin` · `Jetpack Compose` · `English / Español` · `MIT`

---

## What works today

- **Controller-only navigation.** D-pad moves, **A** selects, **B** goes back, **L1/R1** switch tabs, **START** opens the menu, **Y** toggles a favorite. No touch needed.
- **Home, Systems and Favorites.** Rows for recently added games, systems and favorites, with an animated focus ring and a button legend that follows the screen you are on.
- **Dual-screen companion.** The top screen is where you browse; the bottom screen shows details of whatever is in focus (title, system, size, path, controls). It closes when you turn it off in Settings or when the main screen goes away, and the app works the same on a single display.
- **The main UI always opens on the top screen**, even if you start the app from the bottom screen's launcher.
- **Local library scanner.** Finds games in folders named after consoles (`gba`, `3ds`, `switch`, `psx`...), recognizes archives, groups multi-disc games, flags duplicates and ignores junk such as the `._*` files macOS leaves on copied cards. 16 systems are known out of the box.
- **Your folders, your layout.** Scan all storage automatically, or add folders by hand with a controller-friendly folder browser. A **Set up folder** page creates the per-console folders for you.
- **Persistent library.** Games, files and favorites live in a local SQLite database, so your library appears instantly on launch. Games that disappear (an unplugged SD card, for example) are hidden, not forgotten, and come back with their favorites intact.
- **English and Spanish.** Follows the system language, with an override in Settings.

## Roadmap

Thorium is built in phases; each one is designed before it is coded.

| Phase | What | State |
|---|---|---|
| Library, favorites, settings | Scanner, Room database, folder picker | Done |
| Dual-screen companion | Bottom-screen panel, shared state | First version done |
| Emulator profiles + launcher | Configurable profiles, detection, **Play** | Next |
| Metadata and cover art | Pluggable providers, local cache | Planned |
| Configurable sources | Local, NAS/SMB, HTTP, RomM | Planned |
| Download manager | Queue, resume, extract, verify | Planned |
| Polish | Performance, themes, onboarding | Planned |

Planned details worth knowing:

- **Emulators are data, not code.** Each console gets an editable profile (package, activity, how the ROM path is passed). Nothing is hard-coded to a specific emulator.
- **Sources are yours.** Thorium ships no game catalog and does not distribute ROMs. Downloads will only come from sources you configure yourself.

## Controls

| Button | Action |
|---|---|
| D-pad | Move focus |
| A | Select / open |
| B | Back |
| L1 / R1 | Previous / next tab |
| Y | Add or remove favorite |
| START | Menu |
| SELECT | Options *(reserved)* |

The AYN button does not send any event Android apps can read, so it is not used.

## Requirements

- An **AYN Thor** (the dual-screen layout is built around it; it also runs on a single display).
- **Android 10 or newer.** The Thor ships with Android 13, and the per-app language option needs Android 13+.
- **"All files access"** permission. Thorium reads your game folders by real path, which Android only allows with this permission. It asks on first launch and explains why; it only looks inside folders named after consoles and never uploads anything.

## Getting started

1. Install the APK and open Thorium.
2. Grant **All files access** when asked (press **A**, flip the switch, come back with **B**).
3. Put your games in console-named folders on the SD card or internal storage, for example `gba/`, `3ds/` or `switch/`.
   - Not sure how? Open **Settings → Game folders → Add folder...**, pick a folder, and Thorium offers to create the console folders for you.
4. Use **Rescan library** from the START menu whenever you add games.

Supported folder names include `nes`, `snes`, `n64`, `gb`, `gbc`, `gba`, `nds`, `3ds`, `gc`, `wii`, `switch`, `ps1` (or `psx`), `ps2`, `psp`, `genesis` and `dreamcast`.

## Building from source

You do not need Android Studio; the command-line tools are enough.

**Requirements:** JDK 17, the Android SDK (platform 37 and build-tools 36.0.0) and `adb`.

On macOS with Homebrew:

```bash
brew install openjdk@17
brew install --cask android-commandlinetools android-platform-tools

export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools

yes | sdkmanager --sdk_root=$ANDROID_HOME --licenses
sdkmanager --sdk_root=$ANDROID_HOME "platforms;android-37.0" "build-tools;36.0.0" "platform-tools"

# tell Gradle where the SDK is (this file is not committed)
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

Then build, run the tests and install on the device (enable USB debugging first):

```bash
./gradlew assembleDebug
./gradlew test
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Useful while developing on the Thor:

```bash
# grant or revoke the "All files access" permission without opening Settings
adb shell appops set com.thorium.app MANAGE_EXTERNAL_STORAGE allow

# take a screenshot of one display (ids from: adb shell dumpsys SurfaceFlinger --display-id)
adb exec-out screencap -d <display-id> -p > shot.png
```

## Project layout

A multi-module Gradle project; the compiler enforces the boundaries between modules.

| Module | Purpose |
|---|---|
| `:app` | Activities, screens, app-wide state and wiring |
| `:core:model` | Domain models (pure Kotlin) |
| `:core:ui` | Theme, gamepad input mapping, focus-driven components |
| `:data:library` | Console catalog, library scanner, folder setup (pure Kotlin) |
| `:data:db` | Room / SQLite persistence |
| `:feature:display` | Dual-screen support |

Technology: Kotlin, Jetpack Compose, Room, Gradle with a version catalog, Android Gradle Plugin 9.

## Notes for developers

- Everything the user can see is a string resource in `values/` and `values-es/`; the logic layer holds resource ids, not text.
- The input layer deduplicates the events the Thor's controller sends twice (for example **A** also reports `DPAD_CENTER`, and **B** also reports `BACK`).
- Android only allows launching an activity on the second display through `ActivityOptions.setLaunchDisplayId`; Thorium keeps one shared view model so both screens always agree.

## Legal

Thorium is a frontend and library manager. It **does not host, include or distribute game files or any game catalog**, and it does not circumvent copy protection. You are responsible for only using games you are legally entitled to use. Emulator names and console names belong to their respective owners; Thorium is an independent project and is not affiliated with or endorsed by AYN, Nintendo, Sony, Sega or any emulator project.

## License

Thorium is released under the [MIT License](LICENSE). Third-party libraries keep their own licenses; each dependency's license is checked before it is added.
