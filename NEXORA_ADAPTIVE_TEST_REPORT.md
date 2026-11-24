# Adaptive Intelligence — local smoke-test report

Date: 2026-11-23

## Checks performed

- Standalone Kotlin compilation of `NexoraAdaptiveIntelligence.kt`: PASS.
- Kotlin compilation of `AlponUnifiedBrainEngine.kt` against minimal type stubs, including the new adaptive-report integration: PASS.
- Deterministic repeat run on identical synthetic candles: PASS.
- Holdout-isolation check: changing only the synthetic holdout tail did not change the evolved EMA parameters selected on the training segment: PASS.
- Insufficient-history behavior (300 candles, below 420-candle minimum): PASS; all three engines correctly returned unavailable/warm-up.
- Runtime smoke test on 900 synthetic candles: PASS; all engines completed without exception.

## Important limits

These are synthetic-data and standalone Kotlin checks only. They are not a full Android application build, GitHub Actions result, real-market backtest, profitability test, or proof that any strategy has an edge. The Android project was not built here. Run the repository's existing GitHub Actions workflow and then evaluate on real, unseen market history across multiple symbols and intervals before using any research output beyond shadow mode.
