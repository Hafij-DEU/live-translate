package com.hafij.screenlingo;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import android.util.Log;

import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Offline translation with ML Kit, caching one translator per language pair. */
final class TranslateEngine {
    private final Map<String, Translator> translators = new HashMap<>();
    private static final String TAG = "ScreenLingo";
    private static final int SAMPLE_CHARS = 2000;

    private final LanguageIdentifier identifier = LanguageIdentification.getClient(
            new LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.3f).build());

    /**
     * Resolves "auto" to the language of the screen's main text. Short fragments (tabs, buttons,
     * the status bar) are often in the system language and drown out the content, so only the
     * longest blocks are sampled. Resolves to null when the language cannot be identified.
     */
    Task<String> resolveSource(String source, List<String> blocks) {
        if (!Prefs.AUTO.equals(source)) return Tasks.forResult(source);
        List<String> longest = new ArrayList<>(blocks);
        longest.sort(Comparator.comparingInt(String::length).reversed());
        StringBuilder sample = new StringBuilder();
        for (String block : longest) {
            if (sample.length() >= SAMPLE_CHARS) break;
            sample.append(block).append('\n');
        }
        return identifier.identifyLanguage(sample.toString()).continueWith(task -> {
            String tag = task.isSuccessful() ? task.getResult() : null;
            String code = tag == null || "und".equals(tag) ? null : TranslateLanguage.fromLanguageTag(tag);
            Log.i(TAG, "detected language " + tag + " -> " + code);
            return code;
        });
    }

    Task<List<String>> translate(String source, String target, List<String> texts) {
        Translator translator = translator(source, target);
        return translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .onSuccessTask(unused -> {
                    List<Task<String>> parts = new ArrayList<>();
                    for (String text : texts) parts.add(translator.translate(text));
                    return Tasks.<String>whenAllSuccess(parts);
                });
    }

    private Translator translator(String source, String target) {
        String key = source + '>' + target;
        Translator translator = translators.get(key);
        if (translator == null) {
            translator = Translation.getClient(new TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(target)
                    .build());
            translators.put(key, translator);
        }
        return translator;
    }

    void close() {
        for (Translator translator : translators.values()) translator.close();
        translators.clear();
        identifier.close();
    }
}
