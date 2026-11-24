package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle
import kotlin.math.abs

/** Detects abnormal market conditions before a new entry; deterministic safety rules, not prediction. */
data class MarketShockReport(
    val safeForNewEntry: Boolean,
    val severity: String,
    val findings: List<String>,
    val latestRangeToMedian: Double?,
    val latestVolumeToMedian: Double?
)

object MarketShockCircuitBreaker {
    fun evaluate(candles: List<Candle>, now: Long = System.currentTimeMillis()): MarketShockReport {
        val closed = candles.filter { it.closeTime > 0L && it.closeTime <= now &&
            it.open.isFinite() && it.high.isFinite() && it.low.isFinite() && it.close.isFinite() &&
            it.volume.isFinite() && it.open > 0.0 && it.low > 0.0 && it.high >= it.low && it.volume >= 0.0
        }.sortedBy { it.time }
        if (closed.size < 35) return MarketShockReport(false, "INSUFFICIENT DATA",
            listOf("Circuit breaker requires at least 35 valid closed candles; new entry is blocked."), null, null)
        val sample = closed.takeLast(31)
        val baseline = sample.dropLast(1)
        val latest = sample.last()
        fun median(values: List<Double>): Double {
            if (values.isEmpty()) return 0.0
            val sorted = values.sorted(); val m = sorted.size / 2
            return if (sorted.size % 2 == 0) (sorted[m - 1] + sorted[m]) / 2.0 else sorted[m]
        }
        val ranges = baseline.map { it.high - it.low }.filter { it.isFinite() && it > 0.0 }
        val volumes = baseline.map { it.volume }.filter { it.isFinite() && it > 0.0 }
        val medianRange = median(ranges)
        val medianVolume = median(volumes)
        val latestRange = latest.high - latest.low
        val rangeRatio = if (medianRange > 0.0) latestRange / medianRange else null
        val volumeRatio = if (medianVolume > 0.0) latest.volume / medianVolume else null
        val findings = mutableListOf<String>()
        if (rangeRatio != null && rangeRatio >= 4.0) findings += "Volatility shock: latest closed candle range is %.1f× its prior 30-candle median.".format(rangeRatio)
        val previousClose = baseline.lastOrNull()?.close ?: 0.0
        if (previousClose > 0.0 && abs(latest.open / previousClose - 1.0) >= 0.025)
            findings += "Large opening gap of %.2f%% detected versus the prior close.".format((latest.open / previousClose - 1.0) * 100.0)
        val upperWick = latest.high - maxOf(latest.open, latest.close)
        if (latestRange > 0.0 && upperWick / latestRange >= 0.65 && latest.close < latest.open)
            findings += "Strong upper-wick rejection on the latest closed candle; bullish entries are unsafe."
        if (volumeRatio != null && volumeRatio >= 5.0 && latest.close < latest.open)
            findings += "Abnormal sell-candle volume: %.1f× the prior 30-candle median.".format(volumeRatio)
        val recentCloses = closed.takeLast(4)
        if (recentCloses.size == 4) {
            val first = recentCloses.first().close
            val last = recentCloses.last().close
            if (first > 0.0 && abs(last / first - 1.0) >= 0.08)
                findings += "Fast 3-interval price displacement of %.2f%%; wait for volatility normalization.".format((last / first - 1.0) * 100.0)
        }
        val severity = when {
            findings.size >= 3 -> "CRITICAL"
            findings.size >= 1 -> "WARNING"
            else -> "NORMAL"
        }
        return MarketShockReport(findings.isEmpty(), severity, findings, rangeRatio, volumeRatio)
    }
}
