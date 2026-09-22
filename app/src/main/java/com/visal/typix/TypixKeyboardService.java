package com.visal.typix;

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;

public class TypixKeyboardService extends InputMethodService {

    private InputConnection inputConnection;

    @Override
    public View onCreateInputView() {
        LinearLayout keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setPadding(6, 6, 6, 6);

        String[][] rows = {
                {"Q","W","E","R","T","Y","U","I","O","P"},
                {"A","S","D","F","G","H","J","K","L"},
                {"Z","X","C","V","B","N","M","⌫"},
                {"🌐","SPACE","↵"}
        };

        for (String[] row : rows) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);

            for (String key : row) {
                Button button = new Button(this);
                button.setText(key);

                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(0, 70, 1);

                button.setLayoutParams(params);

                button.setOnClickListener(v -> {
                    String text = button.getText().toString();
                    inputConnection = getCurrentInputConnection();

                    if (inputConnection == null) return;

                    if (text.equals("⌫")) {
                        inputConnection.deleteSurroundingText(1, 0);
                    } else if (text.equals("SPACE")) {
                        inputConnection.commitText(" ", 1);
                    } else if (text.equals("↵")) {
                        inputConnection.sendKeyEvent(
                                new android.view.KeyEvent(
                                        android.view.KeyEvent.ACTION_DOWN,
                                        android.view.KeyEvent.KEYCODE_ENTER
                                )
                        );
                    } else if (!text.equals("🌐")) {
                        inputConnection.commitText(text, 1);
                    }
                });

                rowLayout.addView(button);
            }

            keyboard.addView(rowLayout);
        }

        return keyboard;
    }
}
