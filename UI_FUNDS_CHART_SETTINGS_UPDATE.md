# ALPON UI update — v1.2.0

- First navigation destination is presented as **Chart**. It retains the existing live Binance chart and the full market-analysis stack below it, so analysis is available before switching to the ALPON command center.
- Chart height adapts to device screen width (clamped to 230–340 dp); fullscreen mode, pinch zoom, drag navigation, timeframe selection, EMA, Bollinger Bands, VWAP, structure and Smart Money overlays remain available.
- Third destination remains **ALPON**, the intelligence and trading command center.
- Fourth destination is presented as **Funds**. It shows the Paper Trading balance, open exposure, realized P/L, win/loss counts, active positions, and a closed-trade journal with entry/exit prices and timestamps.
- PaperTrade serialization now stores entry time. Existing six-field trade records remain readable; legacy records display that their entry time is unavailable.
- Fifth destination remains **Settings**, with clearer chart, intelligence-module and automation-safety information, plus existing language settings.
- No real Binance orders are submitted. Funds and trade journal in this update refer to the app's local Paper Trading simulation; manually tracked spot holdings remain a separate section.

Android Gradle compilation was attempted but could not run because the wrapper could not download Gradle 8.9 (`services.gradle.org` DNS/network unavailable). Build is therefore unverified.
