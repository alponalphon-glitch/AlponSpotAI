package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle
import kotlin.math.max

/**
 * Historical downside stress test for a proposed LONG/Spot BUY.
 * Uses only confirmed OHLC candles. Historical excursions are stress references,
 * not calibrated probabilities and not guarantees about future losses.
 */
data class DownsideHorizon(
    val bars: Int,
    val samples: Int,
    val adverseP95Percent: Double?,
    val worstAdversePercent: Double?
)

data class ScenarioRiskReport(
    val sufficientHistory: Boolean,
    val stopDistancePercent: Double?,
    val horizons: List<DownsideHorizon>,
    val worstSingleBarAdversePercent: Double?,
    val requiredStressStopPercent: Double?,
    val stopCoversHistoricalStress: Boolean,
    val estimatedLossAtStopPercent: Double?,
    val warnings: List<String>,
    val explanation: String
)

object ScenarioRiskEngine {
    private const val MIN_CANDLES = 120
    private val horizons = listOf(10, 20, 50)

    fun evaluate(
        candles: List<Candle>,
        signal: Signal?,
        now: Long = System.currentTimeMillis()
    ): ScenarioRiskReport {
        val closed = candles.asSequence()
            .filter { it.closeTime > 0L && it.closeTime <= now }
            .sortedBy { it.time }
            .toList()
        val warnings = mutableListOf<String>()
        if (closed.size < MIN_CANDLES) {
            return ScenarioRiskReport(
                false, null, emptyList(), null, null, false, null,
                listOf("Downside stress test needs at least $MIN_CANDLES confirmed closed candles; only ${closed.size} are available."),
                "Insufficient history: downside risk cannot be verified."
            )
        }

        val clean = closed.filter {
            it.open.isFinite() && it.high.isFinite() && it.low.isFinite() &&
                it.close.isFinite() && it.open > 0.0 && it.close > 0.0 &&
                it.low > 0.0 && it.high >= max(it.open, it.close) &&
                it.low <= minOf(it.open, it.close)
        }
        if (clean.size < MIN_CANDLES) {
            return ScenarioRiskReport(
                false, null, emptyList(), null, null, false, null,
                listOf("Too many invalid candles for a reliable downside stress test."),
                "Invalid OHLC data: risk cannot be verified."
            )
        }

        val perHorizon = horizons.map { horizon ->
            val adverse = (0 until clean.size - horizon).mapNotNull { start ->
                val base = clean[start].close
                val forwardLow = clean.subList(start + 1, start + horizon + 1).minOf { it.low }
                if (base > 0.0) ((base - forwardLow) / base * 100.0).coerceAtLeast(0.0)
                    .takeIf { it.isFinite() } else null
            }.sorted()
            DownsideHorizon(
                horizon, adverse.size,
                percentile(adverse, 0.95),
                adverse.maxOrNull()
            )
        }

        val oneBarAdverse = clean.zipWithNext().mapNotNull { (a, b) ->
            ((a.close - b.low) / a.close * 100.0).coerceAtLeast(0.0)
                .takeIf { a.close > 0.0 && it.isFinite() }
        }
        val worstBar = oneBarAdverse.maxOrNull()
        val stopDistance = signal?.let {
            if (it.entry.isFinite() && it.stop.isFinite() && it.entry > 0.0 &&
                it.stop > 0.0 && it.stop < it.entry) (it.entry - it.stop) / it.entry * 100.0
            else null
        }
        val p95 = perHorizon.mapNotNull { it.adverseP95Percent }.maxOrNull()
        val gapBuffer = worstBar?.times(0.5)
        val required = if (p95 != null && gapBuffer != null) p95 + gapBuffer else p95

        if (stopDistance == null) warnings += "No valid BUY entry/Stop Loss pair; stop coverage cannot be checked."
        if (required == null) warnings += "Historical downside distribution could not be calculated."
        if (stopDistance != null && required != null && stopDistance < required) {
            warnings += "Proposed Stop Loss is tighter than the historical 95th-percentile downside stress reference plus a half-bar adverse-move buffer."
        }
        if ((worstBar ?: 0.0) > 5.0) {
            warnings += "At least one historical candle moved more than 5% adversely; gap/fast-market losses can exceed a stop estimate."
        }

        val stopCoverage = stopDistance != null && required != null && stopDistance >= required
        val lossAtStop = stopDistance
        return ScenarioRiskReport(
            true, stopDistance, perHorizon, worstBar, required,
            stopCoverage, lossAtStop, warnings.distinct(),
            if (stopCoverage) "Stop distance covers the measured historical stress reference; this does not cap gap or execution losses."
            else "Historical downside stress is larger than the proposed stop distance or could not be verified."
        )
    }

    private fun percentile(sorted: List<Double>, q: Double): Double? {
        if (sorted.isEmpty()) return null
        val index = kotlin.math.ceil(q * sorted.size).toInt().coerceIn(1, sorted.size) - 1
        return sorted[index]
    }
}
