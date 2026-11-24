# Adaptive Paper-Trade Learning

## What it does

- Stores up to 100 closed Paper Trading outcomes on-device.
- Associates each simulated position with the market regime observed at entry.
- Evaluates the latest 30 outcomes for the same regime (TRENDING, RANGING, HIGH VOLATILITY, etc.).
- Uses an 8-trade warm-up before changing position allowance.
- After at least 12 same-regime outcomes, blocks new BUY entries when win rate is below 40% and average return is non-positive.
- Reduces the maximum position allowance by 50% when recent same-regime results are weak.
- Never increases baseline risk because of a profitable streak.
- Adds the learning status to the AI report warnings so the decision is explainable.

## Compatibility

Existing saved Paper Trading positions with the previous five-field format load as regime `UNKNOWN`; new positions save their entry regime. Existing closed-trade history remains readable and unchanged. The adaptive outcomes are stored separately in SharedPreferences.

## Limitations

This is a conservative feedback mechanism based only on simulated Paper Trading outcomes. It is not a trained neural network and cannot prove future profitability. Results are regime-specific but not yet split by symbol or setup type. It does not use live exchange fills. Paper Trading costs and execution may differ from real trading.

## Verification

The standalone Kotlin engine test covers warm-up, regime separation, weak-performance risk reduction, persistent-loss blocking, no risk increase after positive performance, and encode/decode persistence. Full Android Gradle build still needs verification in Termux or GitHub Actions.
