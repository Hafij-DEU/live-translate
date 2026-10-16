package com.hafij.screenlingo;

import com.google.mlkit.nl.translate.TranslateLanguage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Translation languages offered by ML Kit, with display names and OCR hints. */
final class Languages {
    // Scripts the bundled recognizers cannot read; keep the user's script choice for these.
    private static final Set<String> UNSUPPORTED_SCRIPT = new HashSet<>(Arrays.asList(
            "ar", "be", "bg", "bn", "el", "fa", "gu", "he", "ka", "kn", "mk", "ru", "ta", "te",
            "th", "uk", "ur"));

    private Languages() {}

    static List<String> all() {
        List<String> codes = new ArrayList<>(TranslateLanguage.getAllLanguages());
        codes.sort(Comparator.comparing(Languages::name));
        return codes;
    }

    static String name(String code) {
        if (Prefs.AUTO.equals(code)) return "Auto-detect";
        String name = Locale.forLanguageTag(code).getDisplayLanguage();
        if (name.isEmpty()) return code;
        return name.substring(0, 1).toUpperCase(Locale.getDefault()) + name.substring(1);
    }

    /** Languages written without spaces between words, so OCR lines join without a separator. */
    static boolean joinsWithoutSpaces(String code) {
        return "zh".equals(code) || "ja".equals(code) || "th".equals(code);
    }

    /** The OCR script that reads this language, or null when none of them can. */
    static String scriptFor(String code) {
        switch (code) {
            case "zh":
                return Ocr.CHINESE;
            case "ja":
                return Ocr.JAPANESE;
            case "ko":
                return Ocr.KOREAN;
            case "hi":
            case "mr":
                return Ocr.DEVANAGARI;
            default:
                return Prefs.AUTO.equals(code) || UNSUPPORTED_SCRIPT.contains(code) ? null : Ocr.LATIN;
        }
    }
}
