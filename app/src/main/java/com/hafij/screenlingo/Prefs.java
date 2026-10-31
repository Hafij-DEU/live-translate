package com.hafij.screenlingo;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.mlkit.nl.translate.TranslateLanguage;

import java.util.Locale;

/** User settings: language pair, OCR script and bubble position. */
final class Prefs {
    static final String AUTO = "auto";
    static final String DEFAULT_GEMINI_MODEL = "gemini-flash-latest";

    private Prefs() {}

    private static SharedPreferences store(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static String source(Context c) {
        return store(c).getString("source", AUTO);
    }

    static String target(Context c) {
        String fallback = TranslateLanguage.fromLanguageTag(Locale.getDefault().getLanguage());
        return store(c).getString("target", fallback != null ? fallback : TranslateLanguage.ENGLISH);
    }

    static String script(Context c) {
        return store(c).getString("script", Ocr.LATIN);
    }

    static void setSource(Context c, String code) {
        store(c).edit().putString("source", code).apply();
    }

    static void setTarget(Context c, String code) {
        store(c).edit().putString("target", code).apply();
    }

    static void setScript(Context c, String script) {
        store(c).edit().putString("script", script).apply();
    }

    static String engine(Context c) {
        return store(c).getString("engine", OnlineTranslator.OFFLINE);
    }

    static void setEngine(Context c, String engine) {
        store(c).edit().putString("engine", engine).apply();
    }

    static String deeplKey(Context c) {
        return secret(c, "deepl_key");
    }

    static String geminiKey(Context c) {
        return secret(c, "gemini_key");
    }

    /** API keys are kept sealed by {@link KeyVault}; a plain value saved by 0.2.x is sealed on first read. */
    private static String secret(Context c, String name) {
        SharedPreferences s = store(c);
        String legacy = s.getString(name, null);
        if (legacy != null) {
            String sealed = KeyVault.seal(legacy.trim());
            if (sealed != null) s.edit().putString(name + "_sealed", sealed).remove(name).apply();
            return legacy.trim();
        }
        return KeyVault.open(s.getString(name + "_sealed", null)).trim();
    }

    static boolean hasKeys(Context c) {
        return !deeplKey(c).isEmpty() || !geminiKey(c).isEmpty();
    }

    static String geminiModel(Context c) {
        String model = store(c).getString("gemini_model", "").trim();
        return model.isEmpty() ? DEFAULT_GEMINI_MODEL : model;
    }

    /** Seals and saves the keys. Returns false if the Keystore refused; a key is never written in plain text. */
    static boolean setKeys(Context c, String deeplKey, String geminiKey, String geminiModel) {
        String deepl = KeyVault.seal(deeplKey.trim());
        String gemini = KeyVault.seal(geminiKey.trim());
        SharedPreferences.Editor edit = store(c).edit()
                .putString("gemini_model", geminiModel.trim())
                .remove("deepl_key")
                .remove("gemini_key");
        if (deepl != null) edit.putString("deepl_key_sealed", deepl);
        if (gemini != null) edit.putString("gemini_key_sealed", gemini);
        edit.apply();
        return deepl != null && gemini != null;
    }

    static boolean wifiOnly(Context c) {
        return store(c).getBoolean("wifi_only", false);
    }

    static void setWifiOnly(Context c, boolean on) {
        store(c).edit().putBoolean("wifi_only", on).apply();
    }

    /** The language auto-detect found last time, used to warm up its translator. */
    static String lastSource(Context c) {
        return store(c).getString("last_source", null);
    }

    static void setLastSource(Context c, String code) {
        store(c).edit().putString("last_source", code).apply();
    }

    static int bubbleX(Context c, int fallback) {
        return store(c).getInt("bubble_x", fallback);
    }

    static int bubbleY(Context c, int fallback) {
        return store(c).getInt("bubble_y", fallback);
    }

    static void setBubble(Context c, int x, int y) {
        store(c).edit().putInt("bubble_x", x).putInt("bubble_y", y).apply();
    }
}
