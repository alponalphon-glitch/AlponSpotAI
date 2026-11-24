# Walk-Forward Validation

## Purpose

Walk-Forward Validation checks whether a strategy's parameter choice survives on later candles that were not used to choose those parameters. It is a historical robustness test, not a promise of future profit and not a neural-network training process.

## Current implementation

- Runs two chronological rolling folds over the selected symbol/timeframe's locally saved Binance Spot candles.
- Each fold uses 60% training, 20% unseen test, and advances the training start by 20% of the full time span.
- Training evaluates reward/risk candidates `1.5`, `2.0`, `2.5`, and `3.0`.
- Candidate selection uses training net return minus `0.75 × max drawdown`; candidates with fewer than five training trades are treated as weak evidence.
- The chosen reward/risk is frozen for that fold's test window. Test results are stored and displayed separately from training results.
- During a test run, trading is disabled until the test boundary. Earlier candles are processed only to warm up causal indicators; no training-period trade is included in test statistics.
- The Room DAO supplies ascending pages bounded by the requested end timestamp. The engine does not accumulate the full candle history in memory.
- Existing backtest execution logic remains in use, including fees, slippage, Spot-only BUY/SELL semantics, daily/weekly loss pauses, and the consecutive-loss circuit breaker.
- The screen reports compounded test-window return, average test return, test trades/win rate, average test drawdown, profitable test windows, selected reward/risk values, and low-sample warnings. Each fold starts with fresh simulation capital and a reset loss guard; compounded return is an aggregate across independent windows, not a single continuous account path.

## Important interpretation

- The optimizer currently tunes only the reward/risk target multiplier. Risk per trade remains the user's selected fixed value; the optimizer does not increase account risk.
- Results are specific to the chosen coin, timeframe, stored date range, and simulation assumptions.
- A profitable training result is not proof of a robust strategy. Give greater weight to the unseen test windows, trade count, drawdown, and consistency across folds.
- Historical candles do not model every real-world issue such as latency, order-book liquidity, exchange outages, or market impact. Live trading can differ materially.
- The current layout uses timestamp fractions (not guaranteed equal candle counts), so missing candles can make windows unequal in sample count.

## Verification

- `WalkForwardEngine.kt`, `BacktestingEngine.kt`, and the required analysis/risk domain sources compile with `kotlinc` against a temporary Candle model.
- A deterministic window-planner smoke test passed: two rolling folds, strictly ordered training/test boundaries, advancing windows, and invalid-range rejection.
- Full end-to-end historical simulation and Android/Compose Gradle build were not completed in this environment. The local synthetic end-to-end attempt exceeded the execution time limit because the existing full technical-analysis calculation is expensive when repeatedly evaluated across many bars. Verify runtime on the target device and run `:app:assembleDebug` in Termux or GitHub Actions before installing.
