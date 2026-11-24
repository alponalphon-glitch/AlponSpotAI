package com.alpon.spotai.domain

/** Independent virtual Spot and Futures ledgers. This class never calls exchange order endpoints. */
object DualPaperPortfolioEngine {
    enum class Account { SPOT, FUTURES }
    enum class Side { LONG, SHORT }

    data class Position(
        val symbol: String,
        val side: Side,
        val quantity: Double,
        val entryPrice: Double,
        val entryFee: Double,
        val openedAt: Long,
        val stopLoss: Double? = null,
        val takeProfit: Double? = null,
        val leverage: Int = 1
    )

    data class Ledger(
        val account: Account,
        val startingCash: Double = 5_000.0,
        val cash: Double = 5_000.0,
        val positions: List<Position> = emptyList(),
        val closedPnl: Double = 0.0,
        val feesPaid: Double = 0.0,
        val halted: Boolean = false,
        val haltReason: String? = null
    )

    data class TradeResult(val ledger: Ledger, val message: String, val accepted: Boolean)

    const val FEE_RATE = 0.001       // 0.10% each side
    const val SLIPPAGE_RATE = 0.0005 // 0.05% adverse simulation
    const val MAX_POSITION_EQUITY = 0.25
    const val DAILY_LOSS_STOP = 0.02

    fun newSpot(): Ledger = Ledger(account = Account.SPOT)
    fun newFutures(): Ledger = Ledger(account = Account.FUTURES)



    fun equity(ledger: Ledger, prices: Map<String, Double>): Double {
        val marked = ledger.positions.sumOf { p ->
            val price = prices[p.symbol]?.takeIf { it.isFinite() && it > 0.0 } ?: p.entryPrice
            when (ledger.account) {
                Account.SPOT -> p.quantity * price
                Account.FUTURES -> p.quantity * (if (p.side == Side.LONG) price - p.entryPrice else p.entryPrice - price) + (p.quantity * p.entryPrice / p.leverage)
            }
        }
        return ledger.cash + marked
    }

    /** Spot is long-only; Futures supports long/short but defaults to 1x simulation leverage. */
    fun open(ledger: Ledger, symbol: String, side: Side, price: Double, notional: Double, now: Long,
             stopLoss: Double? = null, takeProfit: Double? = null, leverage: Int = 1): TradeResult {
        if (ledger.halted) return TradeResult(ledger, "Account halted: ${ledger.haltReason ?: "risk lock"}", false)
        if (symbol.isBlank() || !price.isFinite() || price <= 0 || !notional.isFinite() || notional <= 0)
            return TradeResult(ledger, "Invalid symbol, price or size.", false)
        if (ledger.account == Account.SPOT && side != Side.LONG)
            return TradeResult(ledger, "Spot account does not open short positions.", false)
        if (leverage !in 1..1) return TradeResult(ledger, "Paper Futures leverage is fixed at 1x for this phase.", false)
        if (ledger.positions.any { it.symbol == symbol }) return TradeResult(ledger, "Position for $symbol already exists.", false)
        val adversePrice = price * (1.0 + SLIPPAGE_RATE)
        val quantity = notional / adversePrice
        val actualEntryValue = quantity * adversePrice
        val fee = actualEntryValue * FEE_RATE
        val requiredCash = when (ledger.account) {
            Account.SPOT -> actualEntryValue + fee
            Account.FUTURES -> actualEntryValue / leverage + fee
        }
        val accountEquity = ledger.cash + ledger.positions.sumOf { it.quantity * it.entryPrice / it.leverage }
        if (notional > accountEquity * MAX_POSITION_EQUITY + 1e-9)
            return TradeResult(ledger, "Risk limit: position size exceeds 25% of account equity.", false)
        if (requiredCash > ledger.cash || !quantity.isFinite() || quantity <= 0)
            return TradeResult(ledger, "Insufficient paper cash for size and fees.", false)
        val pos = Position(symbol, side, quantity, adversePrice, fee, now, stopLoss, takeProfit, leverage)
        val updated = ledger.copy(cash = ledger.cash - requiredCash, positions = ledger.positions + pos, feesPaid = ledger.feesPaid + fee)
        return TradeResult(updated, "Paper ${side.name} opened for $symbol at simulated $adversePrice.", true)
    }

    fun close(ledger: Ledger, symbol: String, price: Double, now: Long): TradeResult {
        val pos = ledger.positions.firstOrNull { it.symbol == symbol }
            ?: return TradeResult(ledger, "No open virtual position for $symbol.", false)
        if (!price.isFinite() || price <= 0) return TradeResult(ledger, "Invalid exit price.", false)
        val exitPrice = price * (1.0 - SLIPPAGE_RATE)
        val gross = pos.quantity * exitPrice
        val fee = gross * FEE_RATE
        val pnl = when (pos.side) {
            Side.LONG -> (exitPrice - pos.entryPrice) * pos.quantity
            Side.SHORT -> (pos.entryPrice - exitPrice) * pos.quantity
        } - pos.entryFee - fee
        val cashCredit = when (ledger.account) {
            Account.SPOT -> gross - fee
            Account.FUTURES -> (pos.quantity * pos.entryPrice / pos.leverage) + (if (pos.side == Side.LONG) exitPrice - pos.entryPrice else pos.entryPrice - exitPrice) * pos.quantity - fee
        }
        if (!cashCredit.isFinite() || !pnl.isFinite()) return TradeResult(ledger, "Exit calculation invalid; trade not closed.", false)
        val updated = ledger.copy(
            cash = ledger.cash + cashCredit,
            positions = ledger.positions.filterNot { it.symbol == symbol },
            closedPnl = ledger.closedPnl + pnl,
            feesPaid = ledger.feesPaid + fee
        )
        return TradeResult(updated, "Paper exit $symbol • realized PnL ${"%.2f".format(pnl)} • time $now", true)
    }

    fun halt(ledger: Ledger, reason: String): Ledger = ledger.copy(halted = true, haltReason = reason)
    fun resume(ledger: Ledger): Ledger = ledger.copy(halted = false, haltReason = null)
}
