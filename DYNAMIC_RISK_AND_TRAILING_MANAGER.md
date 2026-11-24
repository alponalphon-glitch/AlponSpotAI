# Nexora Dynamic Risk & Money Management

## Implemented
- Keeps the existing conservative sizing and capital guards: default 0.5% account risk per trade, maximum 2% configurable risk, 25% maximum position allocation, estimated fees/slippage, 2% daily loss pause, 5% weekly loss pause, and a 3-loss circuit breaker.
- New Paper Spot positions receive an initial protective stop at 1.8 ATR below entry (with a 50% entry-price lower bound for numeric safety).
- No fixed take-profit is used by the automatic Paper Spot position manager. Trailing activates after price advances by +2% from entry (the default user-requested profit trigger).
- Once active, the trailing stop follows the best observed price at 1.6 ATR distance and can only move upward for a long position; it never loosens.
- A Paper position exits when price reaches the initial/trailing stop, or when a strong opposing SELL signal (score >=72) appears while the trade is profitable.
- Peak, stop and trailing activation are persisted in SharedPreferences. Existing saved positions remain readable; missing legacy ATR values use a conservative 2% entry-price fallback.
- The portfolio shows initial stop, live trailing stop, peak and activation status.

## Scope and safety
- Automatic stop/trailing exits are wired only to the Binance Spot live trade-price stream and close simulated Paper Trading positions.
- No live exchange orders are sent. Futures and Forex order execution are not implemented by this change.
- A stop may fill worse than its trigger during gaps, illiquidity or outages. The signal-strength score is heuristic, not a calibrated probability. No profit is guaranteed.

## Verification
- Standalone Kotlin tests passed: trailing activates at +1R; stop moves upward only; exit fires on stop hit; strong profitable opposing signal exits; weak opposing signal does not.
- Full Android Gradle build and device runtime test were not run.
