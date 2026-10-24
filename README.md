# Typix: Singlish -> Sinhala keyboard (Android)
Type `mama yanawaa`, get `මම යනවා`. Native Kotlin, zero dependencies, no INTERNET permission.

Sinhala typing accepts common phonetic spellings for aspirated letters, sagnaka letters, visarga and nga. The offline word list provides whole-word suggestions and optional auto-correction for common spellings such as `meke`, `hariyt`, `hadala`, `denn`, `helakuru` and `wagem`.

## Build
- Android Studio: open this folder, Run on a phone, then app -> "Enable" -> "Switch to Typix".
- GitHub: push, then Actions -> build -> download `typix-debug-apk`.

## Done
v0.5.0: smoother + smarter typing. Multi-touch fast typing, dirty-rect redraw, background dictionary/emoji loading, canvas emoji grid with fling scrolling. English word suggestions (10k common words) + 3-slot suggestion strip + recent-emoji row, Gboard-style emoji category icons with Recent, `q` = ද (Shift+q = ධ), comma key, long-press "." for punctuation, double-space full stop, space-bar cursor swipe, Caps Lock (double-tap Shift), 2nd symbols page, Enter key follows the editor action (search / send / go / done), auto-capitalize, bigger Singlish word list. App: Singlish cheat sheet, start-language, new typing/suggestion settings.
v0.4.2: toolbar now identical to the design (logo opens a tools menu: clipboard, text editor, settings); capital A (Shift + a) types ඇ, and Shift shows the capital-letter hints (ඇ ඛ ණ ළ ෂ ඨ ඪ භ ඝ ඡ ඣ ඞ) on the keys.
v0.4.1: emoji panel with Recent tab + clearly separated category tabs (each category shows only its own emojis).
v0.4: redesigned to match the Typix design: Ink default theme (#0E1224 / #1B2140 / lime #C8F135), bundled Outfit font, soft rounded keys with Sinhala hints, lime return key, `typix · සිංහල` space bar, tab toolbar (En / සිං with underline, emoji, mic, clipboard, editor, settings), pill suggestion strip, new app Home (status card, try-it field, setup, pill toggles), 2-column Themes grid with Apply button, bottom nav, light/dark toggle.
v0.3: equal-size toolbar buttons on one row, bordered keys (toggle in Settings), nav bar painted like the keyboard, app dark mode (Auto / Light / Dark).
Singlish engine (+tests), suggestions + auto-correct, flat canvas keyboard (shift, ?123, long-press digits, globe = switch language, key hints, 4 heights), always-visible icon toolbar, full emoji (9 categories + flags, auto-filtered to what your phone can draw) / clipboard / text editor panels, 7 themes with previews, adaptive app icon, app (Home / Themes / Settings).
## TODO
Multi-touch fast typing, bigger word list.

## Preview
Rendered preview of the UI (keyboard + app, light and dark): `docs/ui-preview.png` (also `docs/home.png`, `docs/home-dark.png`, `docs/keyboard.png`, `docs/themes.png`). It is a mock render of the design, not a device screenshot.


Design reference renders: `docs/design-*.png`. Fonts: Outfit (SIL OFL) in `app/src/main/assets/fonts/`.

English word list: google-10000-english (MIT, from the Google Trillion Word Corpus), `app/src/main/assets/en_words.txt`.

v0.7 (in progress): built-in voice typing. Tap the mic to dictate in Sinhala or English (follows the En / සිං tab) with live text, continuous listening and auto-capitalisation; tap again to stop. Long-press the mic to use the phone's own voice keyboard instead. Uses the phone's speech recogniser, so voice typing may need an internet connection.
