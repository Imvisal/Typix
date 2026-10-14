# Typix: Singlish -> Sinhala keyboard (Android)
Type `mama yanawaa`, get `මම යනවා`. Native Kotlin, zero dependencies, no INTERNET permission.

## Build
- Android Studio: open this folder, Run on a phone, then app -> "Enable" -> "Switch to Typix".
- GitHub: push, then Actions -> build -> download `typix-debug-apk`.

## Done
v0.3: equal-size toolbar buttons on one row, bordered keys (toggle in Settings), nav bar painted like the keyboard, app dark mode (Auto / Light / Dark).
Singlish engine (+tests), suggestions + auto-correct, flat canvas keyboard (shift, ?123, long-press digits, globe = switch language, key hints, 4 heights), always-visible icon toolbar, full emoji (9 categories + flags, auto-filtered to what your phone can draw) / clipboard / text editor panels, 7 themes with previews, adaptive app icon, app (Home / Themes / Settings).
## TODO
Voice typing, multi-touch fast typing, bigger word list.

## Preview
Rendered preview of the UI (keyboard + app, light and dark): `docs/ui-preview.png` (also `docs/home.png`, `docs/home-dark.png`, `docs/keyboard.png`, `docs/themes.png`). It is a mock render of the design, not a device screenshot.
