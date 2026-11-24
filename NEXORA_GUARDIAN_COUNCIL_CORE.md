# NEXORA Guardian & Council Core — 5 hidden capabilities

This update adds five deterministic, evidence-bounded checks to the existing final decision gate:

1. **Market Data Guardian** — checks the existing data-integrity result and performs a second sanity pass over the latest 30 closed candles.
2. **Signal Confidence Engine** — measures agreement among meaningful analyst directions. The percentage is agreement, not a calibrated probability of profit.
3. **Liquidity Trap Detector** — uses the existing Smart Money breakout classification and swept liquidity-pool observations. It does not claim live order-book depth.
4. **Portfolio Correlation Guard** — blocks duplicate open-symbol exposure and flags high benchmark correlation when invested exposure is already elevated. This is a benchmark proxy, not full pairwise portfolio covariance.
5. **AI Council** — checks for material disagreement among the existing deterministic analyst modules. These are not separate LLM agents.

The checks are hidden from menus and append internal audit lines. Their blockers feed the existing final decision gate for BUY/SELL proposals. No existing files or Spot/Futures modules were intentionally removed; no live exchange order path was added.

## Validation limits

- Standalone Kotlin compilation of `NexoraGuardianCouncilCore.kt` was tested with type-compatible stubs.
- A complete Android Gradle build must still be run in an environment with the project's Gradle distribution and dependencies available.
- Signal agreement is not a win probability; candle-derived liquidity is not order-book depth; benchmark correlation is not a complete portfolio covariance model.
