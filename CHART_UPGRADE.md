# Live chart upgrade

- Replaced periodic chart refresh with Binance combined Spot WebSocket streams: selected-symbol kline plus trade ticks.
- Trade ticks update the active candle's close/high/low immediately; Binance kline events reconcile the candle and refresh the signal only when the candle is closed.
- Added full-screen chart mode, pinch zoom, horizontal pan, timeframe selector, live connection status, and a visible current-price guide.
- Enabled EMA 9/21/50/200, Bollinger Bands, VWAP, and volume by default, with individual visibility toggles. RSI and MACD readings are surfaced beside the chart and in the existing technical-analysis panel.
- Kept the existing REST snapshot as historical chart seed and kept the app analysis-only (no order placement).

## Verification

- ZIP structure can be checked locally.
- A full Android Gradle build was not run in this environment: the Gradle wrapper distribution is not cached and network DNS access to `services.gradle.org` is unavailable.
- Live Binance WebSocket behavior must be verified on a network-connected Android device. Binance documents kline streams and trade streams as real-time market data; trade events are event-driven, so a quiet market may not produce a tick every wall-clock second.
