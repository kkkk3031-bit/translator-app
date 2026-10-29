# translator-app

Native Android app that translates any text to Arabic — both from its own screen and system-wide.

## Features

- Translate between 100+ languages (auto-detect source; Arabic is the default target), with a swap button and recently used languages pinned in the picker
- Works system-wide: select text in any app → **Translate** shows a popup with the translation, a language chip, Listen, Share, Open in app, Copy, and **Replace** (for editable fields)
- **Share → Translator** from any app
- History of the last 200 translations: search, star favorites (kept forever), swipe to delete with undo, tap to reopen
- Text-to-speech for translations
- Paste chip when the clipboard has text
- Correct right-to-left layout for Arabic, Hebrew, Persian, Urdu, and more

## How it works

- **MainActivity** — paste/type text in any language, tap Translate, get the Arabic translation. Auto-detects the source language.
- **ProcessTextActivity** — registers for `ACTION_PROCESS_TEXT`, so "Translator" shows up in the Cut/Copy/Paste popup whenever you select text in *any other app*. Tapping it translates the selection to Arabic in a small popup, with a Copy button.

Both use Google Translate's public `translate_a/single` endpoint (no API key required). It's unofficial and can rate-limit or change without notice, so it sits behind a `TranslationEngine` interface. Text is sent by POST and split into ~4,500-character chunks on paragraph/sentence boundaries, so long selections work and line breaks are preserved.

## Building

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest   # unit tests (no network needed)
```

Requires JDK 17 and an Android SDK with platform 36 (`local.properties` → `sdk.dir`). `minSdk 26`, `targetSdk 35`.
