package com.visal.typix;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

public class TypixKeyboardService extends InputMethodService {

    private LinearLayout keyboard;
    private LinearLayout suggestions;
    private boolean english = false;
    private boolean numbers = false;

    private StringBuilder phoneticBuffer = new StringBuilder();

    private final Map<String, String> words = new HashMap<>();

    @Override
    public View onCreateInputView() {

        createDictionary();

        keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setPadding(dp(5), dp(5), dp(5), dp(5));
        keyboard.setBackgroundColor(Color.rgb(10, 18, 27));

        createTopBar();
        createSuggestionBar();
        buildKeyboard();

        return keyboard;
    }

    private void createTopBar() {

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = new TextView(this);
        logo.setText("Typix");
        logo.setTextColor(Color.rgb(45, 180, 255));
        logo.setTextSize(21);
        logo.setGravity(Gravity.CENTER);

        top.addView(
                logo,
                new LinearLayout.LayoutParams(0, dp(42), 1)
        );

        Button emoji = specialButton("😊");

        emoji.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.commitText("😊", 1);
            }
        });

        Button language = specialButton("සි / EN");

        language.setOnClickListener(v -> {
            english = !english;
            numbers = false;
            phoneticBuffer.setLength(0);
            rebuild();
        });

        Button numbersButton = specialButton("123");

        numbersButton.setOnClickListener(v -> {
            numbers = !numbers;
            rebuild();
        });

        top.addView(emoji);
        top.addView(language);
        top.addView(numbersButton);

        keyboard.addView(top);
    }

    private void createSuggestionBar() {

        suggestions = new LinearLayout(this);
        suggestions.setOrientation(LinearLayout.HORIZONTAL);
        suggestions.setGravity(Gravity.CENTER_VERTICAL);

        suggestions.setBackgroundColor(Color.rgb(18, 30, 42));
        suggestions.setPadding(dp(4), dp(2), dp(4), dp(2));

        updateSuggestions();

        keyboard.addView(
                suggestions,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(42)
                )
        );
    }

    private void updateSuggestions() {

        if (suggestions == null) return;

        suggestions.removeAllViews();

        String current = phoneticBuffer.toString().toLowerCase();

        String[] result = getSuggestions(current);

        for (String word : result) {

            TextView suggestion = new TextView(this);

            suggestion.setText(word);
            suggestion.setTextColor(Color.WHITE);
            suggestion.setTextSize(16);
            suggestion.setGravity(Gravity.CENTER);

            suggestion.setPadding(
                    dp(10),
                    0,
                    dp(10),
                    0
            );

            suggestion.setOnClickListener(v -> {

                InputConnection ic = getCurrentInputConnection();

                if (ic != null) {

                    ic.deleteSurroundingText(
                            phoneticBuffer.length(),
                            0
                    );

                    ic.commitText(word, 1);

                    phoneticBuffer.setLength(0);

                    updateSuggestions();
                }
            });

            suggestions.addView(suggestion);
        }
    }

    private String[] getSuggestions(String text) {

        if (text.length() == 0) {
            return new String[]{
                    "මම",
                    "ඔයා",
                    "කොහොමද"
            };
        }

        if (words.containsKey(text)) {
            return new String[]{
                    words.get(text),
                    text
            };
        }

        if (text.startsWith("mam")) {
            return new String[]{"මම", "මගේ"};
        }

        if (text.startsWith("oya")) {
            return new String[]{"ඔයා", "ඔයාට"};
        }

        if (text.startsWith("koh")) {
            return new String[]{"කොහොමද"};
        }

        if (text.startsWith("hari")) {
            return new String[]{"හරි"};
        }

        return new String[]{text};
    }

    private void buildKeyboard() {

        if (numbers) {
            buildNumberKeyboard();
            return;
        }

        if (english) {
            buildEnglishKeyboard();
        } else {
            buildSinhalaKeyboard();
        }
    }

    private void buildSinhalaKeyboard() {

        String[][] rows = {

                {"අ", "ආ", "ඇ", "ඈ", "ඉ", "ඊ", "උ", "ඌ"},

                {"එ", "ඒ", "ඔ", "ඕ", "ක", "ග", "ච", "ජ"},

                {"ට", "ඩ", "ණ", "ත", "ද", "න", "ප", "බ"},

                {"ම", "ය", "ර", "ල", "ව", "ශ", "ස", "හ"},

                {"ළ", "ෆ", "ං", "ඃ", "්", "ා", "ි", "ී"}
        };

        for (String[] row : rows) {

            LinearLayout line = createRow();

            for (String key : row) {
                addKey(line, key);
            }

            keyboard.addView(line);
        }
    }

    private void buildEnglishKeyboard() {

        String[][] rows = {

                {"Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"},

                {"A", "S", "D", "F", "G", "H", "J", "K", "L"},

                {"Z", "X", "C", "V", "B", "N", "M"}
        };

        for (String[] row : rows) {

            LinearLayout line = createRow();

            for (String key : row) {
                addKey(line, key);
            }

            keyboard.addView(line);
        }
    }

    private void buildNumberKeyboard() {

        String[][] rows = {

                {"1", "2", "3", "4", "5"},

                {"6", "7", "8", "9", "0"},

                {"@", "#", "$", "%", "&"},

                {"!", "?", ".", ",", ":"}
        };

        for (String[] row : rows) {

            LinearLayout line = createRow();

            for (String key : row) {
                addKey(line, key);
            }

            keyboard.addView(line);
        }
    }

    private void addKey(LinearLayout row, String text) {

        Button button = normalButton(text);

        button.setOnClickListener(v -> {

            InputConnection ic = getCurrentInputConnection();

            if (ic == null) return;

            if (!english && !numbers) {

                ic.commitText(text, 1);

            } else {

                ic.commitText(text.toLowerCase(), 1);
            }
        });

        row.addView(button);
    }

    private LinearLayout createRow() {

        LinearLayout row = new LinearLayout(this);

        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        return row;
    }

    private Button normalButton(String text) {

        Button button = new Button(this);

        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(17);
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);

        GradientDrawable bg = new GradientDrawable();

        bg.setColor(Color.rgb(28, 43, 57));
        bg.setCornerRadius(dp(9));

        button.setBackground(bg);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        dp(50),
                        1
                );

        params.setMargins(
                dp(2),
                dp(2),
                dp(2),
                dp(2)
        );

        button.setLayoutParams(params);

        return button;
    }

    private Button specialButton(String text) {

        Button button = normalButton(text);

        button.setTextSize(14);

        return button;
    }

    private void rebuild() {

        keyboard.removeAllViews();

        createTopBar();
        createSuggestionBar();
        buildKeyboard();

        createBottomBar();
    }

    private void createBottomBar() {

        LinearLayout bottom = createRow();

        Button space = specialButton("SPACE");

        LinearLayout.LayoutParams spaceParams =
                new LinearLayout.LayoutParams(
                        0,
                        dp(52),
                        4
                );

        spaceParams.setMargins(
                dp(2),
                dp(2),
                dp(2),
                dp(2)
        );

        space.setLayoutParams(spaceParams);

        space.setOnClickListener(v -> {

            InputConnection ic = getCurrentInputConnection();

            if (ic != null) {

                if (!english && !numbers &&
                        phoneticBuffer.length() > 0) {

                    String input =
                            phoneticBuffer.toString()
                                    .toLowerCase();

                    String translated =
                            words.get(input);

                    if (translated != null) {

                        ic.deleteSurroundingText(
                                phoneticBuffer.length(),
                                0
                        );

                        ic.commitText(
                                translated,
                                1
                        );
                    }

                    phoneticBuffer.setLength(0);
                }

                ic.commitText(" ", 1);

                updateSuggestions();
            }
        });

        bottom.addView(space);

        Button backspace =
                specialButton("⌫");

        backspace.setOnClickListener(v -> {

            InputConnection ic =
                    getCurrentInputConnection();

            if (ic != null) {

                ic.deleteSurroundingText(1, 0);

                if (phoneticBuffer.length() > 0) {
                    phoneticBuffer.deleteCharAt(
                            phoneticBuffer.length() - 1
                    );
                }

                updateSuggestions();
            }
        });

        bottom.addView(backspace);

        Button enter =
                specialButton("↵");

        enter.setOnClickListener(v -> {

            InputConnection ic =
                    getCurrentInputConnection();

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
    }

    private void createDictionary() {

        words.put("mama", "මම");
        words.put("mage", "මගේ");
        words.put("mata", "මට");
        words.put("oyata", "ඔයාට");
        words.put("oya", "ඔයා");
        words.put("oyage", "ඔයාගේ");
        words.put("kohomada", "කොහොමද");
        words.put("kohomada", "කොහොමද");
        words.put("hari", "හරි");
        words.put("hodai", "හොඳයි");
        words.put("hondai", "හොඳයි");
        words.put("mokakda", "මොකක්ද");
        words.put("mokada", "මොකද");
        words.put("dan", "දැන්");
        words.put("oyaata", "ඔයාට");
        words.put("api", "අපි");
        words.put("apiwa", "අපිව");
        words.put("oyala", "ඔයාලා");
        words.put("ela", "එල");
        words.put("supiri", "සුපිරි");
        words.put("thanks", "ස්තුතියි");
        words.put("stutiyi", "ස්තුතියි");
        words.put("ayubowan", "ආයුබෝවන්");
    }

    private int dp(float value) {

        return (int)
                (value *
                        getResources()
                                .getDisplayMetrics()
                                .density
                        + 0.5f);
    }
}