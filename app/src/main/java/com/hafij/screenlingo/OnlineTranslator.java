package com.hafij.screenlingo;

import android.content.Context;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cloud translation engines. Each takes the screen's text segments in reading order and returns
 * one translation per segment. A null source means "detect it".
 */
final class OnlineTranslator {
    static final String OFFLINE = "offline";
    static final String GOOGLE = "google";
    static final String DEEPL = "deepl";
    static final String GEMINI = "gemini";

    static final String[] ENGINES = {OFFLINE, GOOGLE, DEEPL, GEMINI};
    static final String[] NAMES = {
            "Offline (ML Kit, private)",
            "Google Translate (online, no key)",
            "DeepL (online, API key)",
            "Gemini AI (online, API key, most accurate)",
    };

    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private static final int GOOGLE_CHUNK_CHARS = 4000;
    private static final int DEEPL_CHUNK_ITEMS = 50;
    private static final String BROWSER_AGENT = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36";

    private OnlineTranslator() {}

    static int indexOf(String engine) {
        for (int i = 0; i < ENGINES.length; i++) {
            if (ENGINES[i].equals(engine)) return i;
        }
        return 0;
    }

    static Task<List<String>> translate(Context context, String engine, String source, String target,
                                        List<String> texts) {
        Context app = context.getApplicationContext();
        TaskCompletionSource<List<String>> result = new TaskCompletionSource<>();
        NETWORK.execute(() -> {
            try {
                List<String> out;
                switch (engine) {
                    case GOOGLE:
                        out = google(source, target, texts);
                        break;
                    case DEEPL:
                        out = deepl(Prefs.deeplKey(app), source, target, texts);
                        break;
                    case GEMINI:
                        out = gemini(Prefs.geminiKey(app), Prefs.geminiModel(app), source, target, texts);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown engine " + engine);
                }
                if (out.size() != texts.size()) {
                    throw new IOException("got " + out.size() + " translations for " + texts.size() + " blocks");
                }
                result.setResult(out);
            } catch (Exception e) {
                result.setException(e);
            }
        });
        return result.getTask();
    }

    // ---------------------------------------------------------------- Google (unofficial web endpoint)

    private static List<String> google(String source, String target, List<String> texts)
            throws IOException, JSONException {
        // The Chrome-extension endpoint takes one q per segment and answers with one entry each;
        // the older translate_a/single endpoint is captcha-walled on many networks.
        String url = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex"
                + "&sl=" + encode(source == null ? "auto" : googleCode(source))
                + "&tl=" + encode(googleCode(target));
        List<String> out = new ArrayList<>();
        for (List<String> chunk : chunkByChars(texts, GOOGLE_CHUNK_CHARS)) {
            StringBuilder body = new StringBuilder();
            for (String text : chunk) {
                if (body.length() > 0) body.append('&');
                body.append("q=").append(encode(text));
            }
            // The default Dalvik user agent is sent to a captcha page; a browser one is not.
            JSONArray answers = new JSONArray(post(url, "application/x-www-form-urlencoded;charset=UTF-8",
                    body.toString(), Collections.singletonMap("User-Agent", BROWSER_AGENT), 20_000));
            for (int i = 0; i < answers.length(); i++) {
                // With sl=auto each entry is [translation, detectedLanguage]; otherwise a plain string.
                JSONArray pair = answers.optJSONArray(i);
                out.add(pair != null ? pair.getString(0) : answers.getString(i));
            }
        }
        return out;
    }

    private static String googleCode(String code) {
        return "zh".equals(code) ? "zh-CN" : code;
    }

    // ---------------------------------------------------------------- DeepL

    private static List<String> deepl(String key, String source, String target, List<String> texts)
            throws IOException, JSONException {
        if (key.isEmpty()) throw new IOException("add a DeepL API key in ScreenLingo settings");
        String host = key.endsWith(":fx") ? "https://api-free.deepl.com" : "https://api.deepl.com";
        List<String> out = new ArrayList<>();
        for (int start = 0; start < texts.size(); start += DEEPL_CHUNK_ITEMS) {
            List<String> chunk = texts.subList(start, Math.min(texts.size(), start + DEEPL_CHUNK_ITEMS));
            JSONObject body = new JSONObject();
            body.put("text", new JSONArray(chunk));
            body.put("target_lang", deeplTarget(target));
            if (source != null) body.put("source_lang", source.toUpperCase(Locale.ROOT));
            String response = post(host + "/v2/translate", "application/json", body.toString(),
                    Collections.singletonMap("Authorization", "DeepL-Auth-Key " + key), 30_000);
            JSONArray translations = new JSONObject(response).getJSONArray("translations");
            for (int i = 0; i < translations.length(); i++) {
                out.add(translations.getJSONObject(i).getString("text"));
            }
        }
        return out;
    }

    private static String deeplTarget(String code) {
        switch (code) {
            case "en":
                return "EN-US";
            case "pt":
                return "PT-BR";
            case "zh":
                return "ZH-HANS";
            default:
                return code.toUpperCase(Locale.ROOT);
        }
    }

    // ---------------------------------------------------------------- Gemini

    private static List<String> gemini(String key, String model, String source, String target, List<String> texts)
            throws IOException, JSONException {
        if (key.isEmpty()) throw new IOException("add a Gemini API key in ScreenLingo settings");
        String targetName = englishName(target);
        String from = source == null ? "whatever language each string is written in" : englishName(source);
        String prompt = "Translate text read by OCR from a phone screen into " + targetName + ".\n"
                + "Each string in the JSON array below is one text block from the same screen, in reading "
                + "order; use the other blocks as context. The source language is " + from + ". OCR may have "
                + "misread characters or broken words, so silently fix obvious recognition errors. Keep names, "
                + "numbers, URLs, code and emoji unchanged. Return a string unchanged if it is already in "
                + targetName + " or cannot be translated. Translate naturally, as a native speaker would "
                + "write it, not word for word.\n"
                + "Reply with a JSON array of exactly " + texts.size() + " strings in the same order.\n\n"
                + new JSONArray(texts);

        JSONObject schema = new JSONObject()
                .put("type", "ARRAY")
                .put("items", new JSONObject().put("type", "STRING"));
        JSONObject body = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject()
                        .put("role", "user")
                        .put("parts", new JSONArray().put(new JSONObject().put("text", prompt)))))
                .put("generationConfig", new JSONObject()
                        .put("temperature", 0.2)
                        .put("responseMimeType", "application/json")
                        .put("responseSchema", schema));
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + encode(model) + ":generateContent";
        String response = post(url, "application/json", body.toString(),
                Collections.singletonMap("x-goog-api-key", key), 90_000);

        JSONArray candidates = new JSONObject(response).optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) throw new IOException("Gemini returned no answer");
        JSONArray parts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts");
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.getJSONObject(i);
            if (!part.optBoolean("thought", false)) text.append(part.optString("text"));
        }
        JSONArray translated = new JSONArray(text.toString().trim());
        List<String> out = new ArrayList<>();
        for (int i = 0; i < translated.length(); i++) out.add(translated.getString(i));
        return out;
    }

    private static String englishName(String code) {
        String name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH);
        return name.isEmpty() ? code : name;
    }

    // ---------------------------------------------------------------- HTTP

    private static String post(String url, String contentType, String body, Map<String, String> headers,
                               int readTimeoutMs) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(readTimeoutMs);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", contentType);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int code = connection.getResponseCode();
            if (code >= 400) {
                throw new IOException("HTTP " + code + ": " + errorMessage(read(connection.getErrorStream())));
            }
            return read(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private static String errorMessage(String body) {
        try {
            JSONObject json = new JSONObject(body);
            JSONObject error = json.optJSONObject("error");
            if (error != null) return error.optString("message", body);
            if (json.has("message")) return json.getString("message");
        } catch (JSONException ignored) {
            // Not JSON; fall through to the raw text.
        }
        return body.length() > 160 ? body.substring(0, 160) : body;
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = stream.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private static List<List<String>> chunkByChars(List<String> texts, int maxChars) {
        List<List<String>> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int size = 0;
        for (String text : texts) {
            if (!current.isEmpty() && size + text.length() > maxChars) {
                chunks.add(current);
                current = new ArrayList<>();
                size = 0;
            }
            current.add(text);
            size += text.length() + 1;
        }
        if (!current.isEmpty()) chunks.add(current);
        return chunks;
    }
}
