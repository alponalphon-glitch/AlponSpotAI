package com.alpon.spotai.domain

import kotlin.math.max
import java.util.Random

/**
 * Bootstrap stress lab for CLOSED paper-trade outcomes.
 * The reported frequencies are resampling diagnostics, not calibrated future probabilities.
 * Trade outcomes are assumed exchangeable; serial dependence and regime shifts are not modeled.
 */
data class MonteCarloPaperRiskReport(
    val available: Boolean,
    val sampleCount: Int,
    val paths: Int,
    val horizonTrades: Int,
    val exposurePercent: Double,
    val p05EndingReturnPercent: Double?,
    val medianEndingReturnPercent: Double?,
    val p95MaximumDrawdownPercent: Double?,
    val resampledDrawdown10PercentFrequency: Double?,
    val note: String
)

object MonteCarloPaperRiskEngine {
    private const val MIN_SAMPLES = 20
    private const val PATHS = 2_000
    private const val HORIZON = 100

    fun evaluate(
        outcomes: List<AdaptiveTradeOutcome>,
        exposurePercent: Double = 25.0,
        paths: Int = PATHS,
        horizonTrades: Int = HORIZON,
        seed: Long = 0x4E45584F52414L
    ): MonteCarloPaperRiskReport {
        val clean = outcomes.asSequence()
            .filter { it.pnlPercent.isFinite() && it.timestamp > 0L && it.regime.isNotBlank() }
            .sortedBy { it.timestamp }
            .toList()
            .takeLast(100)
            .map { it.pnlPercent.coerceIn(-100.0, 1_000.0) }
        val exposure = exposurePercent.takeIf { it.isFinite() && it in 1.0..100.0 } ?: 25.0
        val safePaths = paths.coerceIn(100, 10_000)
        val safeHorizon = horizonTrades.coerceIn(20, 500)
        if (clean.size < MIN_SAMPLES) {
            return MonteCarloPaperRiskReport(
                false, clean.size, 0, safeHorizon, exposure, null, null, null, null,
                "Warm-up: need at least $MIN_SAMPLES valid closed paper trades; no Monte Carlo estimate is shown."
            )
        }

        val random = Random(seed xor clean.size.toLong() xor clean.sumOf { (it * 100.0).toLong() })
        val endingReturns = ArrayList<Double>(safePaths)
        val maxDrawdowns = ArrayList<Double>(safePaths)
        repeat(safePaths) {
            var equity = 1.0
            var peak = 1.0
            var maxDrawdown = 0.0
            repeat(safeHorizon) {
                val tradeReturn = clean[random.nextInt(clean.size)] / 100.0
                // Outcome is a return on allocated capital, not on the whole account.
                val equityFactor = (1.0 + tradeReturn * exposure / 100.0).coerceAtLeast(0.0)
                equity *= equityFactor
                if (!equity.isFinite()) equity = Double.MAX_VALUE
                peak = max(peak, equity)
                val drawdown = if (peak > 0.0) (peak - equity) / peak * 100.0 else 100.0
                maxDrawdown = max(maxDrawdown, drawdown)
            }
            endingReturns += ((equity - 1.0) * 100.0).coerceIn(-100.0, 1.0e12)
            maxDrawdowns += maxDrawdown.coerceIn(0.0, 100.0)
        }
        endingReturns.sort()
        maxDrawdowns.sort()
        val ddFrequency = maxDrawdowns.count { it >= 10.0 } * 100.0 / maxDrawdowns.size
        return MonteCarloPaperRiskReport(
            true, clean.size, safePaths, safeHorizon, exposure,
            percentile(endingReturns, 0.05), percentile(endingReturns, 0.50),
            percentile(maxDrawdowns, 0.95), ddFrequency,
            "Bootstrap of recent closed paper trades; assumes ${exposure.toInt()}% capital exposure per trade and resamples outcomes with replacement. These are historical-sequence stress diagnostics, not future probabilities, a strategy forecast, or a live-risk guarantee. Serial correlation, changing regimes, fees not recorded in outcomes, and liquidity gaps may change results."
        )
    }

    private fun percentile(sorted: List<Double>, q: Double): Double? {
        if (sorted.isEmpty()) return null
        val index = ((sorted.size - 1) * q).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index].takeIf { it.isFinite() }
    }
}
