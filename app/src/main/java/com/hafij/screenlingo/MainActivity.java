package com.hafij.screenlingo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** Settings screen: language pair, OCR script, start/stop and offline pack download. */
public class MainActivity extends Activity {
    private TextView status;
    private Spinner script;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        script = findViewById(R.id.script);

        List<String> targets = Languages.all();
        List<String> sources = new ArrayList<>();
        sources.add(Prefs.AUTO);
        sources.addAll(targets);

        bind(findViewById(R.id.source), names(sources), sources.indexOf(Prefs.source(this)), i -> {
            String code = sources.get(i);
            Prefs.setSource(this, code);
            String hint = Languages.scriptFor(code);
            if (hint != null) script.setSelection(Ocr.indexOf(hint));
        });
        bind(findViewById(R.id.target), names(targets), targets.indexOf(Prefs.target(this)),
                i -> Prefs.setTarget(this, targets.get(i)));
        bind(script, Ocr.NAMES, Ocr.indexOf(Prefs.script(this)),
                i -> Prefs.setScript(this, Ocr.SCRIPTS[i]));

        findViewById(R.id.start).setOnClickListener(v ->
                startActivity(new Intent(this, ProjectionActivity.class)));
        findViewById(R.id.stop).setOnClickListener(v -> {
            if (TranslateService.instance() != null) {
                startService(new Intent(this, TranslateService.class).setAction(TranslateService.ACTION_STOP));
            }
            status.postDelayed(this::updateStatus, 300);
        });
        findViewById(R.id.download).setOnClickListener(v -> downloadModels());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        if (TranslateService.instance() != null) {
            status.setText(R.string.status_on);
        } else if (!Settings.canDrawOverlays(this)) {
            status.setText(R.string.need_overlay);
        } else {
            status.setText(R.string.status_off);
        }
    }

    private void downloadModels() {
        List<String> codes = new ArrayList<>();
        codes.add(Prefs.target(this));
        String source = Prefs.source(this);
        if (!Prefs.AUTO.equals(source)) codes.add(source);
        RemoteModelManager models = RemoteModelManager.getInstance();
        DownloadConditions conditions = new DownloadConditions.Builder().build();
        List<Task<Void>> downloads = new ArrayList<>();
        for (String code : codes) {
            if (TranslateLanguage.ENGLISH.equals(code)) continue; // built in
            downloads.add(models.download(new TranslateRemoteModel.Builder(code).build(), conditions));
        }
        status.setText(R.string.downloading);
        Tasks.whenAll(downloads)
                .addOnSuccessListener(v -> status.setText(R.string.downloaded))
                .addOnFailureListener(e -> status.setText(getString(R.string.download_failed, e.getMessage())));
    }

    private static String[] names(List<String> codes) {
        String[] out = new String[codes.size()];
        for (int i = 0; i < out.length; i++) out[i] = Languages.name(codes.get(i));
        return out;
    }

    private void bind(Spinner spinner, String[] labels, int selected, IntConsumer onSelect) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
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
