# NEXORA CENTRAL AI BRAIN — integration notes

## What changed

- Added `NexoraCentralAiBrain.kt` as the single aggregation/reporting point for the current market snapshot.
- `DecisionIntelligenceEngine` now assembles one central report after the specialist engines and Master Control final gate have completed.
- The central report receives the AI signal generator, independent analyst council, market integrity, technical quality, unified evidence, quantitative research, adaptive model reports, advanced market specialists, strategic/cognitive diagnostics, Red Team, scenario risk, market-shock guard, Futures derivatives, external context, walk-forward status, historical memory, paper portfolio snapshot, and final Master Control decision.
- The Decision Intelligence screen displays the central report, node status, decision-input vs context labels, blockers, and execution policy.
- Historical Memory / Intelligent Waiting now reads up to the latest 10,000 locally stored candles for the selected instrument/timeframe from Room and passes the resulting report into the central decision flow. When the historical engine finds a strong opposite-bias or wait recommendation, a proposed Spot BUY is blocked. The available sample depends on history the user has actually downloaded; this does not guarantee 1–3 years of coverage.
- Portfolio Intelligence now receives a paper-portfolio snapshot (open symbols, invested notional, and virtual equity). It is not connected to a live Binance account or a full cross-asset correlation exposure model.
- Added an opt-in Auto Paper Trading switch. It is OFF by default. When enabled, Binance Spot only, the app may open one simulated BUY per confirmed candle after all existing gates approve it. Default allocation is up to 10% of virtual equity and adaptive capital-protection limits may reduce it. The existing dynamic paper exit logic manages open simulated positions.
- Futures remains analysis-only. The final central gate downgrades Futures BUY/SELL proposals to WAIT for execution; it does not submit exchange orders.

## Architecture and limits

`NEXORA CENTRAL AI BRAIN` is an explainable deterministic control plane over the existing analysis modules. The analyst perspectives are rule-based; the experimental Deep Learning / Reinforcement Learning / strategy-evolution reports are admitted only when their own holdout checks accept them. The application does not contain a general-purpose hosted LLM or independently reasoning LLM agents. A confidence score is a heuristic score, not a calibrated probability of profit.

Some modules are marked as context/diagnostics rather than independent votes to avoid double-counting correlated indicators or treating candle proxies as verified order-book depth. The central brain can only use feeds and history actually supplied by the app.

## Safety

- No live Binance Spot or Futures order placement was added.
- Auto Paper Trading is user opt-in and uses virtual balance only.
- Futures leverage, margin, liquidation distance, position sizing, and live execution remain disabled/unverified.
- Missing, stale, conflicting, or insufficient evidence continues to fail closed to WAIT.
