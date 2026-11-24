# Binance Futures market integration

This update adds a third market mode to the existing chart and Markets screen:

- Binance Spot (existing)
- Forex via the existing Twelve Data provider (existing)
- Binance Futures with USDⓈ-M and COIN-M contract catalogues and candle endpoints

## Data sources

- USDⓈ-M: `https://fapi.binance.com/` (`/fapi/v1/exchangeInfo`, `/fapi/v1/ticker/24hr`, `/fapi/v1/klines`)
- COIN-M: `https://dapi.binance.com/` (`/dapi/v1/exchangeInfo`, `/dapi/v1/ticker/24hr`, `/dapi/v1/klines`)
- USDⓈ-M derivatives overlay: premium index/mark price, open interest and history, global long/short account ratio, and taker buy/sell ratio.
- COIN-M derivatives overlay: premium index/mark price, open interest and pair-scoped history, pair-scoped global long/short account ratio, and contract-type-scoped taker buy/sell volume. COIN-M uses `pair` for statistics endpoints and `symbol` for contract-specific open interest/mark data.

The app loads the live contract catalogue from Exchange Information and filters to TRADING perpetual/current-quarter/next-quarter contracts. USDⓈ-M includes USDT and USDC quoted contracts. The list is not hardcoded or capped at 500.

The chart requests up to 1,000 candles for the selected interval, and the multi-timeframe analysis requests 12 Binance Futures intervals. Daily history can therefore cover more than one year when Binance has that many candles for the contract. Contract launch dates and exchange availability vary; a full year cannot be guaranteed for every symbol.

## Safety and current scope

- Futures mode is public market-data and chart/analysis only.
- No API keys, account endpoints, order placement, leverage changes, margin transfers, or live execution were added.
- The existing Spot Paper Trading engine is not reused as Futures execution.
- Funding, open-interest history, global long/short positioning, taker flow, and mark/index basis are now checked by a fail-closed derivatives overlay.
- Dedicated Futures position sizing, maintenance-margin tiers, exact liquidation-price modelling, and Futures Paper Trading remain future work. Until those are implemented and validated, the app keeps trade permission at WAIT even when an analytical bias is displayed.
- Futures live WebSocket streaming is not enabled in this update; candles and ticker refresh via REST.
