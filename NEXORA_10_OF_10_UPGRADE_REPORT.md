# NEXORA — Engineering Upgrade Report (2026-11-23)

## Preservation
- Built from `NEXORA_CENTRAL_AI_BRAIN_ADAPTIVE_RISK_2026-11-23.zip`.
- No existing project file was intentionally deleted or renamed.
- Original versions of the four edited Kotlin files were copied into `backups/` with the suffix `before-10-of-10-20261123`.
- Existing Binance Spot, Futures analysis, charts, UI, paper-trading, and other modules were retained.

## Added capability: Monte Carlo Paper Risk Lab
- New source: `app/src/main/java/com/alpon/spotai/domain/MonteCarloPaperRiskEngine.kt`.
- Uses up to the latest 100 valid closed paper-trade outcomes.
- Requires at least 20 outcomes before producing a report.
- Runs deterministic bootstrap resampling (2,000 paths by default; 100 simulated trades per path).
- Reports the 5th-percentile and median ending return, 95th-percentile maximum drawdown, and the share of resampled paths that reach a 10% drawdown.
- Treats each saved trade return as a return on allocated capital and defaults to 25% account exposure per trade.
- Appears as a diagnostic node in the Central AI Brain and Master Control Brain.
- Does not create or authorize orders and does not change BUY/SELL decisions.
- The output is a historical bootstrap stress diagnostic, not a calibrated probability, forecast, or profit guarantee. It assumes exchangeable outcomes and cannot fully model serial dependence, regime shifts, future liquidity, or unrecorded costs.

## Risk-engine hardening
- The legacy risk planner now rejects non-positive or invalid stop prices rather than returning an unusable plan.
- Invalid capital-allocation percentages are rejected instead of silently clamped.
- The allocation-cap warning now displays the actual configured cap rather than always saying 25%.

## Verification performed
- Kotlin compiler smoke test passed for the Monte Carlo engine, adaptive risk manager, dynamic trade manager, and legacy risk planner.
- Tested Monte Carlo warm-up behavior, deterministic repeatability, drawdown range, invalid-stop rejection, adaptive sizing against the risk budget, and trailing-stop monotonicity.
- ZIP integrity and full-project Android compilation are separate checks. Android build could not be completed in this environment because the Gradle wrapper had to download Gradle 8.9 and DNS/network resolution for `services.gradle.org` failed.
- No live Binance order execution was added. Futures remain analysis-only; Spot auto mode remains Paper Trading only.

## Important limitation
A software project cannot honestly be certified as 10/10 or profitable from source inspection alone. Full Android build, device testing, exchange-data validation, long-duration paper trading, and out-of-sample performance evaluation are still required.


## Validation Governance extension (2026-11-23)

Added a central governance report and wired it into Master Control Brain and NEXORA Central AI Brain. It separately tracks Deep Learning and Reinforcement Learning shadow maturity, the observation span / closed-trade count / regime coverage of Paper Trading outcomes, Futures margin/liquidation verification status, and the historical-only interpretation of Monte Carlo output. Futures execution remains disabled and no live order path was added. See `NEXORA_VALIDATION_GOVERNANCE.md`.
