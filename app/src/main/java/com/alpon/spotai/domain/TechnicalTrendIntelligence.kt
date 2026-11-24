package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** Closed-candle trend confirmation suite. Values are computed from supplied OHLCV, never placeholders. */
data class TechnicalTrendReport(
    val direction: String,
    val strength: String,
    val score: Int,
    val adx: Double,
    val plusDi: Double,
    val minusDi: Double,
    val emaRibbonBullish: Boolean,
    val emaRibbonBearish: Boolean,
    val supertrend: String,
    val rollingVwap: Double,
    val anchoredVwap: Double,
    val volumeProfilePoc: Double,
    val volumeProfileVah: Double,
    val volumeProfileVal: Double,
    val keltnerUpper: Double,
    val keltnerLower: Double,
    val choppiness: Double,
    val exhaustionWarning: Boolean,
    val evidence: List<String>,
    val warnings: List<String>
)

object TechnicalTrendIntelligence {
    fun analyze(input: List<Candle>): TechnicalTrendReport? {
        val now = System.currentTimeMillis()
        val c = input.filter { it.closeTime > 0L && it.closeTime <= now }
            .filter { it.time > 0L && it.open.isFinite() && it.high.isFinite() && it.low.isFinite() && it.close.isFinite() && it.volume.isFinite() && it.low > 0.0 && it.high >= it.low && it.volume >= 0.0 }
            .sortedBy { it.time }
        if (c.size < 220) return null
        val close = c.map { it.close }; val last = c.last(); val price = last.close
        fun ema(xs: List<Double>, period: Int): Double {
            if (xs.size < period) return Double.NaN
            val k = 2.0 / (period + 1.0); var v = xs.take(period).average()
            for (i in period until xs.size) v = xs[i] * k + v * (1.0 - k)
            return v
        }
        fun tr(i: Int): Double { val x = c[i]; val p = c[i - 1].close; return max(x.high - x.low, max(abs(x.high - p), abs(x.low - p))) }
        fun atr(period: Int): Double {
            if (c.size <= period) return Double.NaN
            var value = (1..period).map { tr(it) }.average()
            for (i in period + 1 until c.size) value = (value * (period - 1) + tr(i)) / period
            return value
        }
        // Wilder ADX with smoothed directional movement and true range.
        val period = 14
        var smTr = (1..period).sumOf { tr(it) }
        var smPlus = 0.0; var smMinus = 0.0
        for (i in 1..period) {
            val up = c[i].high - c[i - 1].high; val down = c[i - 1].low - c[i].low
            smPlus += if (up > down && up > 0.0) up else 0.0
            smMinus += if (down > up && down > 0.0) down else 0.0
        }
        val dxs = mutableListOf<Double>()
        fun addDx() { val p = if (smTr > 0) smPlus / smTr * 100 else 0.0; val m = if (smTr > 0) smMinus / smTr * 100 else 0.0; dxs += if (p + m > 0) abs(p - m) / (p + m) * 100 else 0.0 }
        addDx()
        for (i in period + 1 until c.size) {
            val up = c[i].high - c[i - 1].high; val down = c[i - 1].low - c[i].low
            smPlus = smPlus - smPlus / period + if (up > down && up > 0.0) up else 0.0
            smMinus = smMinus - smMinus / period + if (down > up && down > 0.0) down else 0.0
            smTr = smTr - smTr / period + tr(i)
            addDx()
        }
        var adx = dxs.take(period).average()
        for (i in period until dxs.size) adx = (adx * (period - 1) + dxs[i]) / period
        val plusDi = if (smTr > 0) smPlus / smTr * 100 else 0.0
        val minusDi = if (smTr > 0) smMinus / smTr * 100 else 0.0
        val e20 = ema(close, 20); val e50 = ema(close, 50); val e100 = ema(close, 100); val e200 = ema(close, 200)
        val ribbonBull = price > e20 && e20 > e50 && e50 > e100 && e100 > e200
        val ribbonBear = price < e20 && e20 < e50 && e50 < e100 && e100 < e200
        // Supertrend (10, 3): final bands and direction carried forward candle by candle.
        val stAtrPeriod = 10; val mult = 3.0
        var upper = Double.NaN; var lower = Double.NaN; var stBull = true
        var stAtr = (1..stAtrPeriod).map { tr(it) }.average()
        for (i in stAtrPeriod until c.size) {
            if (i > stAtrPeriod) stAtr = (stAtr * (stAtrPeriod - 1) + tr(i)) / stAtrPeriod
            val a = stAtr
            val mid = (c[i].high + c[i].low) / 2.0
            val basicUpper = mid + mult * a; val basicLower = mid - mult * a
            val prevClose = c[i - 1].close
            upper = if (!upper.isFinite() || basicUpper < upper || prevClose > upper) basicUpper else upper
            lower = if (!lower.isFinite() || basicLower > lower || prevClose < lower) basicLower else lower
            if (stBull && c[i].close < lower) stBull = false else if (!stBull && c[i].close > upper) stBull = true
        }
        // VWAP for latest 50 bars and anchored from the latest confirmed 20-bar swing extreme.
        val recent = c.takeLast(50); val vol = recent.sumOf { it.volume }
        val vwap = if (vol > 0.0) recent.sumOf { ((it.high + it.low + it.close) / 3.0) * it.volume } / vol else Double.NaN
        val anchorStart = (c.size - 100 until c.size - 20).minByOrNull { c[it].low } ?: c.size - 50
        val anchorSlice = c.drop(anchorStart); val anchorVol = anchorSlice.sumOf { it.volume }
        val anchored = if (anchorVol > 0.0) anchorSlice.sumOf { ((it.high + it.low + it.close) / 3.0) * it.volume } / anchorVol else Double.NaN
        // 48-bin volume-at-price profile over the last 200 candles; 70% value area around POC.
        val profileCandles = c.takeLast(200)
        val profileLow = profileCandles.minOf { it.low }; val profileHigh = profileCandles.maxOf { it.high }
        val binCount = 48
        val bins = DoubleArray(binCount)
        if (profileHigh > profileLow) profileCandles.forEach { candle ->
            val typical = (candle.high + candle.low + candle.close) / 3.0
            val index = (((typical - profileLow) / (profileHigh - profileLow)) * binCount).toInt().coerceIn(0, binCount - 1)
            bins[index] += candle.volume
        }
        val pocIndex = bins.indices.maxByOrNull { bins[it] } ?: 0
        val totalProfileVolume = bins.sum()
        var left = pocIndex; var right = pocIndex; var accumulated = bins[pocIndex]
        while (totalProfileVolume > 0.0 && accumulated < totalProfileVolume * 0.70 && (left > 0 || right < binCount - 1)) {
            val leftVol = if (left > 0) bins[left - 1] else -1.0
            val rightVol = if (right < binCount - 1) bins[right + 1] else -1.0
            if (rightVol > leftVol) { right++; accumulated += bins[right] } else if (left > 0) { left--; accumulated += bins[left] } else { right++; accumulated += bins[right] }
        }
        val binWidth = (profileHigh - profileLow) / binCount
        val poc = profileLow + (pocIndex + 0.5) * binWidth
        val valPrice = profileLow + left * binWidth
        val vahPrice = profileLow + (right + 1) * binWidth
        // Keltner channel: EMA20 +/- 2 * ATR20.
        val a20 = atr(20); val kelUpper = e20 + 2.0 * a20; val kelLower = e20 - 2.0 * a20
        // Choppiness Index (14): high values indicate range/chop, low values directional movement.
        val chopWindow = c.takeLast(14); val trSum = (c.size - 13 until c.size).sumOf { tr(it) }
        val hh = chopWindow.maxOf { it.high }; val ll = chopWindow.minOf { it.low }
        val chop = if (hh > ll && trSum > 0) 100.0 * ln(trSum / (hh - ll)) / ln(14.0) else 100.0
        // Exhaustion proxy: weakening ADX + RSI extreme/reversal + price stretched from EMA20.
        val changes = close.zipWithNext { a, b -> b - a }
        var gain = changes.take(14).sumOf { it.coerceAtLeast(0.0) } / 14.0
        var loss = changes.take(14).sumOf { (-it).coerceAtLeast(0.0) } / 14.0
        for (i in 14 until changes.size) { gain = (gain * 13 + changes[i].coerceAtLeast(0.0)) / 14; loss = (loss * 13 + (-changes[i]).coerceAtLeast(0.0)) / 14 }
        val rsi = if (loss <= 1e-12) 100.0 else 100.0 - 100.0 / (1.0 + gain / loss)
        val oldAdx = dxs.take(dxs.size - 5).takeLast(14).average()
        val adxFalling = oldAdx.isFinite() && adx < oldAdx - 2.0
        val stretched = a20.isFinite() && a20 > 0 && abs(price - e20) / a20 > 2.0
        val exhaustion = (adxFalling && (rsi > 70 || rsi < 30)) || (stretched && (rsi > 75 || rsi < 25))
        var score = 0
        score += when { ribbonBull -> 25; ribbonBear -> -25; price > e50 -> 10; price < e50 -> -10; else -> 0 }
        score += when { adx >= 25 && plusDi > minusDi -> 20; adx >= 25 && minusDi > plusDi -> -20; else -> 0 }
        score += when { stBull -> 15; else -> -15 }
        score += when { price > vwap && price > anchored -> 15; price < vwap && price < anchored -> -15; else -> 0 }
        score += when { chop < 38 -> if (price > e20) 10 else -10; chop > 61.8 -> 0; else -> 0 }
        if (exhaustion) score = (score * 0.65).toInt()
        score = score.coerceIn(-100, 100)
        val direction = when { score >= 25 -> "BULLISH"; score <= -25 -> "BEARISH"; else -> "NEUTRAL" }
        val strength = when { adx >= 40 && abs(score) >= 60 -> "VERY STRONG"; adx >= 25 && abs(score) >= 40 -> "STRONG"; adx >= 18 -> "MODERATE"; else -> "WEAK / RANGE" }
        val evidence = buildList {
            add("EMA ribbon: ${if (ribbonBull) "bullish aligned" else if (ribbonBear) "bearish aligned" else "mixed"}")
            add("ADX ${"%.1f".format(adx)}; +DI ${"%.1f".format(plusDi)}; -DI ${"%.1f".format(minusDi)}")
            add("Supertrend(10,3): ${if (stBull) "BULLISH" else "BEARISH"}")
            add("Rolling VWAP ${"%.6f".format(vwap)}; anchored VWAP ${"%.6f".format(anchored)}")
            add("Volume Profile POC ${"%.6f".format(poc)}; VAH ${"%.6f".format(vahPrice)}; VAL ${"%.6f".format(valPrice)}")
            add("Keltner(20,2ATR): ${"%.6f".format(kelLower)} .. ${"%.6f".format(kelUpper)}")
            add("Choppiness ${"%.1f".format(chop)}; RSI14 ${"%.1f".format(rsi)}")
        }
        val warnings = buildList { if (chop >= 61.8) add("Choppy/range regime: trend-following confirmation is weak."); if (exhaustion) add("Trend exhaustion risk: ADX/RSI/ATR stretch conditions detected."); if (!vwap.isFinite() || !anchored.isFinite()) add("VWAP unavailable because volume is zero or invalid.") }
        return TechnicalTrendReport(direction, strength, score, adx, plusDi, minusDi, ribbonBull, ribbonBear, if (stBull) "BULLISH" else "BEARISH", vwap, anchored, poc, vahPrice, valPrice, kelUpper, kelLower, chop, exhaustion, evidence, warnings)
    }
}
