# ScreenLingo

**Translate anything on your Android screen, in place.**

Tap a floating bubble while any app is open. ScreenLingo reads the text on screen, translates it, and paints each translation directly over the original text, using the page's own background colour so it looks like the app was written in your language.

- No ads, no tracking, no in-app purchases
- Works fully offline, or online with Google Translate, DeepL or Gemini AI for better quality
- Auto-detects the source language

[**Download the latest APK →**](https://github.com/Hafij-DEU/live-translate/releases/latest)

---

## Contents

- [Features](#features)
- [Install](#install)
- [Using ScreenLingo](#using-screenlingo)
- [Translation engines](#translation-engines)
- [Language support](#language-support)
- [Permissions](#permissions)
- [Privacy](#privacy)
- [How it works](#how-it-works)
- [Building from source](#building-from-source)
- [Project structure](#project-structure)
- [Troubleshooting](#troubleshooting)
- [Known limitations](#known-limitations)
- [Credits](#credits)

---

## Features

| Feature | Details |
|---|---|
| Floating bubble | Sits on top of every app. Tap to translate, drag to move, long-press to stop. |
| Quick Settings tile | "Translate screen" tile starts the bubble or translates the current screen. |
| Notification action | The ongoing notification has **Translate** and **Stop** buttons. |
| In-place overlay | Each translated block is drawn over the original, matching its background colour, with dark or light text picked for contrast. |
| On-device OCR | Google ML Kit text recognition for Latin, Chinese, Devanagari, Japanese and Korean scripts. Bundled in the app, no download needed. |
| Four translation engines | Offline (ML Kit), Google Translate, DeepL and Gemini AI. See [Translation engines](#translation-engines). |
| Automatic fallback | If an online engine fails (no internet, bad key, rate limit), the offline engine is used instead and you get a short notice. |
| Language auto-detect | Detects the language of the screen's main text once, ignoring short UI labels. |
| OCR clean-up | Splits headings from body text, rejoins words hyphenated across lines, and leaves number-only text (prices, page numbers) alone. |
| Copy | Long-press the result to copy all translated text to the clipboard. |
| Rotation | Portrait and landscape both work; the capture resizes automatically. |

## Install

1. Download `ScreenLingo-<version>.apk` from the [Releases page](https://github.com/Hafij-DEU/live-translate/releases).
2. Open it on your phone. If Android asks, allow your browser or file manager to install unknown apps.
3. Optional: check the download against `SHA256SUMS` from the same release.

```bash
sha256sum -c SHA256SUMS
```

**Requirements:** Android 10 (API 29) or newer, on an arm64-v8a or x86_64 device.

### Updating

Install the new APK over the old one. Settings and downloaded language packs are kept. Updating stops the bubble, so start it again afterwards.

## Using ScreenLingo

### First start

1. Open **ScreenLingo**.
2. Choose **Translate from** (or leave it on *Auto-detect*) and **Translate to**.
3. Choose the **Text recognition script** that matches the text you want to read. Picking a source language sets this for you.
4. Choose a **Translation engine**. *Offline* works straight away; the online engines are described [below](#translation-engines).
5. Tap **Start floating bubble**.
6. Allow **Display over other apps** when asked, then tap **Start floating bubble** again.
7. Allow notifications (optional; used for the ongoing notification).
8. When Android asks to share your screen, choose **Entire screen** and tap **Start**.

The blue bubble now floats over everything.

### Translating

| Action | Result |
|---|---|
| Tap the bubble | Translates the whole screen. The bubble hides for a moment so it isn't captured. |
| Tap the translation | Closes it and brings the bubble back. |
| Long-press the translation | Copies all translated text. |
| Drag the bubble | Moves it; the position is remembered. |
| Long-press the bubble | Stops ScreenLingo. |
| Quick Settings tile | Starts the bubble, or translates the screen if it is already running. |
| Notification → Translate | Translates the screen (the shade closes first). |
| Notification → Stop | Stops ScreenLingo. |

To add the tile: pull down Quick Settings, tap the edit (pencil) button and drag **Translate screen** into your tiles.

### Offline language packs

The offline engine needs a language pack (about 30 MB) for each language other than English. Packs download automatically the first time a language is used, or you can download the selected pair ahead of time with **Download offline language packs**.

## Translation engines

Choose the engine under **Translation engine** in the app.

| Engine | Quality | Speed | Internet | Key needed | Cost |
|---|---|---|---|---|---|
| **Offline (ML Kit)** | Basic | Fast | Only to download language packs | No | Free |
| **Google Translate** | Good | Fast | Yes | No | Free |
| **DeepL** | Very good | Fast | Yes | Yes | Free tier: 500,000 characters a month |
| **Gemini AI** | Best | Slower (a few seconds) | Yes | Yes | Free tier available |

### Offline (ML Kit)

Default engine. Translation runs on the phone with Google ML Kit's on-device models. Nothing leaves the device. The models are small, so expect literal or occasionally wrong translations.

### Google Translate

No setup. Uses Google Translate's public web endpoint (the one used by the Chrome extension). It is unofficial, so Google may rate-limit or change it at any time; if it fails, ScreenLingo falls back to offline.

### DeepL

1. Create a free account at <https://www.deepl.com/pro-api>.
2. Copy your **Authentication Key** from your DeepL account.
3. Paste it into **DeepL API key** in ScreenLingo.

Free keys end in `:fx` and use DeepL's free API; other keys use the Pro API automatically.

### Gemini AI

Gemini receives every text block on the screen together, so it translates with context, picks the right meaning for single words, and silently fixes OCR mistakes.

1. Get a free API key at <https://aistudio.google.com/apikey>.
2. Paste it into **Gemini API key** in ScreenLingo.
3. Optional: change the **Gemini model**. The default is `gemini-flash-latest`; any model name from the Gemini API works, for example `gemini-2.5-flash`.

API keys are stored only in the app's private storage on your phone and are excluded from Android backups.

## Language support

ScreenLingo has two separate steps, and each supports a different set of languages.

### 1. Reading text from the screen (OCR)

| Script | Example languages |
|---|---|
| Latin | English, Spanish, French, German, Italian, Portuguese, Turkish, Indonesian, Vietnamese, Polish, Dutch, Swedish, … |
| Chinese | Chinese (Simplified and Traditional) |
| Devanagari | Hindi, Marathi, Nepali |
| Japanese | Japanese |
| Korean | Korean |

### 2. Translation

**All engines:** the 59 languages supported by ML Kit:

Afrikaans, Albanian, Arabic, Belarusian, Bengali, Bulgarian, Catalan, Chinese, Croatian, Czech, Danish, Dutch, English, Esperanto, Estonian, Finnish, French, Galician, Georgian, German, Greek, Gujarati, Haitian Creole, Hebrew, Hindi, Hungarian, Icelandic, Indonesian, Irish, Italian, Japanese, Kannada, Korean, Latvian, Lithuanian, Macedonian, Malay, Maltese, Marathi, Norwegian, Persian, Polish, Portuguese, Romanian, Russian, Slovak, Slovenian, Spanish, Swahili, Swedish, Tagalog, Tamil, Telugu, Thai, Turkish, Ukrainian, Urdu, Vietnamese, Welsh.

DeepL supports fewer target languages than this; if DeepL rejects a language, ScreenLingo falls back to offline.

### What this means in practice

| | Can be translated **from** | Can be translated **to** |
|---|---|---|
| Latin-script languages (≈40) | Yes | Yes |
| Chinese, Japanese, Korean, Hindi, Marathi | Yes | Yes |
| Arabic, Belarusian, Bengali, Bulgarian, Georgian, Greek, Gujarati, Hebrew, Kannada, Macedonian, Persian, Russian, Tamil, Telugu, Thai, Ukrainian, Urdu | No (the text can't be read yet) | Yes |

For example, *English → Bengali* works, but *Bengali on screen → English* does not, because ML Kit cannot read Bengali script.

## Permissions

| Permission | Why |
|---|---|
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Draws the bubble and the translation overlay on top of other apps. |
| Screen capture (MediaProjection) | Takes a picture of the screen when you tap the bubble. Android asks every time the bubble starts. |
| Foreground service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`) | Keeps the capture session alive while you use other apps. |
| Notifications (`POST_NOTIFICATIONS`) | Shows the ongoing notification with Translate and Stop buttons. Optional. |
| Internet (`INTERNET`, `ACCESS_NETWORK_STATE`) | Downloads offline language packs and talks to the online engines. |

ScreenLingo does not use accessibility services, does not read notifications, and does not run when the bubble is stopped.

## Privacy

- **Screenshots never leave the phone.** The screen image is processed in memory for text recognition and then discarded. Nothing is saved to storage.
- **Offline engine:** nothing is sent anywhere.
- **Online engines:** only the *recognised text* is sent to the engine you chose (Google, DeepL or Google Gemini), under that provider's terms. Don't use an online engine on screens with passwords or other sensitive text.
- No analytics, no ads, no accounts, no ScreenLingo servers.

## How it works

```
 Tap bubble
     │
     ▼
 Hide bubble ──► Grab latest frame from VirtualDisplay (MediaProjection)
                         │
                         ▼
                 ML Kit text recognition (on-device)
                         │
                         ▼
                 Segments: split headings, join hyphens,
                 drop number-only text, sample background colours
                         │
                         ▼
                 ML Kit language ID (longest blocks only)
                         │
              ┌──────────┴───────────┐
              ▼                      ▼
        Offline ML Kit        Google / DeepL / Gemini
        translator            (falls back to offline on error)
              └──────────┬───────────┘
                         ▼
                 ResultView overlay: one box per segment,
                 drawn at the original position
```

- One `VirtualDisplay` lives for the whole session (Android 14+ allows only one per MediaProjection grant). On rotation it is resized rather than recreated.
- The newest frame is always kept, so a capture works even when the screen hasn't changed.
- The overlay uses screen coordinates, so it lines up with the original text regardless of status-bar or cutout insets.

## Building from source

### Requirements

- JDK 17
- Android SDK with platform `android-35` and build-tools 35
- Gradle 8.9 or newer (the repository has no Gradle wrapper)
- Internet access for the first build (ML Kit comes from Google's Maven repository)

### Build

```bash
git clone https://github.com/Hafij-DEU/live-translate.git
```

```bash
cd live-translate
```

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

Debug build (signed with the Android debug key):

```bash
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Signed release build

Release signing is read from environment variables, so no keystore or password is ever stored in the repository:

| Variable | Meaning |
|---|---|
| `SCREENLINGO_KEYSTORE` | Path to the `.jks` keystore |
| `SCREENLINGO_KEYSTORE_PASSWORD` | Keystore password |
| `SCREENLINGO_KEY_ALIAS` | Key alias |
| `SCREENLINGO_KEY_PASSWORD` | Key password |

Create a keystore once:

```bash
keytool -genkeypair -keystore screenlingo-release.jks -storetype PKCS12 -alias screenlingo -keyalg RSA -keysize 4096 -validity 36500
```

Then set the four variables and build:

```bash
gradle assembleRelease
```

The signed, minified APK is written to `app/build/outputs/apk/release/app-release.apk`. If the variables are not set, Gradle writes an unsigned `app-release-unsigned.apk` instead.

Keep your keystore safe. Android only installs an update when it is signed with the same key as the installed version.

### Install with adb

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Project structure

```
app/src/main/java/com/hafij/screenlingo/
├── MainActivity.java        Settings screen: languages, script, engine, API keys
├── ProjectionActivity.java  Invisible trampoline for overlay/notification/capture consent
├── TranslateService.java    Foreground service: capture, OCR, translation, bubble, overlay
├── QuickTile.java           Quick Settings tile
├── Ocr.java                 ML Kit text recognizers per script
├── Segments.java            Turns OCR blocks into clean translation units
├── TranslateEngine.java     Offline ML Kit translation and language identification
├── OnlineTranslator.java    Google, DeepL and Gemini clients
├── ResultView.java          Overlay that draws translations over the original text
├── Languages.java           Language list, names and OCR script hints
└── Prefs.java               Settings storage
```

The app is plain Java on the Android framework, with no AndroidX UI libraries. Its only dependencies are ML Kit text recognition, translation and language identification.

## Troubleshooting

**The bubble disappeared.**
Android ends screen capture when the phone locks, and also when the app is updated. Open ScreenLingo and tap **Start floating bubble** again.

**"No text found on screen".**
The selected **Text recognition script** may not match the text. Pick the right script, or check that the text is one of the [supported scripts](#1-reading-text-from-the-screen-ocr).

**The text was not translated / stayed in the same language.**
Auto-detect may have decided the text is already in the target language. Set **Translate from** to the actual language.

**"Couldn't detect the language".**
There was too little text to identify. Set **Translate from** manually.

**"Online translation failed (…). Used offline instead."**
The message shows the reason: no internet, an invalid or missing API key, an unsupported language, or the service rate-limiting you. The offline result is shown meanwhile.

**The first translation is slow.**
The offline language pack is downloading (about 30 MB). Later translations take a few seconds or less. Use **Download offline language packs** to fetch them in advance.

**Only one app is translated / the capture is black.**
When Android asks to share the screen, choose **Entire screen**, not a single app. Some apps (banking, DRM video) block screen capture entirely.

**Xiaomi / HyperOS: `adb install` fails with `INSTALL_FAILED_USER_RESTRICTED`.**
Enable **Settings → Additional settings → Developer options → Install via USB**, or install the APK from the phone's file manager.

## Known limitations

- Text recognition covers Latin, Chinese, Devanagari, Japanese and Korean scripts only.
- Very small, stylised or low-contrast text may be misread; Gemini corrects many of these errors.
- The overlay is a snapshot; it does not follow scrolling. Tap to close and translate again.
- The Google engine uses an unofficial endpoint that may stop working without notice.
- Text inside images and video frames is translated like any other text, but motion between capture and display can misalign it.

## Credits

- Text recognition, translation and language identification: [Google ML Kit](https://developers.google.com/ml-kit)
- Bubble and app icon glyph: Material Icons "translate", Apache License 2.0
- Online translation: Google Translate, [DeepL API](https://www.deepl.com/pro-api), [Gemini API](https://ai.google.dev/)
