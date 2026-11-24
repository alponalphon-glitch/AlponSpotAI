# ALPON Forex Market Integration

## Market separation

- `BINANCE_SPOT` remains the default crypto market and continues to use Binance REST/WebSocket.
- `FOREX` uses a separate Twelve Data REST source. Forex symbols are stored in Room with the `FX_` prefix so they cannot collide with Binance symbols.
- Binance Spot pair discovery now includes both `USDT` and `USDC` quote assets, up to 500 markets per quote asset (up to 1,000 combined).
- Forex pair discovery calls the provider's `/forex_pairs` reference endpoint and displays up to 500 pairs actually returned by the provider. The app does not invent unsupported pairs.

## Forex data

- A user-supplied Twelve Data API key is required. It is stored in local app preferences and is not embedded in the APK or source.
- Daily history requests 1,000 OHLC bars (provider maximum is 5,000), exceeding the one-year chart requirement when the provider returns the requested history.
- The FX decision context uses seven real provider intervals: `1month` (up to 240 bars), `1week` (up to 500 bars), `1day` (up to 1,000 bars), `4h`, `2h`, `1h`, and `15min`.
- The daily frame must contain at least 600 closed candles before the Forex multi-timeframe engine can form a consensus. Missing/stale data returns WAIT.
- Supported chart intervals in the UI: 15m, 1h, 2h, 4h, 1d, 1w, and 1M.
- Only the selected Forex pair is downloaded for history; the app does not bulk-download history for all catalogue pairs, to avoid exhausting API credits. Forex REST refresh runs every 30 minutes while the chart is active.
- Forex OHLC aggregate data does not provide centralized traded volume. Volume and taker-flow values are set to zero rather than fabricated. The UI discloses this limitation.
- REST data is refreshed less frequently than Binance WebSocket data; Forex is not labelled as a live tick stream.

## Execution safety

- Forex is analysis-only in this build. Binance Spot Paper Trading is disabled while Forex is selected.
- No Forex broker connection, bid/ask spread, financing/swap, or broker execution-cost feed is connected. Therefore any apparent Forex BUY/SELL direction is held at WAIT by the final decision gate until those costs can be verified.
- Binance Spot SELL rules remain unchanged: SELL is only an exit/reduction of an existing holding; no short position is created.
- The Forex analysis uses a separate 7-timeframe consensus and allows for ordinary weekend market closures while still rejecting stale weekday data and abnormal gaps.

## Provider references

- Twelve Data documents `/forex_pairs` as the source of supported physical currency pairs.
- Twelve Data `/time_series` supports OHLC history and output sizes up to 5,000 records.
- The provider requires an API key and enforces plan-specific API-credit limits.
