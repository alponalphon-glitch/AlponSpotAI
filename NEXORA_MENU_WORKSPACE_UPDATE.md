# NEXORA Menu & Workspace Update — 2026-11-23

## Review of the uploaded source ZIP

- The archive passed `unzip -t` before editing.
- The uploaded project contained five bottom-navigation destinations: Home, Markets, NEXORA, Portfolio and Settings.
- ScannerScreen and NewsScreen existed in source but were not reachable from the main navigation. The background scanner effect also watched a top-level `Scanner` destination that was not present in the actual five-item navigation.
- Backtesting and Paper Trading were available only through the overflow menu.
- The UI used pure black surfaces for both the page and cards, and several accent/muted color values were all white, reducing visual hierarchy.

## Changes made

1. Preserved the original `MainActivity.kt` in `backups/MainActivity.kt.before-menu-workspace-20261123`.
2. Updated the color palette to a dark charcoal surface system with blue/cyan highlights, green/red status colors, and readable muted labels.
3. Added a subtle outline to existing cards so content separates from the black page background.
4. Added a NEXORA workspace strip with five reachable submenus: AI Brain, Scanner, News, Backtest and Paper.
5. Connected Scanner auto-refresh to the actual NEXORA/Scanner submenu at 60-second intervals.
6. Connected News loading and refresh to the NEXORA/News submenu at five-minute intervals.
7. Added a Market Explorer heading and a Trading & Data Safety status card in Settings.
8. Kept the original five main navigation destinations, Spot/Futures/Forex sections, chart and AI screens, account, support, backtesting and paper-trading screens.

## Important implementation limits found in the original source

- The Spot market list displayed by the markets screen is capped at 500 instruments.
- The scanner's current implementation selects the 25 highest-volume USDT tickers before evaluating their candles; it does not scan every USDT pair.
- Forex requires a user-provided Twelve Data API key.
- Futures screens use public market/derivatives data and do not place exchange orders.
- The five hidden Guardian/Council checks are deterministic evidence checks, not five independent LLMs. Their own documentation explains the limitations.

## Validation status

- Original ZIP integrity: passed.
- Kotlin source parser check: no syntax-pattern diagnostics were found. A standalone `kotlinc` attempt could not type-check this Android source because Android, AndroidX Compose, Retrofit and project dependencies were not on the compiler classpath.
- The project launcher is a custom Gradle 8.9 downloader (not a standard Gradle Wrapper distribution). The first build attempt could not download Gradle because `services.gradle.org` DNS resolution failed; Android SDK/dependency availability also could not be verified. The `gradlew` script was marked executable for Linux/Termux use.
- Full Android Gradle build/APK and emulator screenshot: not verified in this environment.
- The accompanying PNG previews are static design mockups based on the source screen structure. Their sample market/balance values are placeholders; they are not captured live app screens.
