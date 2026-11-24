# Long-horizon ALPON SPOT AI upgrade

## Added analysis
- Twelve Binance intervals: 1M, 1W, 3D, 1D, 12H, 8H, 4H, 2H, 1H, 30M, 15M, 5M.
- Daily 1-year and 2-year rolling trend context, plus 24 monthly and 104 weekly candles where Binance history is available.
- Price action: swing highs/lows, HH/HL and LH/LL structure, slope, EMA context, candle patterns, Fibonacci retracements and volume confirmation.
- Chart: recent swing pivots, multiple support/resistance trend segments and Fibonacci levels, in addition to existing EMA, Bollinger, VWAP and volume overlays.

## Scope and limitations
This does not reproduce every TradingView built-in study, community script or proprietary tool. The project already contains a broad 123-reading indicator engine; this update adds a selected core price-action toolkit. The engine is deterministic and rule-based, not a neural network trained on labeled trades. It does not learn from live outcomes yet, and its confidence score is not a probability of success. Historical coverage varies by symbol listing date and Binance availability.

## Verification
Domain Kotlin sources compiled independently and a synthetic fixture confirmed the 2-year daily window, monthly window and 12 timeframe readings. A full Android/Compose Gradle build and live Binance device test are still required.

## 2026-11-22 — Smart Money and price-action quality upgrade

- Added `SmartMoneyAnalysis` using closed Binance candles: unmitigated order-block candidates after displacement, equal-high/equal-low liquidity pools, liquidity sweeps, volume-aware breakout confirmation and possible false-breakout warnings.
- Added Smart Money as a separately weighted AI consensus component; it contributes to BUY/SELL/WAIT scoring and adds a warning when a false breakout is suspected.
- Added a Smart Money dashboard card showing bias, score, active order blocks, liquidity pools and breakout quality.
- Added order-block zones and liquidity-pool levels to the interactive candlestick chart.
- Improved market-structure input validation to exclude an unclosed candle and invalid OHLC rows.
- Trendlines now use rising swing lows for support and falling swing highs for resistance, and chart lines project to the latest visible candle.
- Expanded daily candle-pattern recognition with Morning/Evening Star, Piercing Line, Dark Cloud Cover, Three White Soldiers and Three Black Crows.
- Verification: all domain Kotlin files compiled with `kotlinc`; a 400-candle synthetic fixture exercised Market Structure and Smart Money outputs. Full Android/Compose build and live Binance/device validation remain outstanding.
- Interpretation note: order blocks and liquidity pools are rule-based price/volume approximations; OHLCV candles cannot reveal every resting order or the full order book. No pattern guarantees a trade outcome.
