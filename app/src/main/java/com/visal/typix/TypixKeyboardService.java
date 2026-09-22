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

public class TypixKeyboardService extends InputMethodService {

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable keyBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(28, 43, 57));
        bg.setCornerRadius(dp(8));
        return bg;
    }

    private Button createKey(String text) {
        Button button = new Button(this);

        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(18);
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setBackground(keyBackground());

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        dp(52),
                        1
                );

        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        button.setLayoutParams(params);

        button.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();

            if (ic != null) {
                ic.commitText(text, 1);
            }
        });

        return button;
    }

    private Button createSpecialKey(String text, View.OnClickListener listener) {
        Button button = new Button(this);

        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(18);
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setBackground(keyBackground());
        button.setOnClickListener(listener);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        dp(52),
                        1
                );

        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        button.setLayoutParams(params);

        return button;
    }

    private LinearLayout createRow() {
        LinearLayout row = new LinearLayout(this);

        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(2), 0, dp(2), 0);

        return row;
    }

    private void addKey(LinearLayout row, String text) {
        row.addView(createKey(text));
    }

    @Override
    public View onCreateInputView() {

        LinearLayout keyboard = new LinearLayout(this);

        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setPadding(dp(4), dp(6), dp(4), dp(6));
        keyboard.setBackgroundColor(Color.rgb(10, 20, 29));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), 0, dp(10), dp(4));

        TextView logo = new TextView(this);
        logo.setText("Typix");
        logo.setTextColor(Color.rgb(30, 170, 240));
        logo.setTextSize(22);
        logo.setGravity(Gravity.CENTER);

        header.addView(
                logo,
                new LinearLayout.LayoutParams(
                        0,
                        dp(42),
                        1
                )
        );

        keyboard.addView(header);

        // Row 1
        LinearLayout row1 = createRow();

        addKey(row1, "අ");
        addKey(row1, "ආ");
        addKey(row1, "ඉ");
        addKey(row1, "ඊ");
        addKey(row1, "උ");
        addKey(row1, "ඌ");
        addKey(row1, "එ");
        addKey(row1, "ඒ");

        keyboard.addView(row1);

        // Row 2
        LinearLayout row2 = createRow();

        addKey(row2, "ඔ");
        addKey(row2, "ඕ");
        addKey(row2, "ක");
        addKey(row2, "ග");
        addKey(row2, "ච");
        addKey(row2, "ජ");
        addKey(row2, "ට");
        addKey(row2, "ඩ");

        keyboard.addView(row2);

        // Row 3
        LinearLayout row3 = createRow();

        addKey(row3, "ත");
        addKey(row3, "ද");
        addKey(row3, "න");
        addKey(row3, "ප");
        addKey(row3, "බ");
        addKey(row3, "ම");
        addKey(row3, "ය");
        addKey(row3, "ර");

        keyboard.addView(row3);

        // Row 4
        LinearLayout row4 = createRow();

        addKey(row4, "ල");
        addKey(row4, "ව");
        addKey(row4, "ස");
        addKey(row4, "හ");
        addKey(row4, "ළ");
        addKey(row4, "ණ");

        Button backspace = createSpecialKey("⌫", v -> {
            InputConnection ic = getCurrentInputConnection();

            if (ic != null) {
                ic.deleteSurroundingText(1, 0);
            }
        });

        row4.addView(backspace);

        keyboard.addView(row4);

        // Bottom row
        LinearLayout bottom = createRow();

        Button number = createSpecialKey("123", v -> {
            InputConnection ic = getCurrentInputConnection();

            if (ic != null) {
                ic.commitText("123", 1);
            }
        });

        bottom.addView(number);

        Button space = createSpecialKey("සිංහල", v -> {
            InputConnection ic = getCurrentInputConnection();

            if (ic != null) {
                ic.commitText(" ", 1);
            }
        });

        LinearLayout.LayoutParams spaceParams =
                new LinearLayout.LayoutParams(
                        0,
                        dp(52),
                        4
                );

        spaceParams.setMargins(dp(2), dp(2), dp(2), dp(2));
        space.setLayoutParams(spaceParams);

        bottom.addView(space);

        Button enter = createSpecialKey("↵", v -> {
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

        bottom.addView(enter);

        keyboard.addView(bottom);

        return keyboard;
    }
}