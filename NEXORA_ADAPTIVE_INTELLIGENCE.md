# NEXORA Adaptive Intelligence — 1.6.0

## Connected research modules

### 1. Deep Learning shadow model
- Compact feed-forward neural network: 5 normalized candle-derived inputs, one 8-unit tanh hidden layer, sigmoid output.
- Trains on chronological 70% of valid closed-candle examples; the final 30% is a holdout and is not used for gradient updates.
- Reports latest next-candle up score, holdout accuracy and balanced accuracy.
- It enters Unified Brain only at low weight (0.25) and only when balanced holdout accuracy is at least 52.5% with at least 60 test examples.
- This is a small on-device neural baseline, not a large pretrained deep-learning model. Weights are recalculated in memory and are not persisted.

### 2. Reinforcement Learning shadow policy
- Tabular Q-learning with 9 discretized momentum/volatility states and two actions: flat or long.
- Q-values are learned only on the first chronological 70%; the last 30% is a frozen-policy holdout.
- Reward includes a 0.10% modeled transaction cost on position changes.
- Preliminary shadow gate: at least 8 completed trades, positive holdout return and no more than 12% holdout drawdown.
- The RL policy is not connected to order placement and cannot override risk controls.

### 3. Strategy Evolution laboratory
- Bounded genetic search over EMA fast periods 4–24 and slow periods 25–100.
- Candidate selection uses only the chronological training segment and penalizes drawdown and undersampled candidates.
- The selected candidate is evaluated once on the untouched final 30%, compared with fixed EMA 12/26.
- Shadow promotion requires at least 10 holdout trades, positive return, better return than the fixed baseline and holdout drawdown no greater than 10%.
- Promotion means shadow research only, never automatic live deployment.

## Safety and interpretation
- All calculations use valid closed candles and a maximum rolling input of 1,200 candles.
- Minimum warm-up is 420 candles. Insufficient history returns unavailable/warm-up results.
- Results are deterministic for identical input data and seeded initializations.
- The modeled fee is a simple 0.10% per position change; real exchange fees, spread, slippage, funding, latency and order-book impact may differ.
- Synthetic smoke tests verify execution, determinism and holdout plumbing; they do not establish profitability. Real-market validation across symbols, intervals, market regimes and unseen future periods is still required.
- No module guarantees returns or gives permission to bypass Data Integrity, Capital Protection, Red-Team, Walk-Forward or existing trade gates.
