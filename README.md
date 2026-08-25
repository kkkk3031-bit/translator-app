# translator-app

Native Android app that translates any text to Arabic — both from its own screen and system-wide.

## How it works

- **MainActivity** — paste/type text in any language, tap Translate, get the Arabic translation. Auto-detects the source language.
- **ProcessTextActivity** — registers for `ACTION_PROCESS_TEXT`, so "Translator" shows up in the Cut/Copy/Paste popup whenever you select text in *any other app*. Tapping it translates the selection to Arabic in a small popup, with a Copy button.

Both use Google Translate's public `translate_a/single` endpoint (no API key required).

## Building

No Gradle wrapper is checked in — build with a local Gradle install:

```bash
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires Android SDK with `compileSdk 34`, `minSdk 26`, and Gradle 8.5+.
