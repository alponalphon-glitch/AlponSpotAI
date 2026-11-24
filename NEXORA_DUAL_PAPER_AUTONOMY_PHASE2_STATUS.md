# NEXORA Dual Paper Autonomy — Phase 2 Status

## Implemented in this patch
- Added `DualPaperAutonomousCoordinator.kt` as an autonomous, paper-only trade management core.
- Separate persisted Spot/Futures virtual ledgers and daily statistics via local SharedPreferences.
- Signal prototype uses EMA20/EMA50 crossover, RSI14 band confirmation, ATR14 stops, and 2R target.
- Spot is long-only; Futures allows long/short but is fixed at 1x.
- Risk controls in coordinator: 0.5% risk-budget sizing, 25% position notional cap, max 5 entries/day/account, max 3 simultaneous positions/account, 2% daily loss stop, 3-loss pause.
- Position manager supports stop, target, and signal-flip exit.
- This module never calls exchange order endpoints.

## Not yet connected
- The coordinator is not yet instantiated or called by `SpotViewModel`.
- It is not yet connected to Binance Spot/Futures market scanners, the UI toggle/status, or Android background scheduling.
- Strategy is a deterministic rule-based prototype, not an ML prediction or proof of profitability.
- Full Android build was not run: this ZIP has no Gradle wrapper distribution and the environment has no installed Gradle.

## Next integration work
1. Wire scanner data into this coordinator (Spot top liquid USDT/USDC markets and Futures USDT perpetual markets).
2. Persist controller state and expose per-account status/kill switches in UI.
3. Add bounded periodic scan scheduling with feed integrity gates, duplicate-candle guard, and stale-feed pause.
4. Run build/compile and focused paper-ledger tests on an Android/Gradle environment.
