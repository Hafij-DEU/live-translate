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
        return store(c).getString("deepl_key", "").trim();
    }

    static String geminiKey(Context c) {
        return store(c).getString("gemini_key", "").trim();
    }

    static String geminiModel(Context c) {
        String model = store(c).getString("gemini_model", "").trim();
        return model.isEmpty() ? DEFAULT_GEMINI_MODEL : model;
    }

    static void setKeys(Context c, String deeplKey, String geminiKey, String geminiModel) {
        store(c).edit()
                .putString("deepl_key", deeplKey.trim())
                .putString("gemini_key", geminiKey.trim())
                .putString("gemini_model", geminiModel.trim())
                .apply();
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
