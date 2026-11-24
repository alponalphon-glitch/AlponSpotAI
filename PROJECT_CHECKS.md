# Project checks — 4D scanner update

- Updated the existing Kotlin/Jetpack Compose project; did not replace it with a new project.
- Added layered navy gradient cards and elevation/shadow styling for a deeper premium visual treatment.
- Reworked Scanner to show the selected Binance Spot pair's real candlesticks first, with green bullish and red bearish candles and volume bars.
- Scanner chart refreshes the selected pair through Binance public REST data every 15 seconds and follows the selected timeframe (15m, 1h, 4h, 1d).
- Added the requested three-position control below the chart: Chart (default/first), Analysis (middle), Scanner (last).
- Scanner results remain selectable; choosing a result switches the live chart to that pair.
- Added a source backup: `MainActivity.kt.backup-before-4d-scanner`.
- Static delimiter check passed.
- A full Android Gradle compile was not run in this environment; the original archive does not contain a Gradle wrapper distribution or wrapper JAR, and Gradle is not installed here. GitHub Actions remains configured to provision Gradle 8.9 and build the APK.

# Trade analysis and risk-management update — 2026-11-19

- Expanded the technical engine to 123 calculated indicator / price-action readings across multiple lookback periods and analysis groups.
- Added moving-average, RSI, ROC, momentum, Bollinger, Stochastic, Williams %R, CCI, ATR, MFI, OBV, volume-ratio, ADX/DI, Donchian, Aroon, CMF, MACD variants, Ichimoku, VWAP, pivot and candle-pattern checks.
- Signal scoring now balances indicator families so one family with many parameter variants does not dominate the result.
- Indicator calculations exclude the newest potentially open Binance kline when sufficient candle history is available.
- Added timeframe-aware candle-close clock and rough review/holding window, plus confirmation-based entry guidance.
- Added Stop Loss, TP1/TP2/TP3, risk/reward, account balance input, risk percentage input, cash-at-risk and position-size calculation to the middle Analysis tab.
- Spot SELL guidance is explicitly framed as a possible exit/reduction for existing holdings, not a short instruction.
- Verified the technical-analysis Kotlin source compiles with `kotlinc` against the Candle model; a 300-candle local test produced 123 readings.
- Full Android/Compose Gradle build remains unverified because Gradle and its wrapper JAR/distribution are not available in this environment. GitHub Actions is configured to provision Gradle 8.9.

## 2026-11-19 — adaptive menu and language settings update

- Added a top-right three-line menu with My account, Settings, and Support destinations.
- Added a return control for secondary pages; bottom navigation remains available on the primary app screens.
- Added a device-persistent language selector with 20 language choices.
- Localized the primary navigation, menu, account, and settings labels for the included language dictionaries.
- Added a local-only display-name profile; no Binance login or exchange credentials are collected.
- Added a Telegram support action using the configured Alpon support handle.
- Kept the Compose layout based on `fillMaxSize`, system-safe Scaffold insets, and scrollable settings/profile content for varying phone dimensions.
- Created `MainActivity.kt.backup-before-menu-localization` before editing.

Build verification: not completed in this environment. `./gradlew :app:assembleDebug` was attempted but Gradle 8.9 could not be downloaded because DNS/network access to `services.gradle.org` was unavailable. Kotlin syntax scan found no parser errors, but this is not a substitute for an Android Gradle build. The 20-language picker is present; in this update the primary navigation/menu/settings/account labels are localized, while trading-specific analysis copy remains English pending full translation review.

## Continuous analysis update (2026-11-19)
- Home dashboard refreshes the selected Binance Spot symbol and its closed-candle technical analysis every 15 seconds while the app is open.
- Scanner opportunity results refresh every 60 seconds while the Scanner tab is open.
- The active, still-forming Binance kline is excluded from the technical-analysis input to reduce signal repainting; a new confirmed signal can therefore appear after a candle closes, not on every polling cycle.
- Home now exposes the full indicator-reading matrix grouped by indicator family.
- Removed the product name from the in-app menu/page header and Support/About menu copy; package identifiers remain unchanged.
- The technical-analysis Kotlin source was compiled independently with Kotlin 1.9.0 against the project's Candle data model. Full Android/Compose Gradle build was not run in this environment.

## Binance Spot market selector update (2026-11-19)
- Added Binance `GET /api/v3/exchangeInfo` metadata retrieval.
- Market selector now builds a live list of up to 500 USDT-quoted Spot symbols whose exchange status is `TRADING` and whose Spot permission is not explicitly disabled.
- Joined eligible symbols with live `GET /api/v3/ticker/24hr` data and sorted them by 24-hour quote volume; excluded common leveraged-token suffixes (UP/DOWN/BULL/BEAR).
- Added a searchable market picker above the Home chart and Scanner chart. Selecting a pair switches the active symbol and reloads ticker, candles, and analysis from Binance.
- The list is exchange-driven, not a hard-coded list. The actual number can be below 500 when fewer eligible pairs are returned or Binance/network access is unavailable.
- ZIP integrity can be checked locally; a full Android Gradle build has not been run in this environment.

## Full-history Backtest optimization — 2026-11-22

- Backtest now reads the selected symbol/timeframe's saved Room candles in ascending 1,000-row pages, starting at the earliest stored candle. It no longer fetches only the latest 3,000 candles from Binance at run time.
- If fewer than 211 candles are saved, the UI reports that Binance history must be downloaded first.
- `TechnicalAnalysis.analyze` accepts optional precomputed EMA values while its existing call signature remains source-compatible through defaults.
- `BacktestingEngine` carries SMA-seeded EMA states forward from the first historical candle and retains the value from three candles earlier for EMA-slope checks.
- Non-EMA indicator inputs are limited to the latest 211 candles per evaluation; their largest lookback in this analyzer is 200 candles. This changes the repeated indicator work from reprocessing the entire prefix at every step to a bounded window.
- Verification: standalone Kotlin compilation of all domain sources passed. A generated 520-candle comparison matched all 310 incremental `Signal` results exactly against full-prefix analysis. A 360-candle Backtest comparison matched all reported metrics and trade fields against the previous engine exactly (150 signals evaluated).
- Limitation: Room rows are fetched in 1,000-row pages but currently accumulated into an in-memory list before the Backtest runs. The CPU/indicator calculation is optimized, but the next improvement should make the Backtest consume pages incrementally to bound RAM for extremely large 1-minute histories.
- Full Android Gradle/Compose build was not run: this ZIP has a custom `gradlew` launcher but no `gradle/wrapper` JAR/properties and no Gradle installation in this environment. Verify `:app:assembleDebug` in Termux or GitHub Actions before installing the APK.

## 2026-11-22 — Stream Backtest directly from Room

- Replaced the Backtest history `ArrayList` accumulation with `BacktestingEngine.runFromPages`.
- The ViewModel now reads the earliest saved candle and count, then passes Room pages of up to 1,000 candles directly to the engine.
- The engine retains only a rolling window of at most 211 candles, incremental EMA state, and completed trades; it does not retain the full candle history.
- Kept `BacktestingEngine.run(List<Candle>)` as a compatibility entry point, using the same streaming runner after sorting/filtering.
- Preserved existing EMA seeding, EMA three-bars-ago values, signal timing, execution order, fees, slippage, and trade accounting.
- Kotlin compilation of the domain engine and its analysis dependencies passed using a temporary Candle model.
- A 215-candle synthetic regression check compared the previous engine with both the in-memory compatibility entry point and the Room-page streaming entry point: all result fields and trade records matched (5 signals, 0 trades in that fixture).
- Full Android Gradle compilation could not be run in this environment because Gradle 8.9 was not cached and the distribution host was unreachable. Run `./gradlew :app:assembleDebug` in Termux or GitHub Actions before installing.

## 2026-11-22 — Long-horizon price action and 12-timeframe AI context

- Added `PriceActionAnalysis`: evaluates closed daily candles over rolling 1-year and 2-year windows, plus 104-week and 24-month context when available.
- Added deterministic swing-structure classification (higher-high / higher-low, lower-high / lower-low), linear slope, EMA context, Fibonacci retracement levels, daily candlestick formations (engulfing, hammer/pin bar, shooting star, doji, inside bar), and volume confirmation.
- Added Binance-native multi-timeframe readings for 12 intervals: 1M, 1W, 3D, 1D, 12H, 8H, 4H, 2H, 1H, 30M, 15M and 5M. Long horizons carry more weight than execution timeframes.
- Integrated long-horizon price action as an independent AI consensus component; missing data is surfaced as a warning and available component weights are normalized.
- Expanded market-structure chart overlays to show several recent swing-to-swing support/resistance trend segments, swing pivot markers and Fibonacci levels, alongside existing EMA 9/21/50/200, Bollinger Bands, VWAP and volume.
- Added a dedicated Long-Term Price Action card and a `2Y DAILY` shortcut. Binance's latest 1,000 daily bars cover at least two years for active markets with sufficient listing history; exact availability depends on the symbol.
- Verification: all domain Kotlin sources, including the new price-action and 12-timeframe engines, compiled with `kotlinc`. Synthetic test passed with 731 daily bars, 24 monthly bars and all 12 timeframe readings.
- Full Android/Compose Gradle build remains unverified in this environment. The AI is a deterministic rules-and-scoring engine, not a neural network trained on historical labels; confidence is not a calibrated probability. No strategy can guarantee profitable predictions.

## 2026-11-22 — Smart Money / advanced price-action upgrade

- Added closed-candle Smart Money analysis: order-block candidates, equal-high/equal-low liquidity pools, swept-pool detection, volume-aware breakout confirmation and possible false-breakout classification.
- Integrated Smart Money into the AI consensus as an independent weighted component; possible false breakouts add an explicit caution.
- Added a Smart Money dashboard card and chart overlays for active order blocks and liquidity levels.
- Improved Market Structure to filter invalid and unclosed OHLC candles; trendlines now require rising swing lows or falling swing highs and are projected to the latest visible chart candle.
- Expanded candlestick formations with Morning Star, Evening Star, Piercing Line, Dark Cloud Cover, Three White Soldiers and Three Black Crows.
- Preserved the existing project structure and created timestamped backups before editing MainActivity, MarketStructureAnalysis and AIAnalysisEngine.
- Verification: all domain Kotlin sources compiled with `kotlinc`; a 400-candle synthetic fixture produced valid Market Structure and Smart Money reports. Full Android/Compose build and live Binance/device test are not yet verified.

## Capital protection update

- Added `CapitalProtectionEngine` with an AI Trade Quality Gate and configurable conservative limits.
- Risk sizing now estimates fees and adverse slippage and defaults to 0.5% risk / 25% capital allocation.
- Backtest defaults to 0.5% risk, pauses new entries after 2% daily / 5% weekly loss or three consecutive losses, and measures open-position mark-to-market drawdown.
- Paper BUY requires AI gate approval, caps a position at 25% of paper equity, and persists the loss-guard state locally.
- Domain Kotlin compile and synthetic unit checks passed; full Android Gradle build remains unverified in this environment.

## 2026-11-22 — Walk-Forward Validation

- Added a chronological Walk-Forward engine with two rolling folds (60% training / 20% unseen test / 20% step).
- Each training fold evaluates reward/risk targets 1.5, 2.0, 2.5 and 3.0; it selects the parameter using net return penalized by maximum drawdown and freezes that value for the next unseen test window.
- Test runs warm indicators from earlier saved candles while suppressing all trades before the test boundary, preventing training-period trades from contaminating out-of-sample statistics.
- Added a bounded Room query for ascending candle pages ending at each fold's cutoff; full candle history is not accumulated in RAM.
- Added UI results for compounded and average out-of-sample return, test trade count/win rate, drawdown, profitable folds, selected reward/risk and low-sample warnings.
- Preserved existing capital protection, fees, slippage, Spot-only trade semantics and previous features. Backups created for MainActivity, MarketDatabase and BacktestingEngine before edits.
- Verification: domain Kotlin compilation passed; deterministic split-planner smoke test passed for two chronologically separated rolling folds.
- Limitation: end-to-end repeated historical simulation exceeded this environment's execution time limit, and full Android Gradle build could not run because Gradle 8.9 could not be downloaded (network/DNS unavailable). Runtime and APK build still require verification on-device/Termux or GitHub Actions.

## Cognitive Intelligence upgrade (2026-11-22)
- Added `CognitiveIntelligenceEngine.kt` and a Home dashboard card integrating all five requested systems.
- Reuses the existing Market Regime, Order Flow, Smart Money, Adaptive Learning and Capital Protection engines rather than duplicating their calculations.
- Adds three conditional scenarios (bullish/bearish/neutral) with triggers and invalidation conditions; evidence scores are explicitly not probabilities.
- Adds regime-bucketed Paper Trading outcome review with an 8-trade minimum before weak-regime flags; does not infer the cause of an individual loss.
- Surfaces the existing AI entry gate, paper loss guard and adaptive risk multiplier in the risk controller.
- Standalone Kotlin compilation of all domain source files passed (only pre-existing unused/shadowed-variable warnings).
- Cognitive engine regression checks passed: scenario generation, liquidity integration, weak-regime diagnostics, low-sample warm-up, high-volatility block and daily-loss block.
- Full Android Gradle/APK build remains unverified in this environment.

## 2026-11-22 — ALPON Decision Intelligence & Data Integrity

- Added `DataIntegrityEngine`: rejects incomplete/malformed/stale Binance data, duplicate or unordered timestamps, recent candle gaps, invalid OHLCV/taker-volume fields, mismatched/invalid ticker data and incomplete 12-timeframe coverage. Still-forming candles are excluded.
- Tightened `MultiTimeframeAnalysis`: all 12 Binance-native frames are required; each frame must have enough closed candles, valid fields, recent timestamps and no recent interval gaps.
- Added `IndependentAnalystEngine`: six deterministic rule-based perspectives (momentum, MTF, structure, regime, long-horizon price action, liquidity/breakout). These are not separate LLMs or trained neural networks.
- Added `DecisionIntelligenceEngine`: BUY is fail-closed on analyst disagreement, higher/lower timeframe conflict, false breakout, high volatility, missing deep analysis, invalid SL/TP, insufficient net R/R after costs or failed Walk-Forward validation. Conflicting timeframes never increase risk.
- Added `LearningValidationEngine`: requires at least two unseen folds, ten out-of-sample trades, reliable training samples, positive compounded test return, at least half profitable test folds, average drawdown <=10%, and no test fold with profit factor <=1.0.
- Long-horizon BUY validation requires at least 600 daily candles. The selected timeframe requires at least 220 closed candles.
- Final AI action is changed to WAIT whenever the decision gate rejects BUY or market-data integrity is not valid. Scanner results are labelled unverified watch candidates, not trade signals. Multi-timeframe card displays bias, not a final trade instruction.
- Spot SELL is only emitted when a Paper Spot holding exists. Paper SELL now supports 25%, 50% and 100% exits and rejects quantities above available holdings; no short positions can be opened.
- Added a five-minute refresh of the 12-timeframe context while Home/ALPON analysis screens are open.
- Created `backups/before-decision-intelligence-20261122.tar.gz` before final packaging; original source ZIP remains unchanged.
- Verification: all domain Kotlin files compiled with `kotlinc`; synthetic tests passed for complete/incomplete data, false-breakout rejection, weak Walk-Forward rejection, fee/slippage R/R rejection, invalid SL, timeframe conflict, invalid ticker and Spot SELL with/without a holding. `BinanceApi.kt` compiled against Retrofit API stubs.
- Limitation: full Android/Compose Gradle build and device/network runtime were not verified here because Gradle 8.9 is not installed and this environment cannot complete the Android dependency build. The APK must still be built and tested with Termux or GitHub Actions.


## 2026-11-23 — Separate Binance Spot and Forex markets

- Added a top-right `BINANCE SPOT` / `FOREX` chart switch; market data is namespaced in Room (`FX_` prefix) to prevent cross-market collisions.
- Added Twelve Data REST integration with a user-supplied local API key, `/forex_pairs` catalogue (up to 500 provider-returned pairs), and OHLC time-series requests.
- Forex requests 1,000 daily candles plus seven real timeframe feeds (1M, 1W, 1D, 4H, 2H, 1H, 15M). The Forex MTF engine requires at least 600 closed daily bars; incomplete/stale history yields WAIT. Weekend closures are handled separately from weekday stale-data checks.
- Added Forex search in Markets and Home; added Binance Spot USDT and USDC pair lists, up to 500 pairs for each quote asset.
- Forex is analysis-only in this build. No broker bid/ask spread, financing or execution-cost feed is connected, so directional Forex BUY/SELL proposals remain WAIT and Spot Paper Trading is disabled while Forex is selected. No synthetic volume is fabricated.
- Verification: all domain Kotlin sources compiled with `kotlinc`; synthetic Forex MTF/Data Integrity smoke test passed with 7 frames and 1,000 daily candles; Binance and Twelve Data API source files compiled against Retrofit interface stubs; MainActivity passed Kotlin parser syntax scan with no syntax diagnostics.
- Full Android Gradle/Compose build was attempted but could not start because this environment could not resolve `services.gradle.org` to download Gradle 8.9. APK build and live provider/device tests remain unverified; run `./gradlew :app:assembleDebug` in Termux or GitHub Actions after network access is available.

## Binance Futures market mode — 2026-11-23

- Added a third chart market selector: BINANCE FUTURES, alongside the existing BINANCE SPOT and FOREX selectors.
- Added USDⓈ-M and COIN-M modes using Binance public Futures REST endpoints (`fapi.binance.com` and `dapi.binance.com`).
- Futures Markets catalogue is dynamically loaded from each exchange's `exchangeInfo` plus 24-hour ticker data; it is not a hardcoded 500-symbol list.
- Futures chart loads up to 1,000 candles per selected interval and requests the existing 12-timeframe analysis context. Actual history length depends on contract launch date and exchange availability.
- Futures data is REST-refreshed; a Futures WebSocket feed, dedicated Futures risk model (margin, funding, open interest, liquidation), and Futures paper execution are not implemented in this update.
- Safety: AI action is forced to WAIT in Futures mode until the dedicated Futures execution gate is implemented and validated. No Futures order placement or account credentials were added.
- `BinanceFuturesApi.kt` compiled independently with Retrofit/model stubs; Kotlin parser check found no syntax diagnostics in `MainActivity.kt`.
- Full Android Gradle build not run: this uploaded project does not contain `gradle/wrapper/gradle-wrapper.jar` or wrapper properties, and no system Gradle executable is available in the environment.
