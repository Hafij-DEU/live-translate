package com.hafij.screenlingo;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.text.util.Linkify;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

/** API keys for the online engines (sealed by {@link KeyVault}) and the About section. */
public class SettingsActivity extends Activity {
    private EditText deeplKey;
    private EditText geminiKey;
    private EditText geminiModel;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        EdgeToEdge.apply(this, findViewById(R.id.root), findViewById(R.id.header), findViewById(R.id.content));
        findViewById(R.id.back).setOnClickListener(v -> finish());

        deeplKey = findViewById(R.id.deepl_key);
        geminiKey = findViewById(R.id.gemini_key);
        geminiModel = findViewById(R.id.gemini_model);
        deeplKey.setText(Prefs.deeplKey(this));
        geminiKey.setText(Prefs.geminiKey(this));
        geminiModel.setText(Prefs.geminiModel(this));
        bindEye(deeplKey, findViewById(R.id.deepl_eye));
        bindEye(geminiKey, findViewById(R.id.gemini_eye));
        findViewById(R.id.save_keys).setOnClickListener(v -> {
            boolean ok = saveKeys();
            Toast.makeText(this, ok ? R.string.keys_saved : R.string.keys_save_failed, Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.clear_keys).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.clear_keys_title)
                .setMessage(R.string.clear_keys_body)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.remove, (d, w) -> {
                    deeplKey.setText("");
                    geminiKey.setText("");
                    saveKeys();
                    Toast.makeText(this, R.string.keys_cleared, Toast.LENGTH_SHORT).show();
                })
                .show());

        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        TextView versionView = findViewById(R.id.version);
        versionView.setText(getString(R.string.app_name) + " " + getString(R.string.about_version, version));
        findViewById(R.id.licenses).setOnClickListener(v -> showLicenses());
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveKeys();
    }

    private boolean saveKeys() {
        return Prefs.setKeys(this, deeplKey.getText().toString(), geminiKey.getText().toString(),
                geminiModel.getText().toString());
    }

    /** The eye button toggles between the masked and the readable key. */
    private void bindEye(EditText field, ImageButton eye) {
        eye.setOnClickListener(v -> {
            boolean masked = field.getTransformationMethod() instanceof PasswordTransformationMethod;
            field.setTransformationMethod(masked ? null : PasswordTransformationMethod.getInstance());
            field.setSelection(field.length());
            eye.setImageResource(masked ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);
            eye.setContentDescription(getString(masked ? R.string.hide_key : R.string.show_key));
        });
    }

    private void showLicenses() {
        TextView body = new TextView(this);
        body.setText(getString(R.string.licenses_body));
        body.setAutoLinkMask(Linkify.WEB_URLS);
        body.setTextSize(15);
        body.setLineSpacing(0, 1.15f);
        body.setTextColor(getColor(R.color.text_primary));
        body.setLinkTextColor(getColor(R.color.accent));
        body.setPadding(64, 24, 64, 0);
        new AlertDialog.Builder(this)
                .setTitle(R.string.licenses)
                .setView(body)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
