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
- Translate as you type (toggle in the ⋮ menu), throttled to go easy on the endpoint
- Full Arabic interface, Material You colors on Android 12+, dark mode, adaptive/themed launcher icon

## How it works

- **MainActivity** — paste/type text in any language, tap Translate, get the Arabic translation. Auto-detects the source language.
- **ProcessTextActivity** — registers for `ACTION_PROCESS_TEXT`, so "Translator" shows up in the Cut/Copy/Paste popup whenever you select text in *any other app*. Tapping it translates the selection to Arabic in a small popup, with a Copy button.

Both use Google Translate's public `translate_a/single` endpoint (no API key required). It's unofficial and can rate-limit or change without notice, so it sits behind a `TranslationEngine` interface. Text is sent by POST and split into ~4,500-character chunks on paragraph/sentence boundaries, so long selections work and line breaks are preserved.

## Building

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17 and an Android SDK with platform 36 (`local.properties` → `sdk.dir`). `minSdk 26`, `targetSdk 35`.

### Tests

```bash
./gradlew testDebugUnitTest            # JVM unit tests, no network or device needed
./gradlew connectedDebugAndroidTest    # history database tests on a device/emulator
./gradlew lintDebug
```

Note: `connectedDebugAndroidTest` uninstalls the app afterwards, which wipes its history and settings on that device.

### Release build

The release build is shrunk with R8 (about 2 MB vs about 7 MB for debug). To sign it, create a keystore once and a `keystore.properties` in the project root. Both are gitignored, so never commit them, and back up the keystore: you can't publish updates without it.

```bash
keytool -genkeypair -v -keystore translator-release.jks -alias translator \
        -keyalg RSA -keysize 4096 -validity 10000
```

```properties
# keystore.properties
storeFile=translator-release.jks
storePassword=...
keyAlias=translator
keyPassword=...
```

```bash
./gradlew assembleRelease   # → app/build/outputs/apk/release/app-release.apk
```

Without `keystore.properties`, `assembleRelease` still builds, but the APK is unsigned (`app-release-unsigned.apk`).
