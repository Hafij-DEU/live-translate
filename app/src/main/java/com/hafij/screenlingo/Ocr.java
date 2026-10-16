package com.hafij.screenlingo;

import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

/** On-device text recognizers, one per supported script. */
final class Ocr {
    static final String LATIN = "latin";
    static final String CHINESE = "chinese";
    static final String DEVANAGARI = "devanagari";
    static final String JAPANESE = "japanese";
    static final String KOREAN = "korean";

    static final String[] SCRIPTS = {LATIN, CHINESE, DEVANAGARI, JAPANESE, KOREAN};
    static final String[] NAMES = {
            "Latin (English, Spanish, French, Turkish…)",
            "Chinese",
            "Devanagari (Hindi, Marathi, Nepali…)",
            "Japanese",
            "Korean",
    };

    private Ocr() {}

    static TextRecognizer create(String script) {
        switch (script) {
            case CHINESE:
                return TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
            case DEVANAGARI:
                return TextRecognition.getClient(new DevanagariTextRecognizerOptions.Builder().build());
            case JAPANESE:
                return TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
            case KOREAN:
                return TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
            default:
                return TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        }
    }

    static int indexOf(String script) {
        for (int i = 0; i < SCRIPTS.length; i++) {
            if (SCRIPTS[i].equals(script)) return i;
        }
        return 0;
    }
}
