package com.hafij.screenlingo;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.mlkit.nl.translate.TranslateLanguage;

import java.util.Locale;

/** User settings: language pair, OCR script and bubble position. */
final class Prefs {
    static final String AUTO = "auto";

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
