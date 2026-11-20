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
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Single activity with three top-level destinations.
 * Structure, type scale, colour roles, shape scale and fade-through motion follow
 * the Material 3 guidance in material-components-android/docs.
 */
public class MainActivity extends Activity {
    private static final int displaysTab = 0, appsTab = 1, settingsTab = 2;
    private Core core;
    private FrameLayout pages;
    private final View[] pageViews = new View[3];
    private final View[] navItems = new View[3];
    private int current = displaysTab;

    private LinearLayout tiles;
    private TextView countText;
    private TextView shellText;
    private TextView endpointText;
    private AppAdapter appAdapter;
    private EditText portField;
    private TextView tokenText;
    private String tileSignature = "";
    private int selected = -1;

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
            buildUi();
        } catch (Throwable e) {
            showError(e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            Shizuku.get(this).refresh();
            if (core == null) core = Core.init(this);
            ticker.removeCallbacks(step);
            ticker.postDelayed(step, 400);
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onPause() {
        try { ticker.removeCallbacks(step); } catch (Throwable ignored) {}
        super.onPause();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(col(R.color.surface_container_lowest));

        pages = new FrameLayout(this);
        pages.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        pageViews[displaysTab] = buildDisplays();
        pageViews[appsTab] = buildApps();
        pageViews[settingsTab] = buildSettings();
        for (int i = 0; i < 3; i++) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            pages.addView(pageViews[i], params);
        }
        root.addView(pages);
        root.addView(buildNavigation());
        setContentView(root);
        switchTo(displaysTab, false);
    }

    /** Material 3 navigation bar: 3 destinations, 80dp, surface container, icon + label. */
    private View buildNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(col(R.color.surface_container_low));
        nav.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(80)));
        int[] icons = {R.drawable.ic_displays, R.drawable.ic_apps, R.drawable.ic_settings};
        String[] labels = {getString(R.string.displays), getString(R.string.apps),
                getString(R.string.settings)};
        for (int i = 0; i < 3; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            View indicator = new View(this);
            indicator.setBackgroundResource(R.drawable.bg_indicator);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(56), dp(32));
            item.addView(indicator, ip);
            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            icon.setColorFilter(col(R.color.on_surface_variant));
            item.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView label = text(labels[i], 14, R.color.on_surface_variant);
            item.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            item.setTag(new View[]{indicator, icon, label});
            item.setOnClickListener(v -> switchTo(index, true));
            navItems[i] = item;
            nav.addView(item);
        }
        return nav;
    }

    /** Material 3 fade-through: outgoing fades out, incoming fades in, 300ms total. */
    private void switchTo(int index, boolean animate) {
        if (index == current && pageViews[index].getVisibility() == View.VISIBLE) return;
        final View outgoing = pageViews[current];
        final View incoming = pageViews[index];
        if (!animate) {
            outgoing.setVisibility(View.GONE);
            incoming.setVisibility(View.VISIBLE);
        } else {
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
        }
        current = index;
        for (int i = 0; i < 3; i++) {
            View[] parts = (View[]) navItems[i].getTag();
            boolean active = i == index;
            parts[0].setVisibility(active ? View.VISIBLE : View.INVISIBLE);
            ((ImageView) parts[1]).setColorFilter(col(active
                    ? R.color.on_secondary_container : R.color.on_surface_variant));
            ((TextView) parts[2]).setTextColor(col(active
                    ? R.color.on_surface : R.color.on_surface_variant));
        }
    }

    private View buildDisplays() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column();
        box.setPadding(dp(20), dp(20), dp(20), dp(20));

        box.addView(text(getString(R.string.app_name), 22, R.color.on_surface));
        box.addView(text(getString(R.string.tagline), 12, R.color.on_surface_variant));
        addGap(box, 16);

        LinearLayout session = card();
        session.addView(text(getString(R.string.session), 11, R.color.on_surface_variant));
        shellText = text("Shizuku: checking", 14, R.color.on_surface);
        endpointText = text("Agent: checking", 12, R.color.on_surface_variant);
        session.addView(shellText);
        session.addView(endpointText);
        LinearLayout actions = row();
        Button grant = button(getString(R.string.grant), false);
        grant.setOnClickListener(v -> Shizuku.get(this).requestPermission());
        Button open = button(getString(R.string.open_shizuku), false);
        open.setOnClickListener(v -> {
            Intent intent = getPackageManager().getLaunchIntentForPackage(
                    "moe.shizuku.privileged.api");
            if (intent != null) startActivity(intent);
        });
        actions.addView(weighted(grant));
        actions.addView(weighted(open));
        session.addView(actions);
        box.addView(session);
        addGap(box, 20);

        LinearLayout header = row();
        TextView title = text(getString(R.string.displays), 16, R.color.on_surface);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        countText = text("0", 11, R.color.on_surface_variant);
        header.addView(title);
        header.addView(countText);
        box.addView(header);
        addGap(box, 8);

        LinearLayout controls = row();
        Button create = button(getString(R.string.create_display), false);
        create.setOnClickListener(v -> core.createDisplay());
        Button add = button(getString(R.string.add_app), true);
        add.setOnClickListener(v -> switchTo(appsTab, true));
        controls.addView(weighted(create));
        controls.addView(weighted(add));
        box.addView(controls);
        addGap(box, 12);

        tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.VERTICAL);
        box.addView(tiles);
        scroll.addView(box);
        return scroll;
    }

    private View buildApps() {
        LinearLayout box = column();
        box.setPadding(dp(20), dp(20), dp(20), dp(20));
        box.addView(text(getString(R.string.apps), 22, R.color.on_surface));
        addGap(box, 12);
        EditText search = new EditText(this);
        search.setHint(getString(R.string.search));
        search.setHintTextColor(col(R.color.on_surface_variant));
        search.setTextColor(col(R.color.on_surface));
        search.setBackgroundResource(R.drawable.bg_field);
        search.setPadding(dp(16), dp(12), dp(16), dp(12));
        box.addView(search);

        appAdapter = new AppAdapter(core.apps());
        ListView list = new ListView(this);
        list.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        list.setAdapter(appAdapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Core.AppInfo entry = appAdapter.item(position);
            core.launch(entry.pkg);
            switchTo(displaysTab, true);
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                appAdapter.filter(s.toString());
            }
            public void afterTextChanged(Editable s) {}
        });
        box.addView(list);
        return box;
    }

    private View buildSettings() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = column();
        box.setPadding(dp(20), dp(20), dp(20), dp(20));
        box.addView(text(getString(R.string.settings), 22, R.color.on_surface));
        addGap(box, 16);

        LinearLayout endpoint = card();
        endpoint.addView(text(getString(R.string.port), 12, R.color.on_surface_variant));
        portField = new EditText(this);
        portField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        portField.setText(String.valueOf(Prefs.port(this)));
        portField.setTextColor(col(R.color.on_surface));
        portField.setBackgroundResource(R.drawable.bg_field);
        portField.setPadding(dp(12), dp(12), dp(12), dp(12));
        endpoint.addView(portField);
        Button save = button(getString(R.string.save), true);
        save.setOnClickListener(v -> {
            try { Prefs.port(this, Integer.parseInt(portField.getText().toString())); }
            catch (Throwable ignored) {}
            Mcp.start(core, Prefs.port(this), Prefs.token(this));
            toast("Endpoint restarted");
        });
        endpoint.addView(save);
        endpoint.addView(text(getString(R.string.token), 12, R.color.on_surface_variant));
        tokenText = text(Prefs.token(this), 14, R.color.on_surface);
        tokenText.setTextIsSelectable(true);
        endpoint.addView(tokenText);
        Button copy = button(getString(R.string.copy), false);
        copy.setOnClickListener(v -> {
            ClipboardManager manager = (ClipboardManager) getSystemService(
                    Context.CLIPBOARD_SERVICE);
            if (manager != null) manager.setPrimaryClip(
                    ClipData.newPlainText("HeadlessAndroid", Prefs.token(this)));
        });
        endpoint.addView(copy);
        Button regen = button(getString(R.string.new_token), false);
        regen.setOnClickListener(v -> {
            Prefs.newToken(this);
            tokenText.setText(Prefs.token(this));
            Mcp.start(core, Prefs.port(this), Prefs.token(this));
        });
        endpoint.addView(regen);
        box.addView(endpoint);
        addGap(box, 16);

        LinearLayout grid = card();
        grid.addView(text(getString(R.string.columns), 12, R.color.on_surface_variant));
        LinearLayout columns = row();
        for (int value = 1; value <= 2; value++) {
            final int choice = value;
            Button item = button(String.valueOf(value), false);
            item.setOnClickListener(v -> {
                Prefs.columns(this, choice);
                toast("Columns set to " + choice);
            });
            columns.addView(weighted(item));
        }
        grid.addView(columns);
        box.addView(grid);
        scroll.addView(box);
        return scroll;
    }

    private void refresh() {
        if (core == null || tiles == null) return;
        String state = Shizuku.get(this).state();
        shellText.setText("Shizuku: " + state);
        shellText.setTextColor(col(Shizuku.READY.equals(state)
                ? R.color.primary : R.color.error));
        String endpoint = Mcp.endpoint();
        endpointText.setText("Agent: " + (endpoint == null ? "offline" : endpoint + "/mcp"));
        endpointText.setOnClickListener(v -> showToken());

        List<Core.Slot> slots = core.slots();
        countText.setText(slots.size() + " / 9");
        String next = "";
        for (Core.Slot slot : slots) next += slot.slot + ":" + slot.pkg + "|";
        if (!next.equals(tileSignature)) {
            tileSignature = next;
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
                    selected = slot.slot;
                    tileSignature = "";
                    refresh();
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0, dp(180), 1f);
                params.setMargins(0, 0, dp(8), dp(8));
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
                .setPositiveButton(getString(R.string.copy), (d, w) -> {
                    ClipboardManager manager = (ClipboardManager) getSystemService(
                            Context.CLIPBOARD_SERVICE);
                    if (manager != null) manager.setPrimaryClip(
                            ClipData.newPlainText("HeadlessAndroid", Prefs.token(this)));
                })
                .setNegativeButton("Close", null).show();
    }

    private void showError(Throwable e) {
        LinearLayout root = column();
        root.setFitsSystemWindows(true);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.addView(text("Could not start", 22, R.color.on_surface));
        root.addView(text(Util.message(e), 14, R.color.error));
        setContentView(root);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout card() {
        LinearLayout layout = column();
        layout.setBackgroundResource(R.drawable.bg_card);
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));
        return layout;
    }

    private View weighted(View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                dp(40), 1f);
        params.setMargins(dp(4), dp(8), dp(4), 0);
        view.setLayoutParams(params);
        return view;
    }

    private TextView text(String value, float sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(col(color));
        return view;
    }

    private Button button(String label, boolean filled) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setBackgroundResource(filled ? R.drawable.bg_button_filled : R.drawable.bg_button_tonal);
        button.setTextColor(col(filled ? R.color.primary : R.color.on_surface));
        return button;
    }

    private void addGap(LinearLayout parent, int dp) {
        View gap = new View(this);
        gap.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dp)));
        parent.addView(gap);
    }

    private int dp(int value) { return Util.dp(this, value); }

    private int col(int res) { return getResources().getColor(res, null); }

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
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(10), dp(16), dp(10));
                ImageView icon = new ImageView(MainActivity.this);
                icon.setId(1);
                icon.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
                row.addView(icon);
                LinearLayout texts = column();
                texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView label = text("", 16, R.color.on_surface);
                label.setId(2);
                TextView pkg = text("", 12, R.color.on_surface_variant);
                pkg.setId(3);
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
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint meta = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final Rect dst = new Rect();

        DisplayTile(Context context, Core.Slot slot) {
            super(context);
            this.slot = slot;
            fill.setStyle(Paint.Style.FILL);
            stroke.setStyle(Paint.Style.STROKE);
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
            int pad = dp(12);
            int footer = dp(44);
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
            } else {
                fill.setColor(col(R.color.surface_container_lowest));
                box.set(pad, pad, w - pad, h - footer);
                canvas.drawRoundRect(box, dp(8), dp(8), fill);
            }
            label.setColor(col(R.color.on_surface));
            canvas.drawText(slot.label, pad, h - dp(24), label);
            meta.setColor(col(R.color.on_surface_variant));
            canvas.drawText("D " + slot.displayId + "  ·  " + slot.state, pad, h - dp(8), meta);
        }
    }
}
