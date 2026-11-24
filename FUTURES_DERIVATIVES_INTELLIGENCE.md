# Futures Derivatives Intelligence

## Shared Spot/Futures analysis
Futures OHLCV candles are passed through the existing technical-analysis, market-structure, multi-timeframe, long-horizon price-action, Smart Money, adaptive-learning, Red-Team and capital-protection modules. The Futures loader requests 12 timeframes.

## Derivatives overlay
The public Binance endpoints supply mark price, index price, current funding rate, next funding time, current open interest, recent open-interest history, global long/short account ratio and taker buy/sell ratio. The engine calculates mark/index basis and open-interest change over the returned sample.

Crowding is treated as a risk clue rather than a directional guarantee. Elevated funding and a crowded long/short ratio can warn of squeeze risk; taker flow is treated as short-term aggressive participation; open-interest expansion is not assumed to be bullish or bearish on its own. Stale, missing, mismatched or invalid derivatives data blocks directional Futures permission.

## Deep parameter ensemble
The deterministic engine evaluates more than 1,000 parameter configurations. It groups related configurations into families and averages each family before computing a combined score. This prevents a large number of similar EMA or RSI settings from overwhelming other evidence. Families include EMA, RSI, MACD, Bollinger, Donchian, ATR trend, ADX/DI, regression trendlines, volume flow and Fibonacci swing confluence. It also reports a regression trendline slope and recent support/resistance range.

## Safety boundary
This release does not calculate exact liquidation prices because user leverage, margin mode, maintenance-margin tier, position size and collateral are not configured. Futures execution remains disabled and the app displays WAIT for trade permission even when the analytical bias is bullish or bearish. No live exchange orders are sent.
