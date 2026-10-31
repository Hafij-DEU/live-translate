# Privacy

ScreenLingo has no ads, analytics, tracking or account.

- **Screen capture stays on your device.** When you tap the bubble, the screen image is captured, read with on-device text recognition (Google ML Kit) and discarded. The image is never uploaded or saved.
- **Offline engine (default).** Translation runs on the phone with ML Kit's on-device models. Nothing leaves the device. ML Kit downloads language packs from Google when you first use a language or tap "Download offline language packs".
- **Online engines (optional).** Only if you choose Google Translate, DeepL or Gemini, the *recognised text* (not the image) is sent to that provider, under its terms and privacy policy. Google Translate uses an unofficial web endpoint that may stop working at any time and needs no key; DeepL and Gemini need your own API key, stored only on your device.
- API keys are encrypted with a key held in the Android Keystore and, like all settings, stored only in the app's private storage. They are excluded from Android backups.

Questions: open an issue at https://github.com/Hafij-DEU/live-translate/issues
