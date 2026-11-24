package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle
import kotlin.math.abs

/** Similarity scan over supplied historical candles; analysis-only, never places orders. */
data class HistoricalWaitReport(
    val status: String,
    val similarCases: Int,
    val bullishRate: Double,
    val bearishRate: Double,
    val oneHourAverageReturnPct: Double?,
    val twoHourAverageReturnPct: Double?,
    val recommendation: String,
    val rationale: String
)

object HistoricalMemoryWaitingEngine {
    fun analyze(candles: List<Candle>, timeframeMinutes: Int = 15, lookback: Int = 8): HistoricalWaitReport {
        val barsPerHour = (60 / timeframeMinutes.coerceAtLeast(1)).coerceAtLeast(1)
        val currentWindow = candles.takeLast(lookback + 1)
        if (candles.size < lookback * 5 || currentWindow.size < lookback + 1) {
            return HistoricalWaitReport("INSUFFICIENT HISTORY", 0, 0.0, 0.0, null, null, "WAIT", "Supply a larger candle history (ideally 1–3 years); no conclusion is made from a short sample.")
        }
        val currentReturns = returns(currentWindow)
        val currentMagnitude = currentReturns.map { abs(it) }.average().coerceAtLeast(1e-9)
        val currentDirection = currentReturns.sum().compareTo(0.0)
        val h1 = barsPerHour
        val h2 = barsPerHour * 2
        val candidates = mutableListOf<Pair<Double, Int>>()
        // Historical windows are strictly earlier than the current window and have completed outcome horizons.
        val currentStart = candles.size - lookback - 1
        for (start in 0 until (currentStart - lookback - h2).coerceAtLeast(0)) {
            val window = candles.subList(start, start + lookback + 1)
            val oldReturns = returns(window)
            val corr = similarity(currentReturns, oldReturns)
            val magnitude = oldReturns.map { abs(it) }.average().coerceAtLeast(1e-9)
            val magScore = (1.0 - abs(kotlin.math.ln(magnitude / currentMagnitude)).coerceAtMost(4.0) / 4.0).coerceAtLeast(0.0)
            val score = (corr * 0.75 + magScore * 0.25)
            if (score >= 0.65) candidates += score to (start + lookback)
        }
        val matches = candidates.sortedByDescending { it.first }.take(120).map { it.second }
        if (matches.size < 20) return HistoricalWaitReport("LOW MATCH SAMPLE", matches.size, 0.0, 0.0, null, null, "WAIT", "Only ${matches.size} similar historical patterns found; avoid a confident timing claim.")
        fun avgForward(bars: Int): Double? {
            val vals = matches.mapNotNull { idx ->
                val future = idx + bars
                if (idx in candles.indices && future in candles.indices && candles[idx].close > 0.0)
                    (candles[future].close / candles[idx].close - 1.0) * 100.0 else null
            }
            return vals.takeIf { it.isNotEmpty() }?.average()
        }
        val oneHour = avgForward(h1)
        val twoHour = avgForward(h2)
        val ups = matches.count { candles[it].close >= candles[it - lookback].close }.toDouble() / matches.size
        val downs = 1.0 - ups
        val preferred = if (currentDirection > 0) ups else if (currentDirection < 0) downs else 0.5
        val recommendation = when {
            preferred >= 0.62 -> "DIRECTIONAL SETUP — CONFIRM BEFORE ENTRY"
            preferred <= 0.38 -> "OPPOSITE-BIAS / NO ENTRY"
            oneHour != null && twoHour != null && abs(twoHour) > abs(oneHour) * 1.15 -> "WAIT 2 HOURS, THEN RE-ANALYZE"
            else -> "WAIT 1 HOUR, THEN RE-ANALYZE"
        }
        val rationale = "${matches.size} similar cases from supplied history; aligned-direction frequency ${(preferred * 100).toInt()}%; 1h avg ${oneHour?.let { "%.3f%%".format(it) } ?: "n/a"}, 2h avg ${twoHour?.let { "%.3f%%".format(it) } ?: "n/a"}. This is a historical comparison, not a forecast guarantee."
        return HistoricalWaitReport("HISTORICAL MATCHES READY", matches.size, ups, downs, oneHour, twoHour, recommendation, rationale)
    }

    private fun returns(window: List<Candle>): List<Double> = window.zipWithNext { a, b ->
        if (a.close > 0.0) b.close / a.close - 1.0 else 0.0
    }

    private fun similarity(a: List<Double>, b: List<Double>): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0
        val ma = a.average(); val mb = b.average()
        val va = a.sumOf { (it - ma) * (it - ma) }; val vb = b.sumOf { (it - mb) * (it - mb) }
        if (va <= 1e-12 || vb <= 1e-12) return if (a.zip(b).all { abs(it.first - it.second) < 1e-6 }) 1.0 else 0.0
        val cov = a.zip(b).sumOf { (x, y) -> (x - ma) * (y - mb) }
        return (cov / kotlin.math.sqrt(va * vb)).coerceIn(-1.0, 1.0)
    }
}
