package com.hafij.screenlingo;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateRemoteModel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** Home screen: start/stop, language pair, engine, OCR script, and links to packs and Settings. */
public class MainActivity extends Activity {
    private TextView status;
    private View statusDot;
    private View start;
    private View stop;
    private Spinner source;
    private Spinner target;
    private Spinner script;
    private View swap;
    private TextView engineNote;
    private TextView keyLink;
    private TextView packsCount;
    private List<String> sources;
    private List<String> targets;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        EdgeToEdge.apply(this, findViewById(R.id.root), findViewById(R.id.header), findViewById(R.id.content));
        status = findViewById(R.id.status);
        statusDot = findViewById(R.id.status_dot);
        start = findViewById(R.id.start);
        stop = findViewById(R.id.stop);
        source = findViewById(R.id.source);
        target = findViewById(R.id.target);
        script = findViewById(R.id.script);
        swap = findViewById(R.id.swap);
        packsCount = findViewById(R.id.packs_count);

        targets = Languages.all();
        sources = new ArrayList<>();
        sources.add(Prefs.AUTO);
        sources.addAll(targets);

        bind(source, names(sources), sources.indexOf(Prefs.source(this)), i -> {
            String code = sources.get(i);
            Prefs.setSource(this, code);
            String hint = Languages.scriptFor(code);
            if (hint != null) script.setSelection(Ocr.indexOf(hint));
            updateSwap();
        });
        bind(target, names(targets), targets.indexOf(Prefs.target(this)),
                i -> Prefs.setTarget(this, targets.get(i)));
        bind(script, Ocr.NAMES, Ocr.indexOf(Prefs.script(this)),
                i -> Prefs.setScript(this, Ocr.SCRIPTS[i]));
        swap.setOnClickListener(v -> swapLanguages());
        updateSwap();

        engineNote = findViewById(R.id.engine_note);
        keyLink = findViewById(R.id.key_link);
        keyLink.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        buildEngineChips();
        showEngineFields(Prefs.engine(this));

        start.setOnClickListener(v -> startActivity(new Intent(this, ProjectionActivity.class)));
        stop.setOnClickListener(v -> {
            if (TranslateService.instance() != null) {
                startService(new Intent(this, TranslateService.class).setAction(TranslateService.ACTION_STOP));
            }
            status.postDelayed(this::updateStatus, 300);
        });
        findViewById(R.id.packs).setOnClickListener(v -> startActivity(new Intent(this, PacksActivity.class)));
        findViewById(R.id.settings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void buildEngineChips() {
        RadioGroup group = findViewById(R.id.engine);
        LayoutInflater inflater = getLayoutInflater();
        String current = Prefs.engine(this);
        for (int i = 0; i < OnlineTranslator.ENGINES.length; i++) {
            String engine = OnlineTranslator.ENGINES[i];
            RadioButton chip = (RadioButton) inflater.inflate(R.layout.item_engine_chip, group, false);
            chip.setId(View.generateViewId());
            chip.setText(OnlineTranslator.SHORT_NAMES[i]);
            chip.setContentDescription(OnlineTranslator.NAMES[i]);
            chip.setTag(engine);
            group.addView(chip);
            if (engine.equals(current)) chip.setChecked(true);
        }
        group.setOnCheckedChangeListener((g, id) -> {
            String engine = (String) g.findViewById(id).getTag();
            Prefs.setEngine(this, engine);
            showEngineFields(engine);
        });
    }

    private void swapLanguages() {
        String from = Prefs.source(this);
        String to = Prefs.target(this);
        if (Prefs.AUTO.equals(from)) return;
        swap.animate().rotationBy(180).setDuration(250).start();
        source.setSelection(sources.indexOf(to));
        target.setSelection(targets.indexOf(from));
    }

    private void updateSwap() {
        boolean enabled = !Prefs.AUTO.equals(Prefs.source(this));
        swap.setEnabled(enabled);
        swap.setAlpha(enabled ? 1f : 0.4f);
    }

    private void showEngineFields(String engine) {
        boolean deepl = OnlineTranslator.DEEPL.equals(engine);
        boolean gemini = OnlineTranslator.GEMINI.equals(engine);
        keyLink.setVisibility(deepl || gemini ? View.VISIBLE : View.GONE);
        if (deepl || gemini) {
            String name = getString(deepl ? R.string.label_deepl_key : R.string.label_gemini_key);
            boolean saved = !(deepl ? Prefs.deeplKey(this) : Prefs.geminiKey(this)).isEmpty();
            keyLink.setText(getString(saved ? R.string.key_ready : R.string.key_add, name) + "  \u203A");
        }
        switch (engine) {
            case OnlineTranslator.GOOGLE:
                engineNote.setText(R.string.note_google);
                break;
            case OnlineTranslator.DEEPL:
                engineNote.setText(R.string.note_deepl);
                break;
            case OnlineTranslator.GEMINI:
                engineNote.setText(R.string.note_gemini);
                break;
            default:
                engineNote.setText(R.string.note_offline);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        showEngineFields(Prefs.engine(this));
        // The service starts a moment after the capture dialog closes.
        status.postDelayed(this::updateStatus, 800);
        RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel.class)
                .addOnSuccessListener(models -> packsCount.setText(
                        getString(R.string.packs_count, PacksActivity.codes(models).size())));
    }

    private void updateStatus() {
        boolean running = TranslateService.instance() != null;
        int dot;
        if (running) {
            status.setText(R.string.status_on);
            dot = getColor(R.color.ok);
        } else if (!Settings.canDrawOverlays(this)) {
            status.setText(R.string.need_overlay_short);
            dot = getColor(R.color.warn);
        } else {
            status.setText(R.string.status_off);
            dot = 0x99FFFFFF;
        }
        statusDot.setBackgroundTintList(ColorStateList.valueOf(dot));
        start.setVisibility(running ? View.GONE : View.VISIBLE);
        stop.setVisibility(running ? View.VISIBLE : View.GONE);
    }

    private static String[] names(List<String> codes) {
        String[] out = new String[codes.size()];
        for (int i = 0; i < out.length; i++) out[i] = Languages.name(codes.get(i));
        return out;
    }

    private void bind(Spinner spinner, String[] labels, int selected, IntConsumer onSelect) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, labels);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, selected), false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onSelect.accept(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
}
