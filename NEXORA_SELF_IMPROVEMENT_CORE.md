# NEXORA Hidden Self-Improvement Core

Seven internal capabilities were added to the central decision pipeline. They are not navigation/menu items.

1. **Prediction vs Reality** — summarizes realized closed Paper Trading returns. The current outcome record has no predicted-direction label, so directional forecast accuracy is deliberately not fabricated.
2. **Failure Autopsy** — reports loss count and mean realized loss. Causal attribution is withheld until timestamped entry/exit, fees, slippage and market snapshots are persisted.
3. **Knowledge Graph** — exposes measured benchmark correlation and candle-derived liquidity observations as descriptive relationships, not causation.
4. **Regime Memory** — groups closed Paper Trading outcomes by observed regime label.
5. **Overfitting Detector** — flags strategies with reliable training folds but failed/negative walk-forward out-of-sample evidence after minimum sample thresholds.
6. **Decision Replay** — reports replay availability honestly. Full deterministic replay requires immutable market, feature, model and configuration snapshots; it is not claimed with current stored outcomes alone.
7. **Intelligence Supervisor** — checks data integrity, market shock and red-team robustness; directional proposals fail closed when critical checks fail.

The report is internal and appended to `DecisionIntelligenceReport.hiddenAdvancedAudit`. The core feeds blockers into the existing `DecisionIntelligenceEngine` final gate. It does not add UI/menu elements, enable exchange orders, or claim consciousness / guaranteed profitability.

## Validation

- Standalone Kotlin compilation of `NexoraSelfImprovementCore.kt` with type-compatible stubs: PASS.
- Full Gradle/Android compile: NOT RUN successfully; Gradle wrapper attempted to download Gradle 8.9 but DNS resolution for `services.gradle.org` failed.
- ZIP integrity: validated after packaging.
