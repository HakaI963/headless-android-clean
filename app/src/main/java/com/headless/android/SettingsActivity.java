package com.headless.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Small settings screen: endpoint, token and grid columns. */
public class SettingsActivity extends Activity {
    private Core core;
    private TextView tokenView;

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
            root.addView(title("Settings"));

            root.addView(title("Agent endpoint"));
            EditText port = new EditText(this);
            port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            port.setText(String.valueOf(Prefs.port(this)));
            port.setTextColor(getResources().getColor(R.color.text, null));
            root.addView(port);

            Button save = button("Save", true);
            save.setOnClickListener(v -> {
                try { Prefs.port(this, Integer.parseInt(port.getText().toString())); }
                catch (Throwable ignored) {}
                Mcp.start(core, Prefs.port(this), Prefs.token(this));
                Toast.makeText(this, "Endpoint restarted", Toast.LENGTH_SHORT).show();
            });
            root.addView(save);

            root.addView(title("Access token"));
            tokenView = new TextView(this);
            tokenView.setText(Prefs.token(this));
            tokenView.setTextIsSelectable(true);
            tokenView.setTextColor(getResources().getColor(R.color.text, null));
            root.addView(tokenView);

            Button copy = button("Copy", false);
            copy.setOnClickListener(v -> {
                ClipboardManager manager = (ClipboardManager) getSystemService(
                        Context.CLIPBOARD_SERVICE);
                if (manager != null) manager.setPrimaryClip(
                        ClipData.newPlainText("HeadlessAndroid", Prefs.token(this)));
            });
            root.addView(copy);

            Button regen = button("New token", false);
            regen.setOnClickListener(v -> {
                Prefs.newToken(this);
                tokenView.setText(Prefs.token(this));
                Mcp.start(core, Prefs.port(this), Prefs.token(this));
            });
            root.addView(regen);

            root.addView(title("Columns"));
            LinearLayout columns = new LinearLayout(this);
            for (int value = 1; value <= 3; value++) {
                final int choice = value;
                Button item = button(String.valueOf(value), false);
                item.setOnClickListener(v -> {
                    Prefs.columns(this, choice);
                    Toast.makeText(this, "New displays use " + choice + " columns",
                            Toast.LENGTH_SHORT).show();
                });
                columns.addView(item);
            }
            root.addView(columns);
            root.addView(title("Version 1.0.0 · no clones · one app"));
            setContentView(root);
        } catch (Throwable e) {
            TextView error = new TextView(this);
            error.setText(Util.message(e));
            error.setTextColor(getResources().getColor(R.color.danger, null));
            setContentView(error);
        }
    }

    private TextView title(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(16);
        view.setTextColor(getResources().getColor(R.color.text2, null));
        view.setPadding(0, Util.dp(this, 16f), 0, Util.dp(this, 8f));
        return view;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setBackgroundResource(primary ? R.drawable.bg_primary : R.drawable.bg_button);
        button.setTextColor(getResources().getColor(
                primary ? R.color.on_accent : R.color.text, null));
        return button;
    }
}
