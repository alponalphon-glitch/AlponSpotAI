# Streaming Backtest update

## What changed

Backtest no longer loads all saved candles into an `ArrayList`. It reads up to 1,000 Room rows at a time and feeds them into `BacktestingEngine.runFromPages`.

The engine keeps:
- a rolling window of at most 211 candles for the existing indicator calculations;
- incremental EMA state from the first stored candle, including the EMA value three bars earlier;
- the trade ledger and running performance statistics.

The existing `run(List<Candle>)` API remains available and uses the same runner.

## Preserved behavior

- Existing candle validation and chronological ordering.
- EMA SMA-seeded initialization and EMA slope comparison.
- Signal evaluation against the previous closed candle and execution against the next candle.
- BUY-only Spot entries, SELL exit behavior, stop-first intrabar handling, fees, slippage, and reward/risk settings.

## Verification status

- Domain Kotlin sources compiled successfully in an isolated Kotlin compilation.
- A synthetic regression fixture with 215 candles produced identical prior-engine, in-memory, and paged-stream results: 5 signals and 0 trades.
- Full Android Gradle build remains unverified here because Gradle 8.9 could not be downloaded in this environment. Verify with `./gradlew :app:assembleDebug` before installing.
