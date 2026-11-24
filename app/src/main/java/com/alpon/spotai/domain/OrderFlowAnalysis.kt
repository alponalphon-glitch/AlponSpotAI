package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle

data class OrderFlowReport(
    val buyPressure: Double,
    val sellPressure: Double,
    val takerBuySellRatio: Double,
    val cvd: Double,
    val cvdPercent: Double,
    val relativeVolume: Double,
    val volumeSpike: Boolean,
    val moneyFlowImbalance: Double,
    val tradeActivityStrength: Double,
    val bias: Int
)

object OrderFlowAnalysis {

    fun analyze(input: List<Candle>, lookback: Int = 50): OrderFlowReport? {
        val now = System.currentTimeMillis()

        val candles = input
            .filter { it.closeTime == 0L || it.closeTime <= now }
            .takeLast(lookback.coerceAtLeast(21))

        if (candles.size < 21) return null

        val latest = candles.last()
        val previous = candles.dropLast(1)

        val totalVolume = candles.sumOf { it.volume }
        if (totalVolume <= 0.0) return null

        val buyVolume = candles.sumOf {
            it.takerBuyBaseVolume.coerceIn(0.0, it.volume.coerceAtLeast(0.0))
        }
        val sellVolume = (totalVolume - buyVolume).coerceAtLeast(0.0)

        val buyPressure = buyVolume / totalVolume * 100.0
        val sellPressure = sellVolume / totalVolume * 100.0

        val takerRatio = if (sellVolume > 0.0) buyVolume / sellVolume else 0.0

        val deltas = candles.map {
            2.0 * it.takerBuyBaseVolume.coerceIn(0.0, it.volume.coerceAtLeast(0.0)) - it.volume
        }
        val cvd = deltas.sum()
        val cvdPercent = if (totalVolume > 0.0) cvd / totalVolume * 100.0 else 0.0

        val averageVolume = previous.takeLast(20).map { it.volume }.average()
        val relativeVolume = if (averageVolume > 0.0) latest.volume / averageVolume else 0.0
        val volumeSpike = relativeVolume >= 2.0

        val quoteTotal = candles.sumOf { it.quoteVolume }
        val quoteBuy = candles.sumOf { it.takerBuyQuoteVolume }
        val moneyFlowImbalance = if (quoteTotal > 0.0) {
            ((2.0 * quoteBuy - quoteTotal) / quoteTotal) * 100.0
        } else {
            cvdPercent
        }

        val averageTrades = previous.takeLast(20).map { it.tradeCount.toDouble() }.average()
        val tradeActivity = if (averageTrades > 0.0) {
            latest.tradeCount / averageTrades
        } else {
            0.0
        }

        val bias = when {
            cvdPercent >= 8.0 && buyPressure >= 53.0 -> 1
            cvdPercent <= -8.0 && sellPressure >= 53.0 -> -1
            else -> 0
        }

        return OrderFlowReport(
            buyPressure = buyPressure,
            sellPressure = sellPressure,
            takerBuySellRatio = takerRatio,
            cvd = cvd,
            cvdPercent = cvdPercent,
            relativeVolume = relativeVolume,
            volumeSpike = volumeSpike,
            moneyFlowImbalance = moneyFlowImbalance,
            tradeActivityStrength = tradeActivity,
            bias = bias
        )
    }
}
