package com.headless.android;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Installed-app picker with real launcher icons. No cloning, no copying. */
public class AppsActivity extends Activity {
    private Core core;
    private AppAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            core = Core.init(this);
            int pad = Util.dp(this, 20f);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(getResources().getColor(R.color.bg, null));
            root.setPadding(pad, pad, pad, pad);

            TextView title = new TextView(this);
            title.setText("Apps");
            title.setTextSize(24);
            title.setTextColor(getResources().getColor(R.color.text, null));
            root.addView(title);

            EditText search = new EditText(this);
            search.setHint("Search installed apps");
            search.setTextColor(getResources().getColor(R.color.text, null));
            search.setHintTextColor(getResources().getColor(R.color.muted, null));
            search.setBackgroundResource(R.drawable.bg_card);
            search.setPadding(pad, Util.dp(this, 12f), pad, Util.dp(this, 12f));
            root.addView(search);

            ListView list = new ListView(this);
            adapter = new AppAdapter(core.apps());
            list.setAdapter(adapter);
            list.setOnItemClickListener((parent, view, position, id) -> {
                Core.AppInfo entry = adapter.item(position);
                if (core.launch(entry.pkg) == null) {
                    Toast.makeText(this, "Cannot launch " + entry.label, Toast.LENGTH_SHORT).show();
                    return;
                }
                finish();
            });
            root.addView(list);
            search.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                    adapter.filter(s.toString());
                }
                public void afterTextChanged(Editable s) {}
            });
            setContentView(root);
        } catch (Throwable e) {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(40, 40, 40, 40);
            TextView error = new TextView(this);
            error.setText(Util.message(e));
            error.setTextColor(getResources().getColor(R.color.danger, null));
            root.addView(error);
            setContentView(root);
        }
    }

    private final class AppAdapter extends BaseAdapter {
        private final List<Core.AppInfo> all = new ArrayList<>();
        private List<Core.AppInfo> shown = new ArrayList<>();

        AppAdapter(List<Core.AppInfo> entries) {
            all.addAll(entries);
            shown.addAll(entries);
        }

        void filter(String query) {
            String needle = query == null ? "" : query.trim().toLowerCase(Locale.US);
            List<Core.AppInfo> found = new ArrayList<>();
            for (Core.AppInfo info : all) {
                if (needle.isEmpty() || info.label.toLowerCase(Locale.US).contains(needle)
                        || info.pkg.toLowerCase(Locale.US).contains(needle)) found.add(info);
            }
            shown = found;
            notifyDataSetChanged();
        }

        Core.AppInfo item(int position) { return shown.get(position); }

        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            View view = convert;
            if (view == null) {
                LinearLayout row = new LinearLayout(AppsActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                int pad = Util.dp(AppsActivity.this, 16f);
                row.setPadding(pad, Util.dp(AppsActivity.this, 10f), pad,
                        Util.dp(AppsActivity.this, 10f));
                ImageView icon = new ImageView(AppsActivity.this);
                icon.setId(1);
                icon.setLayoutParams(new LinearLayout.LayoutParams(
                        Util.dp(AppsActivity.this, 40f), Util.dp(AppsActivity.this, 40f)));
                row.addView(icon);
                LinearLayout labels = new LinearLayout(AppsActivity.this);
                labels.setOrientation(LinearLayout.VERTICAL);
                labels.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView label = new TextView(AppsActivity.this);
                label.setId(2);
                label.setTextSize(16);
                label.setTextColor(getResources().getColor(R.color.text, null));
                labels.addView(label);
                TextView pkg = new TextView(AppsActivity.this);
                pkg.setId(3);
                pkg.setTextSize(12);
                pkg.setTextColor(getResources().getColor(R.color.muted, null));
                labels.addView(pkg);
                row.addView(labels);
                view = row;
            }
            Core.AppInfo entry = shown.get(position);
            ((TextView) view.findViewById(2)).setText(entry.label);
            ((TextView) view.findViewById(3)).setText(entry.pkg);
            ImageView icon = view.findViewById(1);
            Drawable drawable = null;
            try { drawable = getPackageManager().getApplicationIcon(entry.pkg); }
            catch (Throwable ignored) {}
            icon.setImageDrawable(drawable);
            return view;
        }
    }
}
