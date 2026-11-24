package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle
import kotlin.math.abs
import kotlin.math.max

data class TimeframeReading(
    val label: String,
    val direction: String,
    val score: Int,
    val changePercent: Double,
    val emaFast: Double,
    val emaSlow: Double,
    val rsi: Double,
    val candles: Int,
    val lastCloseTime: Long = 0L
)

data class MultiTimeframeSignal(
    val action: String,
    val score: Int,
    val trend: String,
    val holdingWindow: String,
    val entry: Double,
    val stop: Double,
    val tp1: Double,
    val tp2: Double,
    val tp3: Double,
    val alignment: String,
    val explanation: String,
    val readings: List<TimeframeReading>
)

object MultiTimeframeAnalysis {
    private fun ema(values: List<Double>, period: Int): Double {
        if (values.size < period) return Double.NaN
        val k = 2.0 / (period + 1.0)
        var result = values.take(period).average()
        for (value in values.drop(period)) result = value * k + result * (1.0 - k)
        return result
    }

    private fun intervalMillis(label: String): Long? {
        val value = label.dropLast(1).toLongOrNull() ?: return null
        val unit = label.lastOrNull() ?: return null
        return when {
            unit == 'M' && value == 1L -> value * 30L * 86_400_000L
            unit.lowercaseChar() == 'm' -> value * 60_000L
            unit.lowercaseChar() == 'h' -> value * 3_600_000L
            unit.lowercaseChar() == 'd' -> value * 86_400_000L
            unit.lowercaseChar() == 'w' -> value * 7L * 86_400_000L
            else -> null
        }
    }

    private fun analyzeFrame(label: String, input: List<Candle>, maxBars: Int, minimum: Int = 25): TimeframeReading? {
        val now = System.currentTimeMillis()
        if (input.any { it.closeTime <= 0L }) return null
        val closedInput = input.filter { it.closeTime <= now }
        val ordered = closedInput.sortedBy { it.time }
        if (ordered != closedInput || ordered.zipWithNext().any { it.first.time >= it.second.time }) return null
        if (ordered.any {
                !it.open.isFinite() || !it.high.isFinite() || !it.low.isFinite() || !it.close.isFinite() || !it.volume.isFinite() ||
                    it.open <= 0.0 || it.close <= 0.0 || it.high < maxOf(it.open, it.close) || it.low > minOf(it.open, it.close) ||
                    it.low <= 0.0 || it.volume < 0.0 || !it.quoteVolume.isFinite() || it.quoteVolume < 0.0 ||
                    it.tradeCount < 0L || !it.takerBuyBaseVolume.isFinite() || it.takerBuyBaseVolume < 0.0 ||
                    it.takerBuyBaseVolume > it.volume + 1e-9 || !it.takerBuyQuoteVolume.isFinite() ||
                    it.takerBuyQuoteVolume < 0.0 || it.takerBuyQuoteVolume > it.quoteVolume + 1e-6
            }) return null
        val frameDuration = intervalMillis(label)
        if (frameDuration != null && label != "1M" && ordered.takeLast(100).zipWithNext().any { (a, b) -> b.time - a.time > frameDuration * 1.5 }) return null
        val lastClose = ordered.lastOrNull()?.closeTime ?: return null
        if (frameDuration != null && now - lastClose > frameDuration * 2.5) return null
        val candles = ordered.takeLast(maxBars)
        if (candles.size < minimum) return null
        val closes = candles.map { it.close }
        val price = closes.last()
        if (!price.isFinite() || price <= 0.0) return null
        val fast = ema(closes, minOf(9, closes.size))
        val slow = ema(closes, minOf(20, closes.size))
        val change = (price / closes.first() - 1.0) * 100.0
        val changes = closes.zipWithNext { a, b -> b - a }.takeLast(14)
        val gains = changes.sumOf { max(it, 0.0) } / changes.size.coerceAtLeast(1)
        val losses = changes.sumOf { max(-it, 0.0) } / changes.size.coerceAtLeast(1)
        val rsi = if (losses == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + gains / losses)
        var points = 0
        if (fast > slow) points += 2 else if (fast < slow) points -= 2
        if (price > slow) points++ else if (price < slow) points--
        if (change > 0.0) points++ else if (change < 0.0) points--
        if (rsi in 55.0..75.0) points++
        if (rsi < 45.0) points--
        val score = (50 + points * 7).coerceIn(0, 100)
        val direction = when { score >= 60 -> "BULLISH"; score <= 40 -> "BEARISH"; else -> "NEUTRAL" }
        return TimeframeReading(label, direction, score, change, fast, slow, rsi, candles.size, candles.lastOrNull()?.closeTime ?: 0L)
    }

    /** Uses Binance-native intervals from monthly down to five minutes. */
    fun analyze(
        oneMonth: List<Candle>, oneWeek: List<Candle>, threeDay: List<Candle>, oneDay: List<Candle>,
        twelveHour: List<Candle>, eightHour: List<Candle>, fourHour: List<Candle>, twoHour: List<Candle>,
        oneHour: List<Candle>, thirtyMin: List<Candle>, fifteenMin: List<Candle>, fiveMin: List<Candle>
    ): MultiTimeframeSignal? {
        val frames = listOfNotNull(
            analyzeFrame("1M", oneMonth, 36, 12),
            analyzeFrame("1W", oneWeek, 104, 25),
            analyzeFrame("3D", threeDay, 120, 25),
            analyzeFrame("1D", oneDay, 365, 25),
            analyzeFrame("12H", twelveHour, 120, 25),
            analyzeFrame("8H", eightHour, 120, 25),
            analyzeFrame("4H", fourHour, 120, 25),
            analyzeFrame("2H", twoHour, 120, 25),
            analyzeFrame("1H", oneHour, 120, 25),
            analyzeFrame("30M", thirtyMin, 120, 25),
            analyzeFrame("15M", fifteenMin, 120, 25),
            analyzeFrame("5M", fiveMin, 120, 25)
        )
        // All 12 Binance-native frames are required. Missing or stale source data means no MTF signal.
        if (frames.size != 12) return null
        val weights = mapOf("1M" to .18, "1W" to .16, "3D" to .12, "1D" to .12, "12H" to .10, "8H" to .08, "4H" to .07, "2H" to .06, "1H" to .05, "30M" to .03, "15M" to .02, "5M" to .01)
        val totalWeight = frames.sumOf { weights[it.label] ?: 0.0 }
        val score = frames.sumOf { it.score * (weights[it.label] ?: 0.0) } / totalWeight
        val longFrames = frames.filter { it.label in setOf("1M", "1W", "3D", "1D") }
        val shortFrames = frames.filter { it.label in setOf("4H", "2H", "1H", "30M", "15M", "5M") }
        val longBull = longFrames.count { it.direction == "BULLISH" }
        val longBear = longFrames.count { it.direction == "BEARISH" }
        val shortBull = shortFrames.count { it.direction == "BULLISH" }
        val shortBear = shortFrames.count { it.direction == "BEARISH" }
        val macroBear = longBear >= 3
        val macroBull = longBull >= 3
        val action = when {
            score >= 62.0 && shortBull >= 3 && !macroBear -> "BUY"
            score <= 38.0 && shortBear >= 3 && !macroBull -> "SELL"
            else -> "WAIT"
        }
        val recent = fifteenMin.sortedBy { it.time }.let { ordered ->
            if (ordered.lastOrNull()?.closeTime?.let { it > System.currentTimeMillis() } == true) ordered.dropLast(1) else ordered
        }.takeLast(15)
        val price = recent.lastOrNull()?.close?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val atr = if (recent.size >= 2) recent.mapIndexed { index, candle ->
            val previous = recent[(index - 1).coerceAtLeast(0)].close
            max(candle.high - candle.low, max(abs(candle.high - previous), abs(candle.low - previous)))
        }.average() else price * .005
        val distance = max(atr * 2.0, price * .003)
        val stop = when (action) { "BUY" -> price - distance; "SELL" -> price + distance; else -> 0.0 }
        val risk = if (stop > 0.0) abs(price - stop) else 0.0
        val tp1 = when (action) { "BUY" -> price + risk; "SELL" -> price - risk; else -> 0.0 }
        val tp2 = when (action) { "BUY" -> price + risk * 2; "SELL" -> price - risk * 2; else -> 0.0 }
        val tp3 = when (action) { "BUY" -> price + risk * 3; "SELL" -> price - risk * 3; else -> 0.0 }
        val trend = when { longBull >= 3 && shortBull >= 3 -> "BULLISH ALIGNMENT"; longBear >= 3 && shortBear >= 3 -> "BEARISH ALIGNMENT"; else -> "MIXED / TRANSITION" }
        val holding = when { action == "WAIT" -> "No trade — wait for confirmation"; longBull >= 3 || longBear >= 3 -> "Higher-timeframe context: days to weeks"; else -> "Short-term setup: hours to days" }
        val alignment = "$longBull bullish / $longBear bearish higher frames • $shortBull bullish / $shortBear bearish lower frames"
        val explanation = when (action) {
            "BUY" -> "Higher-timeframe bias and lower-timeframe momentum support upside. For Spot, wait for a confirmed close and retest; do not chase price."
            "SELL" -> "Downward pressure is aligned across timeframes. For Spot holdings this may mean reduce risk or exit; it is not a short-selling instruction."
            else -> "The 12 timeframes do not provide sufficient alignment. WAIT for structure break, retest and candle-close confirmation."
        }
        return MultiTimeframeSignal(action, score.toInt().coerceIn(0, 100), trend, holding, if (action == "WAIT") 0.0 else price, stop, tp1, tp2, tp3, alignment, explanation, frames)
    }
}
