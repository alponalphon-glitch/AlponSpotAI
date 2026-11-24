# ALPON Red-Team Adversarial Validation

The Red-Team layer challenges a proposed BUY instead of adding another bullish vote. It is deterministic rule-based validation, not a trained model or a probability forecast.

## Checks
- Requires at least 30 confirmed closed candles for the adversarial candle review.
- Flags abnormal candle-range expansion, strong upper-wick rejection, high-volume weak bearish closes, and rejected gap-ups.
- Detects a short-term momentum reversal against the broader recent trend as a regime-transition warning.
- Applies a 10-point adverse directional-score shock; a BUY that falls below the existing minimum score threshold is considered fragile.
- Recalculates net reward/risk with 2x configured fee and slippage assumptions plus an adverse entry displacement of 0.10 ATR.
- A BUY is blocked when any counter-evidence is found or stressed net reward/risk cannot be verified / falls below the configured minimum.

## Scope and limitations
- This is an additional conservative gate for new BUY proposals. It does not block protective SELL exits.
- Candle-volume checks use Binance kline data, not full order-book depth or trade-by-trade tape.
- The stress assumptions are scenarios, not a prediction of actual execution costs.
- Passing this layer does not guarantee profit and does not replace out-of-sample Walk-Forward validation.
