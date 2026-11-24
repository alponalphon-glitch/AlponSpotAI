# NEXORA Adaptive Risk & Money Management

This is an internal decision/execution-control layer. It is intentionally not a separate navigation item or user-facing strategy menu.

## Entry planning
- Uses ATR and nearby support to derive a variable protective stop; it does not impose a fixed 1% stop.
- Accepts only stop distances from 0.60% to 8.00%; otherwise the entry is blocked and the brain returns WAIT.
- Uses a volatility-adjusted account-risk budget: baseline 0.50% of paper equity, reduced toward 0.20% as volatility expands.
- Computes the maximum position notional from stop distance, estimated fees/slippage and a 25% capital-allocation ceiling.
- Uses a 2.2R take-profit reference. The paper executor rejects manually requested size above the risk-derived maximum.

## Position management
- The initial protective stop is stored with the paper position.
- At +1R, the stop ratchets to fee-buffered break-even and then follows the best observed price using current ATR.
- A long stop can only move upward, never loosen.
- A reached stop, target, or strong profitable opposing signal closes the simulated position.
- There is no forced 24/48-hour time exit: positions may remain open for one or two days while their protection remains active.

## Safety and scope
- Paper Trading only. No exchange order is submitted.
- Binance Futures remains analysis-only; this long-only manager is not a Futures/leverage risk model.
- Live updates currently arrive through the selected Spot symbol's WebSocket. To manage a position continuously, keep NEXORA running with that symbol selected and a live connection. If the app is closed, the phone is offline, or the symbol is not selected, the client cannot guarantee stop/target execution during that gap.
- Stops are not guaranteed fill prices; gaps, outages, spread, slippage and fees can worsen simulated/real outcomes.
