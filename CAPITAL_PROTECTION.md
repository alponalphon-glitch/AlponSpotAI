# Capital Protection and Trade Quality Gate

## Added controls

- Default planned risk is 0.5% of account equity per trade (user-adjustable in the UI).
- Default maximum position allocation is 25% of account equity.
- Position sizing estimates per-unit loss using entry/stop adverse slippage and entry/exit fees.
- AI Trade Quality Gate blocks a new BUY unless technical BUY, confidence threshold, directional score, at least four active analysis components, component agreement, volatility and false-breakout checks, and net reward/risk after estimated costs pass.
- Net reward/risk threshold defaults to 1.5:1 using TP2, estimated fees and slippage.
- Backtests use 0.5% default risk and include daily 2% / weekly 5% loss pauses plus a three-consecutive-loss circuit breaker.
- Backtest maximum drawdown now includes mark-to-market open-position equity, not only closed trades.
- Paper Trading BUY requires the AI gate, is capped at 25% of paper equity, and applies persisted daily/weekly and loss-streak guards. Paper SELL remains available to close/reduce a Spot position.

## Important limitations

- Confidence is a heuristic score, not a calibrated probability of profit.
- Fees and slippage are estimates; actual fills, spread, market impact, outages and price gaps can produce larger losses.
- The current app reads Binance Spot market data and simulates Paper Trading; these changes do not create live order execution or guarantee protection of a real exchange account.
- Loss limits stop new entries; they do not automatically liquidate an existing position.
- The loss guard is persisted for Paper Trading on this device. Backtest guard state is recreated for each backtest run.

## Verification

- All domain Kotlin sources compiled with `kotlinc` against a Candle model stub.
- Synthetic checks passed for the trade quality gate, Spot exit semantics, consecutive-loss circuit breaker, fee/slippage-aware sizing and 25% capital cap.
- Full Android Gradle build was not run in this environment; Android UI integration still needs `:app:assembleDebug` verification in Termux or GitHub Actions.
