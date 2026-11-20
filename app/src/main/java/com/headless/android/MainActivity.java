package com.headless.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** XML layouts, Material 3 roles and fade-through destination switching. */
public class MainActivity extends Activity {
    private static final int DISPLAYS = 0, APPS = 1, SETTINGS = 2;

    private Core core;
    private final View[] pages = new View[3];
    private final View[] nav = new View[3];
    private final View[] indicators = new View[3];
    private final ImageView[] icons = new ImageView[3];
    private final TextView[] labels = new TextView[3];
    private int current = DISPLAYS;

    private LinearLayout tiles;
    private TextView shellText;
    private TextView endpointText;
    private TextView countText;
    private AppAdapter appAdapter;
    private EditText portField;
    private TextView tokenText;
    private String signature = "";

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable step = new Runnable() {
        public void run() {
            try { refresh(); } catch (Throwable ignored) {}
            ticker.postDelayed(this, 400);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            core = Core.init(this);
            setContentView(R.layout.activity_main);
            bind();
        } catch (Throwable e) {
            setContentView(R.layout.activity_main);
            new AlertDialog.Builder(this).setTitle("Could not start")
                    .setMessage(Util.message(e)).setPositiveButton("Close", null).show();
        }
    }

    private void bind() {
        pages[DISPLAYS] = findViewById(R.id.page_displays);
        pages[APPS] = findViewById(R.id.page_apps);
        pages[SETTINGS] = findViewById(R.id.page_settings);
        nav[DISPLAYS] = findViewById(R.id.nav_displays);
        nav[APPS] = findViewById(R.id.nav_apps);
        nav[SETTINGS] = findViewById(R.id.nav_settings);
        indicators[DISPLAYS] = findViewById(R.id.indicator_displays);
        indicators[APPS] = findViewById(R.id.indicator_apps);
        indicators[SETTINGS] = findViewById(R.id.indicator_settings);
        icons[DISPLAYS] = findViewById(R.id.icon_displays);
        icons[APPS] = findViewById(R.id.icon_apps);
        icons[SETTINGS] = findViewById(R.id.icon_settings);
        labels[DISPLAYS] = findViewById(R.id.label_displays);
        labels[APPS] = findViewById(R.id.label_apps);
        labels[SETTINGS] = findViewById(R.id.label_settings);
        for (int i = 0; i < 3; i++) {
            final int index = i;
            nav[i].setOnClickListener(v -> switchTo(index));
        }
        for (int i = 1; i < 3; i++) pages[i].setVisibility(View.GONE);

        tiles = findViewById(R.id.tiles);
        shellText = findViewById(R.id.shell_text);
        endpointText = findViewById(R.id.endpoint_text);
        countText = findViewById(R.id.count_text);

        findViewById(R.id.btn_grant).setOnClickListener(
                v -> Shizuku.get(this).requestPermission());
        findViewById(R.id.btn_open_shizuku).setOnClickListener(v -> {
            Intent intent = getPackageManager().getLaunchIntentForPackage(
                    "moe.shizuku.privileged.api");
            if (intent != null) startActivity(intent);
        });
        findViewById(R.id.btn_create).setOnClickListener(v -> core.createDisplay());
        findViewById(R.id.btn_add_app).setOnClickListener(v -> switchTo(APPS));
        endpointText.setOnClickListener(v -> showToken());

        EditText search = findViewById(R.id.app_search);
        ListView list = findViewById(R.id.app_list);
        appAdapter = new AppAdapter(core.apps());
        list.setAdapter(appAdapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Core.AppInfo entry = appAdapter.item(position);
            core.launch(entry.pkg);
            switchTo(DISPLAYS);
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                appAdapter.filter(s.toString());
            }
            public void afterTextChanged(Editable s) {}
        });

        portField = findViewById(R.id.port_field);
        portField.setText(String.valueOf(Prefs.port(this)));
        tokenText = findViewById(R.id.token_text);
        tokenText.setText(Prefs.token(this));
        findViewById(R.id.btn_save).setOnClickListener(v -> {
            try { Prefs.port(this, Integer.parseInt(portField.getText().toString())); }
            catch (Throwable ignored) {}
            Mcp.start(core, Prefs.port(this), Prefs.token(this));
            toast("Endpoint restarted");
        });
        findViewById(R.id.btn_copy).setOnClickListener(v -> copy(Prefs.token(this)));
        findViewById(R.id.btn_regen).setOnClickListener(v -> {
            Prefs.newToken(this);
            tokenText.setText(Prefs.token(this));
            Mcp.start(core, Prefs.port(this), Prefs.token(this));
        });
        findViewById(R.id.btn_columns_1).setOnClickListener(v -> {
            Prefs.columns(this, 1); toast("Columns: 1");
        });
        findViewById(R.id.btn_columns_2).setOnClickListener(v -> {
            Prefs.columns(this, 2); toast("Columns: 2");
        });
        switchTo(DISPLAYS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            Shizuku.get(this).refresh();
            ticker.removeCallbacks(step);
            ticker.postDelayed(step, 400);
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onPause() {
        try { ticker.removeCallbacks(step); } catch (Throwable ignored) {}
        super.onPause();
    }

    /** Material 3 fade-through between top-level destinations. */
    private void switchTo(int index) {
        if (index == current) return;
        final View outgoing = pages[current];
        final View incoming = pages[index];
        AlphaAnimation out = new AlphaAnimation(1f, 0f);
        out.setDuration(150);
        out.setInterpolator(new DecelerateInterpolator());
        out.setAnimationListener(new Animation.AnimationListener() {
            public void onAnimationStart(Animation a) {}
            public void onAnimationRepeat(Animation a) {}
            public void onAnimationEnd(Animation a) {
                outgoing.setVisibility(View.GONE);
                incoming.setVisibility(View.VISIBLE);
                AlphaAnimation in = new AlphaAnimation(0f, 1f);
                in.setDuration(150);
                in.setInterpolator(new DecelerateInterpolator());
                incoming.startAnimation(in);
            }
        });
        outgoing.startAnimation(out);
        current = index;
        for (int i = 0; i < 3; i++) {
            boolean active = i == index;
            indicators[i].setVisibility(active ? View.VISIBLE : View.INVISIBLE);
            icons[i].setColorFilter(getResources().getColor(active
                    ? R.color.on_secondary_container : R.color.on_surface_variant, null));
            labels[i].setTextColor(getResources().getColor(active
                    ? R.color.on_surface : R.color.on_surface_variant, null));
        }
    }

    private void refresh() {
        if (core == null || tiles == null) return;
        String state = Shizuku.get(this).state();
        shellText.setText("Shizuku: " + state);
        shellText.setTextColor(getResources().getColor(Shizuku.READY.equals(state)
                ? R.color.primary : R.color.error, null));
        String endpoint = Mcp.endpoint();
        endpointText.setText("Agent: " + (endpoint == null ? "offline" : endpoint + "/mcp"));

        List<Core.Slot> slots = core.slots();
        countText.setText(slots.size() + " / 9");
        String next = "";
        for (Core.Slot slot : slots) next += slot.slot + ":" + slot.pkg + "|";
        if (!next.equals(signature)) {
            signature = next;
            tiles.removeAllViews();
            LinearLayout row = null;
            for (int i = 0; i < slots.size(); i++) {
                if (i % 2 == 0) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    tiles.addView(row);
                }
                final Core.Slot slot = slots.get(i);
                DisplayTile tile = new DisplayTile(this, slot);
                tile.setOnClickListener(v -> {
                    signature = "";
                    refresh();
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                        Util.dp(this, 180f), 1f);
                params.setMargins(0, 0, Util.dp(this, 8f), Util.dp(this, 8f));
                tile.setLayoutParams(params);
                row.addView(tile);
            }
        }
        for (int i = 0; i < tiles.getChildCount(); i++) {
            View row = tiles.getChildAt(i);
            if (!(row instanceof LinearLayout)) continue;
            LinearLayout line = (LinearLayout) row;
            for (int j = 0; j < line.getChildCount(); j++) {
                View tile = line.getChildAt(j);
                if (tile instanceof DisplayTile) tile.postInvalidate();
            }
        }
    }

    private void showToken() {
        String endpoint = Mcp.endpoint();
        new AlertDialog.Builder(this).setTitle("Agent endpoint")
                .setMessage("Endpoint: " + (endpoint == null ? "offline" : endpoint + "/mcp")
                        + "\nToken: " + Prefs.token(this))
                .setPositiveButton("Copy", (d, w) -> copy(Prefs.token(this)))
                .setNegativeButton("Close", null).show();
    }

    private void copy(String value) {
        ClipboardManager manager = (ClipboardManager) getSystemService(
                Context.CLIPBOARD_SERVICE);
        if (manager != null) manager.setPrimaryClip(ClipData.newPlainText("HeadlessAndroid", value));
        toast("Copied");
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

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
        public int getCount() { return shown.size(); }
        public Object getItem(int position) { return shown.get(position); }
        public long getItemId(int position) { return position; }

        public View getView(int position, View convert, ViewGroup parent) {
            View view = convert;
            if (view == null) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(Util.dp(MainActivity.this, 16f),
                        Util.dp(MainActivity.this, 10f),
                        Util.dp(MainActivity.this, 16f),
                        Util.dp(MainActivity.this, 10f));
                ImageView icon = new ImageView(MainActivity.this);
                icon.setId(1);
                icon.setLayoutParams(new LinearLayout.LayoutParams(
                        Util.dp(MainActivity.this, 40f), Util.dp(MainActivity.this, 40f)));
                row.addView(icon);
                LinearLayout texts = new LinearLayout(MainActivity.this);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView label = new TextView(MainActivity.this);
                label.setId(2);
                label.setTextSize(16);
                label.setTextColor(getResources().getColor(R.color.on_surface, null));
                TextView pkg = new TextView(MainActivity.this);
                pkg.setId(3);
                pkg.setTextSize(12);
                pkg.setTextColor(getResources().getColor(R.color.on_surface_variant, null));
                texts.addView(label);
                texts.addView(pkg);
                row.addView(texts);
                view = row;
            }
            Core.AppInfo entry = shown.get(position);
            ((TextView) view.findViewById(2)).setText(entry.label);
            ((TextView) view.findViewById(3)).setText(entry.pkg);
            ImageView icon = view.findViewById(1);
            try { icon.setImageDrawable(getPackageManager().getApplicationIcon(entry.pkg)); }
            catch (Throwable ignored) {}
            return view;
        }
    }

    /** M3 card shape: 12dp, surface container, preview fitted inside. */
    private class DisplayTile extends View {
        private final Core.Slot slot;
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint meta = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect dst = new Rect();

        DisplayTile(Context context, Core.Slot slot) {
            super(context);
            this.slot = slot;
            label.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14,
                    getResources().getDisplayMetrics()));
            meta.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11,
                    getResources().getDisplayMetrics()));
            setBackgroundResource(R.drawable.bg_card);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            int pad = Util.dp(getContext(), 12f);
            int footer = Util.dp(getContext(), 44f);
            Bitmap frame = slot.session == null ? null : slot.session.capture();
            int availW = w - pad * 2;
            int availH = h - footer - pad;
            if (frame != null && !frame.isRecycled()) {
                float scale = Math.min((float) availW / frame.getWidth(),
                        (float) availH / frame.getHeight());
                int dw = Math.round(frame.getWidth() * scale);
                int dh = Math.round(frame.getHeight() * scale);
                int left = (w - dw) / 2;
                int top = pad + (availH - dh) / 2;
                dst.set(left, top, left + dw, top + dh);
                canvas.drawBitmap(frame, null, dst, null);
            }
            label.setColor(getResources().getColor(R.color.on_surface, null));
            canvas.drawText(slot.label, pad, h - Util.dp(getContext(), 24f), label);
            meta.setColor(getResources().getColor(R.color.on_surface_variant, null));
            canvas.drawText("D " + slot.displayId + "  ·  " + slot.state,
                    pad, h - Util.dp(getContext(), 8f), meta);
        }
    }
}
