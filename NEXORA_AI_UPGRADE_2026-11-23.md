# NEXORA AI research upgrade — 2026-11-23

## Scope and preservation
- Continued from the complete Spot + Futures history project archive.
- No existing project file or AI module was deleted.
- Added one new domain engine and connected its report into the existing adaptive, unified-brain, orchestration, central-brain and diagnostic layers.
- The app UI was intentionally left unchanged; APK generation is left to the project owner.

## New walk-forward probabilistic research model
- Added `ProbabilisticForecastEngine` with 11 candle-derived features: 1/3/6/12/24-bar log returns, 12/24-bar realized volatility, short/long moving-average gap, relative range expansion, RSI and relative volume.
- Trains a regularized logistic model locally from closed candles only.
- Uses four expanding chronological test folds. Each fold is evaluated only on observations later than its training segment.
- Reports balanced accuracy, Brier score against a training-prevalence baseline, expected calibration error, cost-aware directional simulation return, drawdown and trade count.
- The model is accepted only if all configured out-of-sample, calibration-diagnostic, cost and drawdown gates pass.
- Accepted output is added to the unified brain as low-weight shadow evidence only. It cannot bypass data-integrity, decision, capital-protection, Red-Team, market-shock, Walk-Forward or Futures safety gates.
- The raw latest model score is explicitly not described as a calibrated win probability.

## Existing model preservation
- Existing Tiny MLP, Q-learning and evolutionary EMA research remain intact.
- Existing Spot and Futures APIs, market history, candle charts, 12-timeframe analysis, derivatives context and Paper Trading remain intact.
- Futures remains fail-closed for execution because leverage, margin mode, liquidation-distance and position-size controls are not verified/implemented as an executable risk model.
- No live exchange order execution was added.

## Source consistency correction
- Corrected a central-brain report property reference from `strategic.executionDetail` to the actual `strategic.executionRealityDetail` field.

## Verification
- The new probabilistic engine and existing adaptive-intelligence source compiled with `kotlinc` against the project's Candle data shape.
- A 1,200-candle synthetic run completed and produced finite diagnostics; the sample was correctly rejected because cost-aware out-of-sample return/drawdown gates failed. This is an expected fail-closed outcome, not a profitability claim.
- A full Android Gradle/APK build was not run. The project archive has no Gradle wrapper JAR/properties and this environment has no installed Gradle distribution. Build and on-device testing remain required in Termux or Android Studio.
