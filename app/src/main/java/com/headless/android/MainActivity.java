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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** The run screen. Java only; every lifecycle method is guarded. */
public class MainActivity extends Activity {
    private Core core;
    private LinearLayout slotsBox;
    private LinearLayout emptyBox;
    private LinearLayout actionsBox;
    private TextView shellText;
    private TextView endpointText;
    private TextView countText;
    private TextView errorText;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = () -> {
        try { refresh(); } catch (Throwable ignored) {}
        ticker.postDelayed(this::safeTick, 250);
    };
    private int selected = -1;
    private String slotSignature = "";
    private boolean uiReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            core = Core.init(this);
            buildUi();
            uiReady = true;
        } catch (Throwable e) {
            showError(e);
        }
    }

    private void safeTick() {
        try { tick.run(); } catch (Throwable ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            Shizuku.get(this).refresh();
            if (core == null) core = Core.init(this);
            if (uiReady) refresh();
        } catch (Throwable ignored) {
        }
        if (uiReady) ticker.postDelayed(this::safeTick, 250);
    }

    @Override
    protected void onPause() {
        try { ticker.removeCallbacks(this::safeTick); } catch (Throwable ignored) {}
        super.onPause();
    }

    private void buildUi() {
        int pad = Util.dp(this, 20f);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(R.color.bg));
        root.setPadding(pad, pad, pad, pad);

        TextView title = text("Headless Android", 24, R.color.text);
        root.addView(title);
        root.addView(text("Run apps on private displays", 14, R.color.text2));
        addSpace(root, 16);

        Button add = button("Add app", true);
        add.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        root.addView(add);
        addSpace(root, 16);

        LinearLayout session = new LinearLayout(this);
        session.setOrientation(LinearLayout.VERTICAL);
        session.setBackgroundResource(R.drawable.bg_card);
        session.setPadding(pad, pad, pad, pad);
        session.addView(text("Session", 12, R.color.muted));
        shellText = text("Shizuku: checking", 14, R.color.text2);
        endpointText = text("Agent: checking", 14, R.color.text2);
        shellText.setVisibility(View.VISIBLE);
        endpointText.setVisibility(View.VISIBLE);
        session.addView(shellText);
        session.addView(endpointText);
        root.addView(session);
        addSpace(root, 20);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView section = text("Displays", 14, R.color.text2);
        section.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        countText = text("0 / 9", 12, R.color.muted);
        header.addView(section);
        header.addView(countText);
        Button settings = button("Settings", false);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        header.addView(settings);
        root.addView(header);
        addSpace(root, 12);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        slotsBox = new LinearLayout(this);
        slotsBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox = new LinearLayout(this);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.addView(text("No displays running", 16, R.color.text));
        emptyBox.addView(text("Launch an installed app to give it a private screen. "
                + "The original app keeps its data and signature.", 14, R.color.text2));
        content.addView(emptyBox);
        content.addView(slotsBox);
        scroll.addView(content);
        root.addView(scroll);

        actionsBox = new LinearLayout(this);
        actionsBox.setOrientation(LinearLayout.HORIZONTAL);
        actionsBox.setVisibility(View.GONE);
        root.addView(actionsBox);
        errorText = text("", 14, R.color.danger);
        root.addView(errorText);
        setContentView(root);
    }

    private void showError(Throwable e) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(R.color.bg));
        int pad = Util.dp(this, 20f);
        root.setPadding(pad, pad, pad, pad);
        root.addView(text("Headless Android could not start", 20, R.color.text));
        root.addView(text(Util.message(e), 14, R.color.danger));
        root.addView(text("No app data was changed.", 14, R.color.text2));
        setContentView(root);
    }

    private void refresh() {
        if (!uiReady || core == null) return;
        String shell = Shizuku.get(this).state();
        shellText.setText("Shizuku: " + shell);
        shellText.setTextColor(color(Shizuku.READY.equals(shell) ? R.color.good : R.color.danger));
        shellText.setOnClickListener(v -> {
            if (!Shizuku.get(this).ready()) Shizuku.get(this).requestPermission();
        });
        String endpoint = Mcp.endpoint();
        endpointText.setText("Agent: " + (endpoint == null ? "offline" : endpoint + "/mcp"));
        endpointText.setOnClickListener(v -> showToken());
        List<Core.Slot> slots = core.slots();
        countText.setText(slots.size() + " / " + Core.MAX_SLOTS);
        emptyBox.setVisibility(slots.isEmpty() ? View.VISIBLE : View.GONE);
        String signature = "";
        for (Core.Slot slot : slots) signature += slot.slot + ":" + slot.pkg + "|";
        if (!signature.equals(slotSignature)) {
            slotSignature = signature;
            slotsBox.removeAllViews();
            for (Core.Slot slot : slots) {
                final Core.Slot target = slot;
                slotsBox.addView(new SlotView(this, core, slot, () -> {
                    selected = target.slot;
                    slotSignature = "";
                    refresh();
                }));
            }
        }
        for (int i = 0; i < slotsBox.getChildCount(); i++) {
            View child = slotsBox.getChildAt(i);
            if (child instanceof SlotView) ((SlotView) child).sync(selected);
        }
        if (!slots.isEmpty() && selected < 0) selected = slots.get(0).slot;
        buildActions(slots);
        String error = "";
        for (Core.Slot slot : slots) if (!slot.state.equals("live")) error = slot.error;
        errorText.setText(error);
    }

    private void buildActions(List<Core.Slot> slots) {
        actionsBox.removeAllViews();
        Core.Slot slot = core.slot(selected);
        if (slot == null) {
            actionsBox.setVisibility(View.GONE);
            return;
        }
        actionsBox.setVisibility(View.VISIBLE);
        addAction("Back", v -> sendKey(slot, android.view.KeyEvent.KEYCODE_BACK));
        addAction("Home", v -> sendKey(slot, android.view.KeyEvent.KEYCODE_HOME));
        addAction("Recents", v -> sendKey(slot, android.view.KeyEvent.KEYCODE_APP_SWITCH));
        addAction("Shot", v -> toast("Screenshot available through MCP"));
        addAction("Close", v -> {
            core.close(slot.slot);
            selected = -1;
            slotSignature = "";
            refresh();
        });
    }

    private void addAction(String label, View.OnClickListener listener) {
        Button button = button(label, false);
        button.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        button.setOnClickListener(listener);
        actionsBox.addView(button);
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
                .setNeutralButton("New token", (d, w) -> {
                    Prefs.newToken(this);
                    Mcp.start(core, Prefs.port(this), Prefs.token(this));
                })
                .setNegativeButton("Close", null).show();
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color(color));
        return view;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setBackgroundResource(primary ? R.drawable.bg_primary : R.drawable.bg_button);
        button.setTextColor(color(primary ? R.color.on_accent : R.color.text));
        return button;
    }

    private void addSpace(LinearLayout parent, int dp) {
        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(1, Util.dp(this, dp)));
        parent.addView(space);
    }

    private int color(int res) { return getResources().getColor(res, null); }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

    /** One display tile, drawn directly. No XML, no binding library, no lifecycle state. */
    private static final class SlotView extends View {
        private final Core core;
        private final Core.Slot slot;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint meta = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final Rect dst = new Rect();
        private final float density;
        private boolean selected;

        SlotView(android.content.Context context, Core core, Core.Slot slot,
                 Runnable onSelect) {
            super(context);
            this.core = core;
            this.slot = slot;
            this.density = context.getResources().getDisplayMetrics().density;
            fill.setStyle(Paint.Style.FILL);
            stroke.setStyle(Paint.Style.STROKE);
            text.setTextSize(15f * density * 2);
            meta.setTextSize(12f * density * 2);
            setOnClickListener(v -> onSelect.run());
        }

        void sync(int selectedSlot) {
            selected = selectedSlot == slot.slot;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            box.set(0, 0, w, h);
            fill.setColor(getResources().getColor(R.color.surface, null));
            canvas.drawRoundRect(box, 14f * density, 14f * density, fill);
            stroke.setColor(getResources().getColor(
                    selected ? R.color.accent : R.color.hairline, null));
            stroke.setStrokeWidth((selected ? 2f : 1f) * density);
            canvas.drawRoundRect(box, 14f * density, 14f * density, stroke);

            Bitmap bitmap = slot.session == null ? null : slot.session.capture();
            float top = 28f * density;
            float bottom = h - 48f * density;
            if (bitmap != null && !bitmap.isRecycled()) {
                float scale = Math.min((w - 24f * density) / bitmap.getWidth(),
                        (bottom - top) / bitmap.getHeight());
                float dw = bitmap.getWidth() * scale;
                float dh = bitmap.getHeight() * scale;
                float left = (w - dw) / 2f;
                dst.set((int) left, (int) top, (int) (left + dw), (int) (top + dh));
                canvas.drawBitmap(bitmap, null, dst, null);
            }
            text.setColor(getResources().getColor(R.color.text, null));
            canvas.drawText(slot.label, 12f * density, h - 24f * density, text);
            meta.setColor(getResources().getColor(R.color.muted, null));
            String id = "D " + slot.displayId + "  ·  " + slot.state;
            canvas.drawText(id, 12f * density, h - 8f * density, meta);
        }
    }
}
