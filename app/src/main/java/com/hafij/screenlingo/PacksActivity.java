package com.hafij.screenlingo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Lists every offline translation pack with download and remove buttons. */
public class PacksActivity extends Activity {
    private static final int IN_USE = 0;
    private static final int DOWNLOADED = 1;
    private static final int AVAILABLE = 2;
    private static final int[] SECTIONS = {
            R.string.section_in_use, R.string.section_downloaded, R.string.section_available};
    private static final int[] AVATAR_COLORS = {
            R.color.c_violet, R.color.c_blue, R.color.c_teal, R.color.c_green,
            R.color.c_amber, R.color.c_orange, R.color.c_pink, R.color.c_red};

    private final RemoteModelManager models = RemoteModelManager.getInstance();
    private final List<String> all = Languages.all();
    private final Set<String> downloaded = new HashSet<>();
    private final Set<String> downloading = new HashSet<>();
    private final Set<String> inUse = new HashSet<>();
    private final List<String> rows = new ArrayList<>();
    private final List<Integer> rowSections = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private TextView summary;
    private String query = "";

    /** Language codes of the downloaded packs; English is built in and always counts. */
    static Set<String> codes(Set<TranslateRemoteModel> downloadedModels) {
        Set<String> out = new HashSet<>();
        out.add(TranslateLanguage.ENGLISH);
        for (TranslateRemoteModel model : downloadedModels) out.add(model.getLanguage());
        return out;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_packs);
        ListView list = findViewById(R.id.list);
        EdgeToEdge.apply(this, findViewById(R.id.root), findViewById(R.id.header), list);
        summary = findViewById(R.id.summary);
        findViewById(R.id.back).setOnClickListener(v -> finish());

        EditText search = findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.getDefault());
                rebuild();
            }
        });

        Switch wifiOnly = findViewById(R.id.wifi_only);
        wifiOnly.setChecked(Prefs.wifiOnly(this));
        wifiOnly.setOnCheckedChangeListener((b, on) -> Prefs.setWifiOnly(this, on));

        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> onAction(rows.get(position)));
        refresh();
    }

    private void refresh() {
        models.getDownloadedModels(TranslateRemoteModel.class).addOnSuccessListener(found -> {
            downloaded.clear();
            downloaded.addAll(codes(found));
            rebuild();
        });
    }

    private void rebuild() {
        inUse.clear();
        inUse.add(Prefs.target(this));
        String source = Prefs.source(this);
        if (Prefs.AUTO.equals(source)) source = Prefs.lastSource(this);
        if (source != null) inUse.add(source);

        rows.clear();
        rowSections.clear();
        for (int section = IN_USE; section <= AVAILABLE; section++) {
            for (String code : all) {
                if (sectionOf(code) != section || !matches(code)) continue;
                rows.add(code);
                rowSections.add(section);
            }
        }
        int ready = 0;
        for (String code : all) if (downloaded.contains(code)) ready++;
        summary.setText(getString(R.string.packs_summary, ready, all.size()));
        adapter.notifyDataSetChanged();
    }

    private int sectionOf(String code) {
        if (inUse.contains(code)) return IN_USE;
        return downloaded.contains(code) ? DOWNLOADED : AVAILABLE;
    }

    private boolean matches(String code) {
        if (query.isEmpty()) return true;
        Locale locale = Locale.getDefault();
        return Languages.name(code).toLowerCase(locale).contains(query)
                || nativeName(code).toLowerCase(locale).contains(query)
                || code.startsWith(query);
    }

    private void onAction(String code) {
        if (TranslateLanguage.ENGLISH.equals(code) || downloading.contains(code)) return;
        String name = Languages.name(code);
        TranslateRemoteModel model = new TranslateRemoteModel.Builder(code).build();
        if (downloaded.contains(code)) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.delete_pack, name))
                    .setMessage(R.string.delete_pack_body)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.remove, (d, w) -> models.deleteDownloadedModel(model)
                            .addOnSuccessListener(v -> toast(getString(R.string.pack_deleted, name)))
                            .addOnFailureListener(e -> toast(e.getMessage()))
                            .addOnCompleteListener(t -> refresh()))
                    .show();
            return;
        }
        DownloadConditions.Builder conditions = new DownloadConditions.Builder();
        if (Prefs.wifiOnly(this)) conditions.requireWifi();
        downloading.add(code);
        adapter.notifyDataSetChanged();
        models.download(model, conditions.build())
                .addOnSuccessListener(v -> toast(getString(R.string.pack_done, name)))
                .addOnFailureListener(e -> toast(getString(R.string.download_failed, e.getMessage())))
                .addOnCompleteListener(t -> {
                    downloading.remove(code);
                    refresh();
                });
    }

    private boolean onWifi() {
        ConnectivityManager connectivity = getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
    }

    private static String nativeName(String code) {
        Locale locale = Locale.forLanguageTag(code);
        String name = locale.getDisplayLanguage(locale);
        if (name.isEmpty()) return "";
        return name.substring(0, 1).toUpperCase(locale) + name.substring(1);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return rows.get(position).hashCode();
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) view = getLayoutInflater().inflate(R.layout.item_pack, parent, false);
            String code = rows.get(position);
            int section = rowSections.get(position);

            TextView header = view.findViewById(R.id.section);
            boolean first = position == 0 || rowSections.get(position - 1) != section;
            header.setVisibility(first ? View.VISIBLE : View.GONE);
            header.setText(SECTIONS[section]);

            TextView avatar = view.findViewById(R.id.avatar);
            avatar.setText(code.toUpperCase(Locale.ROOT));
            int color = getColor(AVATAR_COLORS[Math.floorMod(code.hashCode(), AVATAR_COLORS.length)]);
            avatar.setBackgroundTintList(ColorStateList.valueOf(color));

            String name = Languages.name(code);
            ((TextView) view.findViewById(R.id.name)).setText(name);
            view.findViewById(R.id.badge).setVisibility(inUse.contains(code) ? View.VISIBLE : View.GONE);

            boolean english = TranslateLanguage.ENGLISH.equals(code);
            boolean busy = downloading.contains(code);
            boolean ready = downloaded.contains(code);
            int stateText;
            if (english) {
                stateText = R.string.pack_built_in;
            } else if (busy) {
                stateText = Prefs.wifiOnly(PacksActivity.this) && !onWifi()
                        ? R.string.pack_waiting_wifi : R.string.pack_downloading;
            } else {
                stateText = ready ? R.string.pack_ready : R.string.pack_missing;
            }
            String nativeName = nativeName(code);
            String line = getString(stateText);
            if (!nativeName.isEmpty() && !nativeName.equals(name)) line = "\u2068" + nativeName + "\u2069 · " + line;
            TextView state = view.findViewById(R.id.state);
            state.setText(line);
            state.setTextColor(getColor(ready && !busy ? R.color.c_green : R.color.text_secondary));

            ProgressBar progress = view.findViewById(R.id.progress);
            ImageButton action = view.findViewById(R.id.action);
            progress.setVisibility(busy ? View.VISIBLE : View.GONE);
            action.setVisibility(busy ? View.GONE : View.VISIBLE);
            action.setOnClickListener(v -> onAction(code));
            action.setClickable(!english);
            if (english) {
                action.setImageResource(R.drawable.ic_check);
                action.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
                action.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.c_green)));
                action.setContentDescription(getString(R.string.pack_built_in));
            } else if (ready) {
                action.setImageResource(R.drawable.ic_delete);
                action.setImageTintList(ColorStateList.valueOf(getColor(R.color.c_red)));
                action.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.tint_pink)));
                action.setContentDescription(getString(R.string.remove));
            } else {
                action.setImageResource(R.drawable.ic_download);
                action.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
                action.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.accent)));
                action.setContentDescription(getString(R.string.download));
            }
            return view;
        }
    }
}
