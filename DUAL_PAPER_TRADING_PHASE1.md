# Dual Paper Trading — Phase 1

This phase adds an isolated simulation ledger engine for two virtual accounts:
- Binance Spot: 5,000 USDT, long-only
- Binance Futures: 5,000 USDT, long/short paper simulation at fixed 1x leverage

Simulation assumptions: 0.10% fee per side and 0.05% adverse slippage per side. A single position per symbol per virtual account is allowed, with a 25% account-equity notional cap per position. The engine supports simulated open/close, PnL, fees, and account halt/resume state.

**Safety boundary:** this is paper-only. It does not call exchange order endpoints and does not place real orders. Phase 1 adds the engine module but does not yet connect an autonomous market scanner, periodic execution loop, persistence, or a UI account dashboard. Do not treat it as ready for unattended paper trading until those integrations and build/device checks are completed.
