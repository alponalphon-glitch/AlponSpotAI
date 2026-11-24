# NEXORA Long-History Analysis

## Change
The historical-memory analysis loader now reads up to 250,000 locally stored candles for the selected symbol, market key, and timeframe, instead of only the latest 10,000. The list is ordered chronologically before it is passed to `HistoricalMemoryWaitingEngine`, which scans earlier windows and compares them with the current pattern.

## Data acquisition and persistence
- Spot full-history pagination uses Binance `startTime` pages of up to 1,000 candles and persists each page in Room.
- Futures full-history pagination uses Binance Futures kline pages of up to 1,500 candles and persists each page in Room.
- Existing sync state and candle records are retained; this change does not delete or migrate AI files or alter the AI engines.
- The app's existing full-history download action must be run for each selected symbol/timeframe to populate older history. The analysis reads only data already downloaded and stored locally.

## Boundaries
- Analysis reads at most 250,000 stored candles per selected symbol/timeframe in one pass to avoid unbounded Android memory use. Therefore it is not an unlimited guarantee that every 1-minute candle from every year is processed. Coverage depends on timeframe, symbol listing date, Binance data availability, and successful full-history download.
- The historical similarity engine is a historical-pattern comparison, not a guaranteed forecast. It does not train a large language model or guarantee profit.
- No live orders or execution behavior was changed.
