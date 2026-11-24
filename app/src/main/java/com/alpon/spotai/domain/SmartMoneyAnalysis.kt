package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Rule-based SMC context derived only from confirmed, closed Binance candles. */
data class OrderBlockZone(
    val direction: String,
    val low: Double,
    val high: Double,
    val originTime: Long,
    val strength: Int,
    val mitigated: Boolean
)

data class LiquidityPool(
    val type: String,
    val price: Double,
    val touches: Int,
    val swept: Boolean
)

data class SmartMoneyReport(
    val orderBlocks: List<OrderBlockZone>,
    val liquidityPools: List<LiquidityPool>,
    val breakoutQuality: String,
    val bias: String,
    val score: Double,
    val explanation: String
)

object SmartMoneyAnalysis {
    fun analyze(input: List<Candle>, structure: MarketStructureReport? = null): SmartMoneyReport? {
        val now = System.currentTimeMillis()
        val candles = input.asSequence()
            .filter { it.closeTime == 0L || it.closeTime <= now }
            .filter { it.open.isFinite() && it.high.isFinite() && it.low.isFinite() && it.close.isFinite() && it.volume.isFinite() }
            .filter { it.low > 0.0 && it.high >= it.low && it.volume >= 0.0 }
            .sortedBy { it.time }.toList().takeLast(500)
        if (candles.size < 60) return null

        val tr = candles.zipWithNext { a, b ->
            max(b.high - b.low, max(abs(b.high - a.close), abs(b.low - a.close)))
        }
        val atr = tr.takeLast(14).average().takeIf { it.isFinite() && it > 0.0 } ?: return null
        val averageVolume = candles.takeLast(21).dropLast(1).map { it.volume }.average().coerceAtLeast(1e-12)
        val last = candles.last()
        val pivots = structure?.swingPoints ?: findPivots(candles, 3)

        // Equal highs/lows: cluster independent confirmed pivots within 0.20 ATR.
        val pools = mutableListOf<LiquidityPool>()
        listOf("HIGH" to pivots.filter { it.type == "HIGH" }, "LOW" to pivots.filter { it.type == "LOW" }).forEach { (type, points) ->
            val groups = mutableListOf<MutableList<SwingPoint>>()
            points.takeLast(40).sortedBy { it.price }.forEach { p ->
                val group = groups.lastOrNull()
                if (group != null && abs(group.map { it.price }.average() - p.price) <= atr * 0.20) group.add(p)
                else groups.add(mutableListOf(p))
            }
            groups.filter { it.size >= 2 }.forEach { group ->
                val level = group.map { it.price }.average()
                val swept = if (type == "HIGH") last.high > level && last.close < level else last.low < level && last.close > level
                pools += LiquidityPool(if (type == "HIGH") "BUY_SIDE" else "SELL_SIDE", level, group.size, swept)
            }
        }

        // Order block candidate: last opposite candle before a >= 1.5 ATR displacement.
        val blocks = mutableListOf<OrderBlockZone>()
        val start = (candles.size - 80).coerceAtLeast(2)
        for (i in start until candles.lastIndex) {
            val origin = candles[i]
            val next = candles.getOrNull(i + 1) ?: continue
            val bullishImpulse = next.close > origin.high && next.close - origin.high >= atr * 0.5 && next.close > next.open
            val bearishImpulse = next.close < origin.low && origin.low - next.close >= atr * 0.5 && next.close < next.open
            if (bullishImpulse && origin.close < origin.open) {
                val invalidated = candles.drop(i + 2).any { it.close < origin.low }
                if (!invalidated) blocks += OrderBlockZone("BULLISH", origin.low, max(origin.open, origin.close), origin.time, if (next.volume >= averageVolume * 1.5) 85 else 65, last.low <= max(origin.open, origin.close))
            } else if (bearishImpulse && origin.close > origin.open) {
                val invalidated = candles.drop(i + 2).any { it.close > origin.high }
                if (!invalidated) blocks += OrderBlockZone("BEARISH", min(origin.open, origin.close), origin.high, origin.time, if (next.volume >= averageVolume * 1.5) 85 else 65, last.high >= min(origin.open, origin.close))
            }
        }
        val activeBlocks = blocks.filterNot { it.mitigated }.takeLast(8)

        val nearestHigh = pivots.lastOrNull { it.type == "HIGH" && it.time < last.time }
        val nearestLow = pivots.lastOrNull { it.type == "LOW" && it.time < last.time }
        val volumeRatio = last.volume / averageVolume
        val breakout = when {
            nearestHigh != null && last.close > nearestHigh.price && volumeRatio >= 1.25 -> "CONFIRMED_BULLISH_BREAKOUT"
            nearestHigh != null && last.high > nearestHigh.price && last.close <= nearestHigh.price -> "POSSIBLE_BULLISH_FALSE_BREAKOUT"
            nearestLow != null && last.close < nearestLow.price && volumeRatio >= 1.25 -> "CONFIRMED_BEARISH_BREAKOUT"
            nearestLow != null && last.low < nearestLow.price && last.close >= nearestLow.price -> "POSSIBLE_BEARISH_FALSE_BREAKOUT"
            else -> "NO_CONFIRMED_BREAKOUT"
        }
        var score = 0.0
        if (activeBlocks.any { it.direction == "BULLISH" && last.close in it.low..it.high }) score += 30.0
        if (activeBlocks.any { it.direction == "BEARISH" && last.close in it.low..it.high }) score -= 30.0
        if (pools.any { it.type == "SELL_SIDE" && it.swept }) score += 25.0
        if (pools.any { it.type == "BUY_SIDE" && it.swept }) score -= 25.0
        score += when (breakout) { "CONFIRMED_BULLISH_BREAKOUT" -> 35.0; "CONFIRMED_BEARISH_BREAKOUT" -> -35.0; "POSSIBLE_BULLISH_FALSE_BREAKOUT" -> -20.0; "POSSIBLE_BEARISH_FALSE_BREAKOUT" -> 20.0; else -> 0.0 }
        score = score.coerceIn(-100.0, 100.0)
        val bias = when { score >= 20.0 -> "BULLISH"; score <= -20.0 -> "BEARISH"; else -> "NEUTRAL" }
        val explanation = "${activeBlocks.size} active order blocks; ${pools.size} equal-high/low liquidity pools; breakout: $breakout; volume ${"%.2f".format(volumeRatio)}x. Zones are hypotheses, not guaranteed institutional orders."
        return SmartMoneyReport(activeBlocks, pools.sortedBy { abs(it.price - last.close) }.take(10), breakout, bias, score, explanation)
    }

    private fun findPivots(c: List<Candle>, radius: Int): List<SwingPoint> {
        val out = mutableListOf<SwingPoint>()
        for (i in radius until c.size - radius) {
            val x = c[i]
            if ((i-radius until i).all { x.high > c[it].high } && (i+1..i+radius).all { x.high >= c[it].high }) out += SwingPoint(i, x.time, x.high, "HIGH")
            if ((i-radius until i).all { x.low < c[it].low } && (i+1..i+radius).all { x.low <= c[it].low }) out += SwingPoint(i, x.time, x.low, "LOW")
        }
        return out
    }
}
