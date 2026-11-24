package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle
import kotlin.math.max
import kotlin.math.min

data class BacktestTrade(
    val entryTime: Long,
    val exitTime: Long,
    val entryPrice: Double,
    val exitPrice: Double,
    val quantity: Double,
    val pnl: Double,
    val pnlPercent: Double,
    val result: String,
    val exitReason: String
)

data class BacktestResult(
    val initialCapital: Double,
    val endingBalance: Double,
    val netProfit: Double,
    val totalReturnPercent: Double,
    val totalTrades: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val profitFactor: Double?,
    val maxDrawdownPercent: Double,
    val totalFees: Double,
    val signalsEvaluated: Int,
    val trades: List<BacktestTrade>
)

object BacktestingEngine {

    private data class Position(
        val entryTime: Long,
        val entryPrice: Double,
        val quantity: Double,
        val stop: Double,
        val target: Double
    )

    /** Keeps the same SMA-seeded EMA definition as TechnicalAnalysis, including the value 3 bars ago. */
    private class EmaTracker {
        private val periods = listOf(5, 6, 8, 9, 12, 13, 16, 18, 19, 20, 21, 24, 26, 34, 39, 40, 50, 60, 80, 100, 120, 150, 200)
        private val counts = periods.associateWith { 0 }.toMutableMap()
        private val seeds = periods.associateWith { 0.0 }.toMutableMap()
        private val values = periods.associateWith { Double.NaN }.toMutableMap()
        private val recent = periods.associateWith { java.util.ArrayDeque<Double>() }.toMutableMap()

        fun update(price: Double) {
            for (period in periods) {
                val count = counts.getValue(period) + 1
                counts[period] = count
                val next = when {
                    count < period -> {
                        seeds[period] = seeds.getValue(period) + price
                        Double.NaN
                    }
                    count == period -> {
                        seeds[period] = seeds.getValue(period) + price
                        seeds.getValue(period) / period
                    }
                    else -> {
                        val previous = values.getValue(period)
                        val k = 2.0 / (period + 1.0)
                        price * k + previous * (1.0 - k)
                    }
                }
                values[period] = next
                val queue = recent.getValue(period)
                queue.addLast(next)
                if (queue.size > 4) queue.removeFirst()
            }
        }

        fun current(): Map<Int, Double> = values.toMap()

        fun threeBarsAgo(): Map<Int, Double> = periods.associateWith { period ->
            recent.getValue(period).firstOrNull() ?: Double.NaN
        }
    }

    private fun validParameters(
        initialCapital: Double,
        riskPercent: Double,
        feeRate: Double,
        slippageRate: Double,
        rewardRisk: Double
    ): Boolean =
        initialCapital.isFinite() && initialCapital > 0.0 &&
        riskPercent.isFinite() && riskPercent > 0.0 && riskPercent <= 100.0 &&
        feeRate.isFinite() && feeRate in 0.0..0.01 &&
        slippageRate.isFinite() && slippageRate in 0.0..0.01 &&
        rewardRisk.isFinite() && rewardRisk in 1.0..5.0

    private fun validCandle(c: Candle): Boolean =
        c.open.isFinite() && c.high.isFinite() && c.low.isFinite() && c.close.isFinite() &&
            c.open > 0.0 && c.high >= c.low && c.low > 0.0 && c.close > 0.0

    /**
     * Compatibility entry point for callers that already have candles in memory.
     * The same streaming calculation is used after sorting and filtering the input.
     */
    fun run(
        candles: List<Candle>,
        interval: String = "1h",
        initialCapital: Double = 1000.0,
        riskPercent: Double = 0.5,
        feeRate: Double = 0.001,
        slippageRate: Double = 0.0005,
        rewardRisk: Double = 2.0
    ): BacktestResult? {
        if (!validParameters(initialCapital, riskPercent, feeRate, slippageRate, rewardRisk)) return null
        val runner = Runner(interval, initialCapital, riskPercent, feeRate, slippageRate, rewardRisk)
        candles.asSequence().sortedBy { it.time }.filter(::validCandle).forEach(runner::accept)
        return runner.finish()
    }

    /**
     * Reads history page-by-page. Only the rolling analysis window and resulting trades
     * are retained in memory; the full candle history is never accumulated.
     * loadPage must return candles ordered by open time (ascending).
     */
    suspend fun runFromPages(
        firstTime: Long,
        interval: String = "1h",
        initialCapital: Double = 1000.0,
        riskPercent: Double = 0.5,
        feeRate: Double = 0.001,
        slippageRate: Double = 0.0005,
        rewardRisk: Double = 2.0,
        tradeAfterTime: Long = firstTime,
        loadPage: suspend (afterTime: Long, limit: Int) -> List<Candle>
    ): BacktestResult? {
        if (!validParameters(initialCapital, riskPercent, feeRate, slippageRate, rewardRisk)) return null

        val runner = Runner(interval, initialCapital, riskPercent, feeRate, slippageRate, rewardRisk, tradeAfterTime)
        var cursor = firstTime - 1L

        while (true) {
            val page = loadPage(cursor, 1000)
            if (page.isEmpty()) break
            val ordered = page.sortedBy { it.time }
            for (candle in ordered) runner.accept(candle)

            val nextCursor = ordered.last().time
            if (nextCursor <= cursor) {
                throw IllegalStateException("Saved history pagination did not advance.")
            }
            cursor = nextCursor
            if (ordered.size < 1000) break
        }

        return runner.finish()
    }

    private class Runner(
        private val interval: String,
        initialCapital: Double,
        private val riskPercent: Double,
        private val feeRate: Double,
        private val slippageRate: Double,
        private val rewardRisk: Double,
        private val tradeAfterTime: Long = Long.MIN_VALUE
    ) {
        private var balance = initialCapital
        private var peakBalance = initialCapital
        private var maxDrawdown = 0.0
        private var totalFees = 0.0
        private var grossProfit = 0.0
        private var grossLoss = 0.0
        private var signalsEvaluated = 0
        private val trades = mutableListOf<BacktestTrade>()
        private val lossGuard = CapitalProtectionEngine.LossGuard()
        private var position: Position? = null
        private val emaTracker = EmaTracker()
        private val window = java.util.ArrayDeque<Candle>()
        private var lastCandle: Candle? = null
        private var lastTime: Long? = null
        private var validCount = 0

        private fun closePosition(pos: Position, rawExit: Double, exitTime: Long, reason: String) {
            val exitPrice = rawExit * (1.0 - slippageRate)
            val fees = (pos.entryPrice + exitPrice) * pos.quantity * feeRate
            val pnl = (exitPrice - pos.entryPrice) * pos.quantity - fees
            val invested = pos.entryPrice * pos.quantity

            balance += pnl
            totalFees += fees
            if (pnl > 0.0) grossProfit += pnl
            if (pnl < 0.0) grossLoss += -pnl

            peakBalance = max(peakBalance, balance)
            val drawdown = if (peakBalance > 0.0) (peakBalance - balance) / peakBalance * 100.0 else 0.0
            maxDrawdown = max(maxDrawdown, drawdown)
            lossGuard.recordClosedTrade(pnl, balance, exitTime)

            trades += BacktestTrade(
                pos.entryTime, exitTime, pos.entryPrice, exitPrice, pos.quantity, pnl,
                if (invested > 0.0) pnl / invested * 100.0 else 0.0,
                when {
                    pnl > 0.0 -> "WIN"
                    pnl < 0.0 -> "LOSS"
                    else -> "BREAKEVEN"
                },
                reason
            )
        }

        /** Called in chronological order; memory use is bounded to 211 candles. */
        fun accept(next: Candle) {
            if (!validCandle(next)) return
            val previousTime = lastTime
            if (previousTime != null && next.time < previousTime) {
                throw IllegalArgumentException("Backtest candles must be ordered by open time.")
            }

            // The previous candle is the analysis point; this arriving candle is its next bar.
            if (window.size >= 210 && next.time >= tradeAfterTime) {
                val history = window.toList()
                val signal = TechnicalAnalysis.analyze(
                    history + history.last(),
                    interval,
                    emaTracker.current(),
                    emaTracker.threeBarsAgo()
                )

                if (signal != null) {
                    signalsEvaluated++
                    val currentPosition = position
                    if (currentPosition != null) {
                        val exit = when {
                            next.open <= currentPosition.stop -> next.open to "STOP_LOSS_GAP"
                            next.open >= currentPosition.target -> next.open to "TAKE_PROFIT_GAP"
                            signal.action == "SELL" -> next.open to "SELL_SIGNAL"
                            next.low <= currentPosition.stop -> currentPosition.stop to "STOP_LOSS"
                            next.high >= currentPosition.target -> currentPosition.target to "TAKE_PROFIT"
                            else -> null
                        }
                        if (exit != null) {
                            closePosition(currentPosition, exit.first, next.time, exit.second)
                            position = null
                        }
                    } else if (signal.action == "BUY" && lossGuard.canOpenNewPosition(balance, next.time).first) {
                        val distance = signal.entry - signal.stop
                        if (distance.isFinite() && distance > 0.0) {
                            val entryPrice = next.open * (1.0 + slippageRate)
                            val riskCash = balance * riskPercent / 100.0
                            val riskQuantity = riskCash / distance
                            val capitalQuantity = balance / (entryPrice * (1.0 + feeRate))
                            val quantity = min(riskQuantity, capitalQuantity)

                            if (quantity.isFinite() && quantity > 0.0) {
                                val newPosition = Position(
                                    next.time, entryPrice, quantity,
                                    entryPrice - distance,
                                    entryPrice + distance * rewardRisk
                                )
                                position = newPosition
                                val exit = when {
                                    next.low <= newPosition.stop -> newPosition.stop to "STOP_LOSS"
                                    next.high >= newPosition.target -> newPosition.target to "TAKE_PROFIT"
                                    else -> null
                                }
                                if (exit != null) {
                                    closePosition(newPosition, exit.first, next.time, exit.second)
                                    position = null
                                }
                            }
                        }
                    }
                }
            }

            // Update EMA only after using the prior candle's EMA state for its signal.
            emaTracker.update(next.close)
            window.addLast(next)
            if (window.size > 211) window.removeFirst()
            // Mark-to-market drawdown includes an open Spot position, not only closed trades.
            val openPosition = position
            val markEquity = balance + (openPosition?.let {
                val estimatedExit = next.close * (1.0 - slippageRate)
                (estimatedExit - it.entryPrice) * it.quantity - estimatedExit * it.quantity * feeRate
            } ?: 0.0)
            peakBalance = max(peakBalance, markEquity)
            val markDrawdown = if (peakBalance > 0.0) (peakBalance - markEquity) / peakBalance * 100.0 else 0.0
            maxDrawdown = max(maxDrawdown, markDrawdown)

            lastCandle = next
            lastTime = next.time
            validCount++
        }

        fun finish(): BacktestResult? {
            val finalCandle = lastCandle ?: return null
            if (validCount < 211) return null
            position?.let {
                closePosition(it, finalCandle.close, finalCandle.closeTime, "END_OF_DATA")
                position = null
            }

            val wins = trades.count { it.pnl > 0.0 }
            val losses = trades.count { it.pnl < 0.0 }
            val initialCapital = initialBalance
            val actualNetProfit = balance - initialCapital

            return BacktestResult(
                initialCapital,
                balance,
                actualNetProfit,
                actualNetProfit / initialCapital * 100.0,
                trades.size,
                wins,
                losses,
                if (trades.isNotEmpty()) wins.toDouble() / trades.size * 100.0 else 0.0,
                if (grossLoss > 0.0) grossProfit / grossLoss else if (grossProfit > 0.0) null else 0.0,
                maxDrawdown,
                totalFees,
                signalsEvaluated,
                trades.toList()
            )
        }

        private val initialBalance: Double = initialCapital
    }
}
