package com.headless.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** The run screen. Java only, every lifecycle call guarded. */
public class MainActivity extends Activity {
    private Core core;
    private GridLayout tiles;
    private LinearLayout empty;
    private LinearLayout actions;
    private TextView shellText;
    private TextView endpointText;
    private TextView countText;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable step = new Runnable() {
        public void run() {
            try { refresh(); } catch (Throwable ignored) {}
            ticker.postDelayed(this, 300);
        }
    };
    private int selected = -1;
    private String signature = "";

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
            ticker.postDelayed(step, 300);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onPause() {
        try { ticker.removeCallbacks(step); } catch (Throwable ignored) {}
        super.onPause();
    }

    private void buildUi() {
        int pad = Util.dp(this, 20f);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(c(R.color.bg));
        root.setPadding(pad, pad, pad, pad);

        root.addView(title("Headless Android", 24, R.color.text));
        root.addView(title("Private screens for your apps", 14, R.color.text2));
        addGap(root, 20);

        LinearLayout session = card();
        session.addView(title("Session", 12, R.color.muted));
        shellText = value("Shizuku: checking");
        endpointText = value("Agent: checking");
        session.addView(shellText);
        session.addView(endpointText);
        LinearLayout sessionActions = new LinearLayout(this);
        sessionActions.setOrientation(LinearLayout.HORIZONTAL);
        Button grant = smallButton("Grant Shizuku", false);
        grant.setOnClickListener(v -> Shizuku.get(this).requestPermission());
        Button openShizuku = smallButton("Open Shizuku", false);
        openShizuku.setOnClickListener(v -> openShizukuManager());
        sessionActions.addView(grant);
        sessionActions.addView(openShizuku);
        session.addView(sessionActions);
        root.addView(session);
        addGap(root, 20);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView section = title("Virtual displays", 16, R.color.text);
        section.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        countText = title("0", 12, R.color.muted);
        header.addView(section);
        header.addView(countText);
        root.addView(header);
        addGap(root, 12);

        LinearLayout primary = new LinearLayout(this);
        primary.setOrientation(LinearLayout.HORIZONTAL);
        Button create = smallButton("Create display", false);
        create.setOnClickListener(v -> {
            if (core.createDisplay() == null) toast("All 9 displays are in use");
        });
        Button addApp = smallButton("Add app", true);
        addApp.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        primary.addView(create);
        primary.addView(addApp);
        root.addView(primary);
        addGap(root, 12);

        Button settings = smallButton("Settings", false);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        root.addView(settings);
        addGap(root, 16);

        ScrollView scroll = new ScrollView(this);
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.addView(title("No displays yet", 16, R.color.text));
        empty.addView(title("Create a display to see the virtual screen, or add an installed app to run on it.",
                14, R.color.text2));
        tiles = new GridLayout(this);
        tiles.setColumnCount(2);
        holder.addView(empty);
        holder.addView(tiles);
        scroll.addView(holder);
        root.addView(scroll);

        HorizontalScrollView actionsScroll = new HorizontalScrollView(this);
        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actionsScroll.addView(actions);
        root.addView(actionsScroll);
        setContentView(root);
    }

    private void refresh() {
        if (core == null || tiles == null) return;
        String state = Shizuku.get(this).state();
        shellText.setText("Shizuku: " + state);
        shellText.setTextColor(c(Shizuku.READY.equals(state) ? R.color.good : R.color.danger));
        shellText.setOnClickListener(v -> Shizuku.get(this).requestPermission());
        String endpoint = Mcp.endpoint();
        endpointText.setText("Agent: " + (endpoint == null ? "offline" : endpoint + "/mcp"));
        endpointText.setOnClickListener(v -> showToken());

        List<Core.Slot> slots = core.slots();
        countText.setText(slots.size() + " / 9");
        empty.setVisibility(slots.isEmpty() ? View.VISIBLE : View.GONE);
        String next = "";
        for (Core.Slot slot : slots) next += slot.slot + ":" + slot.pkg + "|";
        if (!next.equals(signature)) {
            signature = next;
            tiles.removeAllViews();
            for (final Core.Slot slot : slots) {
                TileView view = new TileView(this, slot);
                view.setOnClickListener(v -> {
                    selected = slot.slot;
                    signature = "";
                    refresh();
                });
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = 0;
                params.height = Util.dp(this, 220f);
                params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                params.setMargins(0, 0, Util.dp(this, 12f), Util.dp(this, 12f));
                view.setLayoutParams(params);
                tiles.addView(view);
            }
        }
        actions.removeAllViews();
        Core.Slot current = core.slot(selected);
        if (current != null) {
            addAction("Back", v -> sendKey(current, android.view.KeyEvent.KEYCODE_BACK));
            addAction("Home", v -> sendKey(current, android.view.KeyEvent.KEYCODE_HOME));
            addAction("Close", v -> {
                core.close(current.slot);
                selected = -1;
                signature = "";
                refresh();
            });
        }
    }

    private void sendKey(Core.Slot slot, int code) {
        try {
            core.input("key", new org.json.JSONObject().put("slot", slot.slot).put("code", code));
        } catch (Throwable e) {
            toast(Util.message(e));
        }
    }

    private void showToken() {
        String endpoint = Mcp.endpoint();
        String message = "Endpoint: " + (endpoint == null ? "offline" : endpoint + "/mcp")
                + "\nToken: " + Prefs.token(this);
        new AlertDialog.Builder(this).setTitle("Agent endpoint")
                .setMessage(message)
                .setPositiveButton("Copy", (d, w) -> {
                    ClipboardManager manager = (ClipboardManager) getSystemService(
                            Context.CLIPBOARD_SERVICE);
                    if (manager != null) manager.setPrimaryClip(
                            ClipData.newPlainText("HeadlessAndroid", message));
                })
                .setNegativeButton("Close", null).show();
    }

    private void openShizukuManager() {
        Intent intent = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
        if (intent == null) toast("Shizuku is not installed");
        else startActivity(intent);
    }

    private void showError(Throwable e) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Util.dp(this, 20f), Util.dp(this, 20f), Util.dp(this, 20f), Util.dp(this, 20f));
        root.addView(title("Could not start", 20, R.color.text));
        root.addView(title(Util.message(e), 14, R.color.danger));
        setContentView(root);
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundResource(R.drawable.bg_card);
        layout.setPadding(Util.dp(this, 16f), Util.dp(this, 16f),
                Util.dp(this, 16f), Util.dp(this, 16f));
        return layout;
    }

    private TextView title(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(c(color));
        return view;
    }

    private TextView value(String text) {
        TextView view = title(text, 14, R.color.text2);
        view.setPadding(0, Util.dp(this, 6f), 0, 0);
        return view;
    }

    private Button smallButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setBackgroundResource(primary ? R.drawable.bg_primary : R.drawable.bg_button);
        button.setTextColor(c(primary ? R.color.on_accent : R.color.text));
        return button;
    }

    private void addAction(String label, View.OnClickListener listener) {
        Button button = smallButton(label, false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Util.dp(this, 100f), ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, Util.dp(this, 8f), 0);
        button.setLayoutParams(params);
        actions.addView(button);
    }

    private void addGap(LinearLayout parent, int dp) {
        View gap = new View(this);
        gap.setLayoutParams(new LinearLayout.LayoutParams(1, Util.dp(this, dp)));
        parent.addView(gap);
    }

    private int c(int res) { return getResources().getColor(res, null); }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

    /** One virtual display tile: real frame, display id and state. */
    private static final class TileView extends View {
        private final Core.Slot slot;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint meta = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final float density;

        TileView(Context context, Core.Slot slot) {
            super(context);
            this.slot = slot;
            density = context.getResources().getDisplayMetrics().density;
            fill.setStyle(Paint.Style.FILL);
            stroke.setStyle(Paint.Style.STROKE);
            label.setTextSize(14f * density * 2);
            meta.setTextSize(11f * density * 2);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            box.set(0, 0, w, h);
            fill.setColor(c(R.color.surface));
            canvas.drawRoundRect(box, 14f * density, 14f * density, fill);
            stroke.setColor(c(R.color.hairline));
            stroke.setStrokeWidth(density);
            canvas.drawRoundRect(box, 14f * density, 14f * density, stroke);

            android.graphics.Bitmap frame = slot.session == null ? null : slot.session.capture();
            float left = 12f * density;
            float top = 12f * density;
            float right = w - 12f * density;
            float bottom = h - 56f * density;
            if (frame != null && !frame.isRecycled()) {
                float scale = Math.min((right - left) / frame.getWidth(),
                        (bottom - top) / frame.getHeight());
                float dw = frame.getWidth() * scale;
                float dh = frame.getHeight() * scale;
                canvas.drawBitmap(frame, null, new android.graphics.Rect(
                        (int) ((w - dw) / 2), (int) top,
                        (int) ((w + dw) / 2), (int) (top + dh)), null);
            } else {
                fill.setColor(c(R.color.bg));
                box.set(left, top, right, bottom);
                canvas.drawRoundRect(box, 8f * density, 8f * density, fill);
            }
            label.setColor(c(R.color.text));
            canvas.drawText(slot.label, 12f * density, h - 30f * density, label);
            meta.setColor(c(R.color.muted));
            canvas.drawText("D " + slot.displayId + "  ·  " + slot.state,
                    12f * density, h - 12f * density, meta);
        }

        private int c(int res) {
            return getResources().getColor(res, null);
        }
    }
}
