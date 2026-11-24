# ALPON Decision Intelligence & Data Integrity

## Decision flow

1. **Data Integrity Engine** validates the Binance ticker, selected timeframe, OHLCV values, timestamps, duplicates, recent candle gaps, freshness and all 12 Binance-native timeframes. It excludes still-forming candles. The selected timeframe needs at least 220 closed candles; all 12 frames must be available; the deep-analysis gate also requires at least 600 daily candles for a 1–2 year context.
2. **Deep Market Analysis** combines the existing technical indicators, market structure, market regime, price action, Smart Money/liquidity and multi-timeframe modules.
3. **Independent Analyst Engine** produces separate deterministic rule-based opinions for momentum, multi-timeframe trend, market structure, regime, long-horizon price action and liquidity/breakout quality. These are not separate LLMs or trained neural networks.
4. **Signal Verification / Decision Intelligence** requires analyst agreement, rejects higher/lower timeframe conflict, rejects false breakouts, blocks high-volatility entries, validates directional Stop Loss/Take Profit levels and checks net reward/risk after estimated fees and slippage. BUY is fail-closed: any critical blocker means WAIT.
5. **Red-Team Adversarial Review** actively searches for evidence against a BUY: abnormal range expansion, upper-wick rejection, high-volume weak bearish closes, rejected gap-ups, short-term reversal against the broader trend, fragile score margin, and poor reward/risk under 2× fee/slippage plus a 0.10 ATR adverse entry scenario. Any critical counter-evidence blocks BUY.
6. **Learning & Validation** combines the existing regime-specific Paper Trading feedback with chronological Walk-Forward validation. A strategy is not accepted for new BUY entries unless there are at least two unseen test folds, at least ten out-of-sample trades, reliable training samples, positive compounded out-of-sample return, at least half profitable test folds, average test drawdown no greater than 10%, and no test fold with profit factor at or below 1.0.

## Spot execution rules

- SELL can only reduce an existing Paper Spot holding; it cannot open a short position.
- Paper SELL supports 25%, 50% and 100% exits and rejects quantities larger than available holdings.
- BUY execution requires both the existing AI Trade Quality Gate and the new Decision Intelligence gate.
- The scanner labels single-timeframe discoveries as unverified watch candidates, not trade signals.

## Important limitations

- Analysis and analyst opinions are deterministic rule-based software, not independent AI model calls or a trained neural network.
- Walk-Forward currently validates the existing backtest strategy and reward/risk parameter on chronological unseen windows; it does not prove future profitability.
- Fee/slippage values are estimates configured in the existing protection engine; Red-Team stress testing doubles these estimates and adds a 0.10 ATR adverse entry scenario. These are stress assumptions, not a live Binance account fee schedule or order-book execution simulation.
- Red-Team candle anomaly checks use OHLCV candles and cannot see hidden liquidity, spoofing, full order-book depth, or all news/event risks.
- This build gates Paper Trading only; it does not add authenticated live Binance order execution.


## Historical downside stress intelligence (1.4.1 upgrade)

A new ScenarioRiskEngine evaluates confirmed candle history across 10-, 20-, and 50-candle forward downside windows. It reports empirical 95th-percentile and worst adverse excursions, then compares the proposed BUY stop distance against the largest horizon stress reference plus a half-largest-single-candle adverse-move buffer. BUY is fail-closed if the history or stop coverage cannot be verified. These statistics are historical stress references, not calibrated probabilities or guaranteed loss limits; see `SCENARIO_RISK_INTELLIGENCE.md`.
