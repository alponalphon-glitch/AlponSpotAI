# NEXORA Scenario Intelligence — 2026-11-23

Added a hidden five-part analysis layer:
1. Bullish, bearish and sideways conditional scenarios with explicit triggers and invalidation levels.
2. Higher/lower timeframe agreement summary using the existing MTF readings.
3. Liquidity sweep / structure-break trap warning using existing market-structure evidence.
4. Confidence calibration from closed Paper Trading outcomes in the same market regime; fewer than 10 samples is explicitly marked uncalibrated.
5. No-Trade Intelligence when candle warm-up, MTF, regime, or directional alignment is insufficient.

The layer is attached to `DecisionIntelligenceReport` and participates in the final gate: BUY is vetoed unless the bullish scenario leads; SELL receives a warning unless bearish scenario leads. The layer is internal and adds no menu. It does not place orders and does not promise predictive accuracy. Scenario levels are recent-range breakout/breakdown references, not guaranteed support/resistance.
