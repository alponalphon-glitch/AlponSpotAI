# Alpon Spot AI

Native Android Spot-market analysis application built with Kotlin, Jetpack Compose, Retrofit and Binance public Spot REST API.

## Market analysis
- Binance Spot live ticker and OHLCV candles (public endpoints; no API key required).
- Candlestick timeframes: 1m, 5m, 15m, 1h, 4h and 1d in the scanner.
- 123 calculated indicator / price-action readings across trend, momentum, volatility, volume, breakout, levels and candle-pattern groups. Multiple lookback periods are evaluated for EMA, SMA, RSI, ROC, momentum, Bollinger %B, Stochastic, Williams %R, CCI, ATR, MFI, OBV, volume ratio, ADX/DI, Donchian, Aroon and CMF, plus MACD variants, Ichimoku components, VWAP, pivot levels and candle patterns.
- Family-balanced confirmation score and BUY / SELL / WAIT decision. The newest potentially incomplete Binance candle is excluded from the indicator calculations.
- ATR and recent-structure based illustrative stop-loss and three take-profit levels.
- Trade-plan screen shows the next candle close time, an estimated review horizon based on the selected timeframe, and a confirmation-based entry note. It does not promise an exact entry or holding duration.
- Money management calculator estimates risk amount and position size from user-entered USDT balance, risk percentage and entry-to-stop distance.
- Scanner evaluates the 25 highest quote-volume USDT Spot pairs.
- Local portfolio tracking with current market value and unrealized P/L estimates.

## Crypto news tab
- The third bottom-navigation tab is News (the previous Scanner destination is no longer a bottom tab).
- Reads public RSS headlines from Yahoo Finance Crypto and the CoinMarketCap blog; no API key is required.
- Displays only items with a valid publication timestamp within the last 24 hours, removes duplicate links, and sorts newest first.
- Refreshes automatically every five minutes while the News tab is open; users can also refresh manually.
- Tapping a headline opens the publisher's original page. If a feed is unavailable or has no qualifying recent items, the app shows an unavailable/empty state rather than fabricated headlines.
- Publisher headlines and summaries remain in their original language; the News navigation label follows the selected app language.

## Safety and limitations
- Analysis only: no API key, account access, order placement or automatic trading.
- Binance public API availability and rate limits depend on network and region. Errors are surfaced rather than replaced with invented prices.
- Technical readings are deterministic calculations, not machine-learning predictions or guaranteed outcomes. A score is a rule-based confirmation score, not a probability of profit.
- SELL is described as a possible exit/reduction signal for existing Spot holdings, not a short-selling instruction.
- Stop and target levels are analytical reference levels, not exchange orders. Fees, slippage, liquidity and gaps can increase losses. The user must verify all levels manually.
- The suggested review horizon is a rough timeframe-based planning aid, not a recommended guaranteed holding period.

## Build
GitHub Actions builds the debug APK on push, pull request or manual workflow dispatch. It uses JDK 17 and Gradle 8.9.

```bash
gradle --no-daemon :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Adaptive navigation and language settings

The app includes a top-right three-line menu for My account, Settings, and Support, plus a local-only display-name profile. Settings offers 20 language choices and saves the selection on-device. Primary navigation, menu, account, and settings labels are translated for the included language dictionaries. Trading-specific indicator and analysis terminology remains in English in this release. The Compose screen shell uses full-size layouts and scrollable settings/profile content to adapt to different Android phone dimensions.

A Telegram support shortcut is configured for `@alpon_alphon`.

### Binance Spot pair selector
The Home chart and Markets tab include searchable Binance Spot markets populated from `exchangeInfo` and 24-hour ticker data. The selector loads up to 500 active USDT pairs and up to 500 active USDC pairs, sorted by quote volume within each quote asset. Selecting a pair refreshes its ticker, candlesticks, and analysis. The scanner remains focused on USDT pairs.

### Forex market
The chart's upper-right market switch separates `BINANCE SPOT` and `FOREX`. Forex uses Twelve Data, not Binance. Add your own Twelve Data API key in the Forex connection dialog; the key is stored in local app preferences and is not embedded in the project. The Markets tab loads up to 500 provider-supported Forex pairs (actual count depends on the provider) and supports search by symbol or pair name.

Forex chart intervals are 15m, 1h, 2h, 4h, 1d, 1w and 1M. The app requests up to 1,000 daily OHLC candles, which exceeds one year when the provider returns the requested history. Forex analysis requires at least 600 confirmed daily candles and seven complete timeframe readings; otherwise it returns WAIT. Only the selected pair's history is downloaded to control API-credit use.

Forex aggregate OHLC data does not include centralized market volume, and this build has no broker bid/ask spread or execution-cost feed. It therefore displays Forex analysis but keeps directional BUY/SELL proposals at WAIT and disables Forex orders. Binance Spot paper trading and Spot-only SELL rules remain separate. See [`FOREX_MARKET_INTEGRATION.md`](FOREX_MARKET_INTEGRATION.md) for implementation details.

## ALPON Decision Intelligence & Data Integrity

See [`ALPON_DECISION_INTELLIGENCE.md`](ALPON_DECISION_INTELLIGENCE.md) for the fail-closed decision flow: strict Binance data checks, all 12 timeframes, independent rule-based analyst opinions, false-breakout and cost-aware signal verification, Red-Team adversarial review, Walk-Forward acceptance rules, and Spot-only partial/full exits. New BUY entries remain blocked until data, strategy validation, risk and adversarial checks pass. This is deterministic decision software, not a trained neural network or a guarantee of future returns.


## Version 1.4.1

Adds historical downside stress analysis for proposed Spot BUY entries. The additional gate compares stop distance with 10/20/50-candle downside excursions and fails closed when stress coverage cannot be verified. This remains analytical/Paper Trading functionality; no live orders are sent.


## Futures derivatives intelligence and deep technical ensemble

- Binance USDⓈ-M / COIN-M futures candles pass through the same technical, market-structure, 12-timeframe, price-action and risk-analysis pipeline used by Spot.
- A parameter-grid ensemble evaluates more than 1,000 configurations across EMA crossovers/slopes, RSI thresholds, MACD, Bollinger bands, Donchian breakouts, ATR trend variants, ADX/DI, regression trendlines, volume flow and Fibonacci swing levels. Correlated configurations are averaged by family; they are not represented as 1,000 independent indicators or independent votes.
- Futures-only overlay retrieves mark/index price, current funding, open interest and its recent history, global long/short account positioning and taker buy/sell flow. It flags crowded positioning, sudden open-interest expansion and mark/index dislocations.
- If derivatives data is missing, stale or inconsistent, Futures analysis fails closed. Directional analysis is displayed separately from trade permission.
- Futures order execution remains disabled: leverage, margin mode, position size and liquidation-distance controls are not yet configured. No live orders are sent.
- This is deterministic statistical/rule-based software, not a human-like mind, trained neural network or profit guarantee.
