package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Deterministic price-action analysis. It is rule-based, not a trained neural network. */
data class HorizonTrend(
    val label: String,
    val direction: String,
    val score: Double,
    val candles: Int,
    val changePercent: Double,
    val structure: String
)

data class FibonacciLevel(val ratio: Double, val price: Double)

data class CandlePatternReading(
    val name: String,
    val direction: String,
    val strength: Int,
    val explanation: String
)

data class PriceActionReport(
    val yearlyTrend: HorizonTrend?,
    val twoYearTrend: HorizonTrend?,
    val weeklyTrend: HorizonTrend?,
    val monthlyTrend: HorizonTrend?,
    val patterns: List<CandlePatternReading>,
    val fibonacci: List<FibonacciLevel>,
    val volumeConfirmation: String,
    val summary: String
)

object PriceActionAnalysis {
    private const val DAY = 86_400_000L

    fun analyze(
        dailyInput: List<Candle>,
        weeklyInput: List<Candle>,
        monthlyInput: List<Candle>
    ): PriceActionReport? {
        val daily = dailyInput.sortedBy { it.time }.dropLastIfOpen()
        val weekly = weeklyInput.sortedBy { it.time }.dropLastIfOpen()
        val monthly = monthlyInput.sortedBy { it.time }.dropLastIfOpen()
        if (daily.size < 30) return null

        val now = daily.last().time
        val oneYear = daily.filter { it.time >= now - 365L * DAY }
        val twoYear = daily.filter { it.time >= now - 730L * DAY }
        val yearTrend = horizon("1 YEAR", oneYear)
        val twoYearTrend = horizon("2 YEARS", twoYear)
        val weekTrend = horizon("WEEKLY", weekly.takeLast(104))
        val monthTrend = horizon("MONTHLY", monthly.takeLast(24))
        val patterns = detectPatterns(daily)
        val fib = fibonacci(daily.takeLast(730))
        val volume = volumeConfirmation(daily)

        val longDirection = twoYearTrend?.direction ?: yearTrend?.direction ?: "UNKNOWN"
        val midDirection = yearTrend?.direction ?: "UNKNOWN"
        val weeklyDirection = weekTrend?.direction ?: "UNKNOWN"
        val summary = when {
            longDirection == "BULLISH" && midDirection == "BULLISH" && weeklyDirection == "BULLISH" ->
                "Long-, medium- and weekly trends are aligned bullish; wait for a pullback and candle-close confirmation."
            longDirection == "BEARISH" && midDirection == "BEARISH" && weeklyDirection == "BEARISH" ->
                "Long-, medium- and weekly trends are aligned bearish; Spot users should prioritize capital protection."
            else -> "Trend horizons are mixed. Treat short-term signals cautiously until higher-timeframe structure confirms."
        }

        return PriceActionReport(yearTrend, twoYearTrend, weekTrend, monthTrend, patterns, fib, volume, summary)
    }

    private fun List<Candle>.dropLastIfOpen(): List<Candle> {
        if (isEmpty()) return this
        return if (last().closeTime > 0L && last().closeTime > System.currentTimeMillis()) dropLast(1) else this
    }

    private fun horizon(label: String, candles: List<Candle>): HorizonTrend? {
        if (candles.size < 12) return null
        val first = candles.first().close
        val last = candles.last().close
        if (first <= 0.0 || last <= 0.0) return null
        val change = (last / first - 1.0) * 100.0
        val closes = candles.map { it.close }
        val fast = ema(closes, min(20, closes.size))
        val slow = ema(closes, min(50, closes.size))
        val recentHighs = swingPrices(candles, true).takeLast(3)
        val recentLows = swingPrices(candles, false).takeLast(3)
        val structure = when {
            recentHighs.size >= 2 && recentLows.size >= 2 &&
                recentHighs.last() > recentHighs[recentHighs.lastIndex - 1] &&
                recentLows.last() > recentLows[recentLows.lastIndex - 1] -> "HH + HL"
            recentHighs.size >= 2 && recentLows.size >= 2 &&
                recentHighs.last() < recentHighs[recentHighs.lastIndex - 1] &&
                recentLows.last() < recentLows[recentLows.lastIndex - 1] -> "LH + LL"
            else -> "RANGE / TRANSITION"
        }
        val slope = linearSlope(closes.takeLast(min(100, closes.size)))
        var score = 0.0
        if (last > fast) score += 25.0 else score -= 25.0
        if (fast > slow) score += 25.0 else score -= 25.0
        score += (change * 2.0).coerceIn(-30.0, 30.0)
        score += when (structure) { "HH + HL" -> 20.0; "LH + LL" -> -20.0; else -> 0.0 }
        score += if (slope > 0) 10.0 else if (slope < 0) -10.0 else 0.0
        score = score.coerceIn(-100.0, 100.0)
        val direction = when { score >= 20.0 -> "BULLISH"; score <= -20.0 -> "BEARISH"; else -> "SIDEWAYS" }
        return HorizonTrend(label, direction, score, candles.size, change, structure)
    }

    private fun ema(values: List<Double>, period: Int): Double {
        if (values.isEmpty()) return Double.NaN
        val p = period.coerceIn(1, values.size)
        val k = 2.0 / (p + 1.0)
        var result = values.take(p).average()
        for (v in values.drop(p)) result = v * k + result * (1.0 - k)
        return result
    }

    private fun linearSlope(values: List<Double>): Double {
        if (values.size < 3) return 0.0
        val n = values.size.toDouble()
        val meanX = (n - 1.0) / 2.0
        val meanY = values.average()
        var numerator = 0.0
        var denominator = 0.0
        values.forEachIndexed { i, v ->
            val dx = i - meanX
            numerator += dx * (v - meanY)
            denominator += dx * dx
        }
        return if (denominator == 0.0 || meanY == 0.0) 0.0 else numerator / denominator / abs(meanY)
    }

    private fun swingPrices(candles: List<Candle>, highs: Boolean): List<Double> {
        val result = mutableListOf<Double>()
        for (i in 2 until candles.size - 2) {
            val v = if (highs) candles[i].high else candles[i].low
            val isPivot = if (highs) {
                (i - 2 until i).all { v > candles[it].high } && (i + 1..i + 2).all { v >= candles[it].high }
            } else {
                (i - 2 until i).all { v < candles[it].low } && (i + 1..i + 2).all { v <= candles[it].low }
            }
            if (isPivot) result += v
        }
        return result
    }

    private fun detectPatterns(c: List<Candle>): List<CandlePatternReading> {
        if (c.size < 3) return emptyList()
        val a = c[c.lastIndex - 1]
        val b = c.last()
        val body = abs(b.close - b.open).coerceAtLeast(1e-12)
        val range = (b.high - b.low).coerceAtLeast(1e-12)
        val upper = b.high - max(b.open, b.close)
        val lower = min(b.open, b.close) - b.low
        val out = mutableListOf<CandlePatternReading>()
        if (a.close < a.open && b.close > b.open && b.open <= a.close && b.close >= a.open)
            out += CandlePatternReading("BULLISH ENGULFING", "BULLISH", 75, "Current bullish body engulfs the previous bearish body.")
        if (a.close > a.open && b.close < b.open && b.open >= a.close && b.close <= a.open)
            out += CandlePatternReading("BEARISH ENGULFING", "BEARISH", 75, "Current bearish body engulfs the previous bullish body.")
        if (lower >= body * 2.0 && upper <= body && b.close >= b.low + range * .6)
            out += CandlePatternReading("HAMMER / PIN BAR", "BULLISH", 60, "Long lower wick shows rejection of lower prices; context is required.")
        if (upper >= body * 2.0 && lower <= body && b.close <= b.low + range * .4)
            out += CandlePatternReading("SHOOTING STAR", "BEARISH", 60, "Long upper wick shows rejection of higher prices; context is required.")
        if (body / range <= .1)
            out += CandlePatternReading("DOJI", "NEUTRAL", 35, "Open and close are close; market indecision.")
        val prev = c[c.lastIndex - 1]
        if (b.high < prev.high && b.low > prev.low)
            out += CandlePatternReading("INSIDE BAR", "NEUTRAL", 35, "Price is consolidating inside the previous candle range.")
        val first = c[c.lastIndex - 2]
        val firstBody = abs(first.close - first.open)
        val middleBody = abs(a.close - a.open)
        val firstMid = (first.open + first.close) / 2.0
        if (first.close < first.open && middleBody < firstBody * .55 && b.close > b.open && b.close > firstMid && b.open < a.close)
            out += CandlePatternReading("MORNING STAR", "BULLISH", 78, "Three-candle reversal structure; strongest after a sustained decline and near support.")
        if (first.close > first.open && middleBody < firstBody * .55 && b.close < b.open && b.close < firstMid && b.open > a.close)
            out += CandlePatternReading("EVENING STAR", "BEARISH", 78, "Three-candle reversal structure; strongest after a sustained rise and near resistance.")
        if (a.close < a.open && b.close > b.open && b.open < a.low && b.close > (a.open + a.close) / 2.0 && b.close < a.open)
            out += CandlePatternReading("PIERCING LINE", "BULLISH", 65, "Bullish recovery into the prior bearish body; confirm with trend and volume.")
        if (a.close > a.open && b.close < b.open && b.open > a.high && b.close < (a.open + a.close) / 2.0 && b.close > a.open)
            out += CandlePatternReading("DARK CLOUD COVER", "BEARISH", 65, "Bearish reversal attempt into the prior bullish body; confirm with context.")
        val c3 = c.takeLast(3)
        if (c3.all { it.close > it.open } && c3.zipWithNext().all { (x, y) -> y.close > x.close && y.open >= min(x.open, x.close) && y.open <= max(x.open, x.close) })
            out += CandlePatternReading("THREE WHITE SOLDIERS", "BULLISH", 82, "Three consecutive advancing candles; avoid chasing after an extended move.")
        if (c3.all { it.close < it.open } && c3.zipWithNext().all { (x, y) -> y.close < x.close && y.open <= max(x.open, x.close) && y.open >= min(x.open, x.close) })
            out += CandlePatternReading("THREE BLACK CROWS", "BEARISH", 82, "Three consecutive declining candles; check nearby support before acting.")
        return out
    }

    private fun fibonacci(candles: List<Candle>): List<FibonacciLevel> {
        if (candles.size < 20) return emptyList()
        val sample = candles.takeLast(200)
        val lowCandle = sample.minByOrNull { it.low } ?: return emptyList()
        val highCandle = sample.maxByOrNull { it.high } ?: return emptyList()
        val low = lowCandle.low
        val high = highCandle.high
        val range = high - low
        if (range <= 0.0) return emptyList()
        val upMove = lowCandle.time < highCandle.time
        val ratios = listOf(.236, .382, .5, .618, .786)
        return ratios.map { ratio ->
            FibonacciLevel(ratio, if (upMove) high - range * ratio else low + range * ratio)
        }
    }

    private fun volumeConfirmation(candles: List<Candle>): String {
        if (candles.size < 21) return "INSUFFICIENT VOLUME HISTORY"
        val avg = candles.takeLast(21).dropLast(1).map { it.volume }.average()
        val current = candles.last()
        val ratio = current.volume / avg.coerceAtLeast(1e-12)
        return when {
            ratio >= 1.5 && current.close > current.open -> "BULLISH VOLUME CONFIRMATION (${"%.2f".format(ratio)}x)"
            ratio >= 1.5 && current.close < current.open -> "BEARISH VOLUME CONFIRMATION (${"%.2f".format(ratio)}x)"
            else -> "NO STRONG VOLUME CONFIRMATION (${"%.2f".format(ratio)}x)"
        }
    }
}
