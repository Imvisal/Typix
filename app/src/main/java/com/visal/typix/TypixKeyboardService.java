package com.visal.typix;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

public class TypixKeyboardService extends InputMethodService {

    private LinearLayout keyboard;
    private boolean sinhalaMode = true;

    private final Map<String, String> phonetic = new HashMap<>();

    @Override
    public View onCreateInputView() {
        createPhoneticMap();

        keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setPadding(6, 6, 6, 6);
        keyboard.setBackgroundColor(Color.rgb(12, 20, 30));

        createToolbar();
        createSinhalaKeys();
        createBottomRow();

        return keyboard;
    }

    private void createToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = new TextView(this);
        logo.setText("Typix");
        logo.setTextColor(Color.rgb(60, 180, 255));
        logo.setTextSize(20);
        logo.setGravity(Gravity.CENTER);

        bar.addView(logo, new LinearLayout.LayoutParams(
                0, 55, 1
        ));

        Button emoji = smallButton("☺");
        Button language = smallButton("සි | En");
        Button settings = smallButton("⚙");

        emoji.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.commitText("😊", 1);
        });

        language.setOnClickListener(v -> {
            sinhalaMode = !sinhalaMode;
            rebuildKeyboard();
        });

        settings.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.commitText(" ", 1);
        });

        bar.addView(emoji);
        bar.addView(language);
        bar.addView(settings);

        keyboard.addView(bar);
    }

    private void createSinhalaKeys() {

        String[][] rows = {
                {"අ", "ආ", "ඇ", "ඈ", "ඉ", "ඊ", "උ", "ඌ", "එ", "ඒ"},
                {"ඔ", "ඕ", "ක", "ඛ", "ග", "ඝ", "ච", "ජ", "ට", "ඩ"},
                {"ණ", "ත", "ද", "න", "ප", "බ", "ම", "ය", "ර", "ල"},
                {"ව", "ශ", "ෂ", "ස", "හ", "ළ", "ෆ", "ං", "ඃ", "්"}
        };

        for (String[] row : rows) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);

            for (String key : row) {
                addKey(line, key);
            }

            keyboard.addView(line);
        }
    }

    private void createEnglishKeys() {

        String[][] rows = {
                {"Q","W","E","R","T","Y","U","I","O","P"},
                {"A","S","D","F","G","H","J","K","L"},
                {"Z","X","C","V","B","N","M"}
        };

        for (String[] row : rows) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);

            for (String key : row) {
                addKey(line, key);
            }

            keyboard.addView(line);
        }
    }

    private void createBottomRow() {

        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER);

        Button number = smallButton("?123");
        Button space = smallButton("SPACE");
        Button backspace = smallButton("⌫");
        Button enter = smallButton("↵");

        number.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.commitText("123", 1);
        });

        space.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.commitText(" ", 1);
        });

        backspace.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.deleteSurroundingText(1, 0);
        });

        enter.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.sendKeyEvent(
                        new android.view.KeyEvent(
                                android.view.KeyEvent.ACTION_DOWN,
                                android.view.KeyEvent.KEYCODE_ENTER
                        )
                );
            }
        });

        line.addView(number);
        line.addView(space, new LinearLayout.LayoutParams(0, 60, 2));
        line.addView(backspace);
        line.addView(enter);

        keyboard.addView(line);
    }

    private void addKey(LinearLayout row, String text) {

        Button button = smallButton(text);

        button.setOnClickListener(v -> {

            InputConnection ic = getCurrentInputConnection();

            if (ic == null) return;

            if (sinhalaMode) {
                ic.commitText(text, 1);
            } else {
                ic.commitText(text.toLowerCase(), 1);
            }
        });

        row.addView(button, new LinearLayout.LayoutParams(
                0,
                58,
                1
        ));
    }

    private Button smallButton(String text) {

        Button button = new Button(this);

        button.setText(text);
        button.setTextSize(15);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(28, 42, 55));
        bg.setCornerRadius(14);

        button.setBackground(bg);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        65,
                        58
                );

        params.setMargins(3, 3, 3, 3);

        button.setLayoutParams(params);

        return button;
    }

    private void rebuildKeyboard() {

        keyboard.removeAllViews();

        createToolbar();

        if (sinhalaMode) {
            createSinhalaKeys();
        } else {
            createEnglishKeys();
        }

        createBottomRow();
    }

    private void createPhoneticMap() {

        phonetic.put("a", "අ");
        phonetic.put("aa", "ආ");
        phonetic.put("i", "ඉ");
        phonetic.put("ii", "ඊ");
        phonetic.put("u", "උ");
        phonetic.put("uu", "ඌ");

        phonetic.put("ka", "ක");
        phonetic.put("ga", "ග");
        phonetic.put("cha", "ච");
        phonetic.put("ja", "ජ");

        phonetic.put("ta", "ට");
        phonetic.put("da", "ඩ");
        phonetic.put("na", "න");

        phonetic.put("pa", "ප");
        phonetic.put("ba", "බ");
        phonetic.put("ma", "ම");

        phonetic.put("ya", "ය");
        phonetic.put("ra", "ර");
        phonetic.put("la", "ල");

        phonetic.put("wa", "ව");
        phonetic.put("sa", "ස");
        phonetic.put("ha", "හ");
    }
}