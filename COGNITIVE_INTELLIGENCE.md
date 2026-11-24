# ALPON Cognitive Intelligence Upgrade

This upgrade unifies five explainable decision-support systems in the Home analysis view:

1. **Market Regime Intelligence** reuses the existing ADX/DI, efficiency-ratio and ATR-regime engine.
2. **Multi-Scenario Analysis** presents bullish, bearish and neutral conditions with explicit triggers and invalidation conditions. Evidence scores are heuristic, not probabilities.
3. **Liquidity Intelligence** combines Binance candle taker-buy/sell volume, relative volume, Smart Money liquidity pools and breakout warnings. It does not claim to be a full live order-book depth heatmap.
4. **Mistake Intelligence** reviews closed Paper Trading outcomes by regime. It requires at least eight outcomes before flagging a weak regime and explicitly avoids claiming causal explanations from outcome correlations.
5. **Smart Risk Controller** surfaces the existing quality gate, adaptive paper guard and capital protection limits. It does not place live orders or increase baseline risk.

The existing MarketRegimeAnalysis, OrderFlowAnalysis, SmartMoneyAnalysis, AdaptiveLearningEngine and CapitalProtectionEngine remain the source of their respective calculations. This is deterministic rule-based analysis, not a trained neural network.
