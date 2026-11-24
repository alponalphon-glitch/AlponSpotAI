# Historical Downside Scenario Risk Intelligence

## Added in this build

The Decision Intelligence screen now includes a downside stress review for proposed Spot BUY setups.

- Uses confirmed, closed OHLC candles only.
- Measures the forward adverse excursion over 10-, 20-, and 50-candle horizons.
- Reports the empirical 95th-percentile adverse excursion and worst observed excursion for each horizon.
- Adds a buffer equal to half the largest observed one-candle adverse move.
- Compares the proposed entry-to-stop distance with the resulting historical stress reference.
- Blocks a new BUY when history is insufficient, the stop is invalid, or the stop is tighter than the historical stress reference.

## Interpretation and limitations

These are historical stress measurements, not calibrated probabilities, forecasts, or guarantees. The test uses candle lows and cannot model order-book depth, intrabar path, spread changes, exchange outages, liquidation mechanics, or stop execution slippage exactly. A stop does not guarantee a maximum loss; gaps and fast markets can exceed it.

This is a conservative additional Paper Trading decision gate. It does not send live orders, increase position size, or train a neural network. Full Android build and device runtime verification are still required.
