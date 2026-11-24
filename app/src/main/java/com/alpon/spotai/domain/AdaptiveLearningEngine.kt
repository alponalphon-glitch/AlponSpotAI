package com.alpon.spotai.domain

/** Closed Paper Trading outcome used for conservative, regime-specific feedback. */
data class AdaptiveTradeOutcome(
    val regime: String,
    val pnlPercent: Double,
    val timestamp: Long
)

data class AdaptiveLearningDecision(
    val allowed: Boolean,
    val riskMultiplier: Double,
    val sampleCount: Int,
    val winRatePercent: Double?,
    val averageReturnPercent: Double?,
    val explanation: String
)

/**
 * A conservative feedback layer, not a predictive ML model.
 * It learns only from closed Paper Trading outcomes and never increases baseline risk.
 */
object AdaptiveLearningEngine {
    private const val MIN_SAMPLES_FOR_BLOCK = 12
    private const val MIN_SAMPLES_FOR_REDUCED_RISK = 8
    private const val MAX_SAMPLES_PER_REGIME = 30

    fun evaluate(regime: String, history: List<AdaptiveTradeOutcome>): AdaptiveLearningDecision {
        val key = regime.trim().uppercase().ifBlank { "UNKNOWN" }
        val sample = history.asSequence()
            .filter { it.regime.trim().uppercase() == key && it.pnlPercent.isFinite() }
            .sortedByDescending { it.timestamp }
            .take(MAX_SAMPLES_PER_REGIME)
            .toList()
        val count = sample.size
        if (count < MIN_SAMPLES_FOR_REDUCED_RISK) {
            return AdaptiveLearningDecision(
                allowed = true,
                riskMultiplier = 1.0,
                sampleCount = count,
                winRatePercent = if (count == 0) null else sample.count { it.pnlPercent > 0.0 } * 100.0 / count,
                averageReturnPercent = if (count == 0) null else sample.map { it.pnlPercent }.average(),
                explanation = "Adaptive learning warm-up: $count/$MIN_SAMPLES_FOR_REDUCED_RISK closed paper trades in $key; baseline risk only."
            )
        }

        val wins = sample.count { it.pnlPercent > 0.0 }
        val winRate = wins * 100.0 / count
        val average = sample.map { it.pnlPercent }.average()

        if (count >= MIN_SAMPLES_FOR_BLOCK && winRate < 40.0 && average <= 0.0) {
            return AdaptiveLearningDecision(
                allowed = false,
                riskMultiplier = 0.0,
                sampleCount = count,
                winRatePercent = winRate,
                averageReturnPercent = average,
                explanation = "Adaptive guard blocked BUY: $key setup has $count recent paper trades, ${"%.1f".format(winRate)}% wins and negative/non-positive average return."
            )
        }

        if (winRate < 50.0 || average <= 0.0) {
            return AdaptiveLearningDecision(
                allowed = true,
                riskMultiplier = 0.5,
                sampleCount = count,
                winRatePercent = winRate,
                averageReturnPercent = average,
                explanation = "Adaptive guard reduced position allowance by 50%: recent $key paper performance is weak."
            )
        }

        return AdaptiveLearningDecision(
            allowed = true,
            riskMultiplier = 1.0,
            sampleCount = count,
            winRatePercent = winRate,
            averageReturnPercent = average,
            explanation = "Adaptive feedback: $key paper results are positive across $count recent trades; risk remains at baseline, never increased."
        )
    }

    fun encode(history: List<AdaptiveTradeOutcome>): String = history.takeLast(100).joinToString(";") {
        "${it.regime.replace('|', '_')}|${it.pnlPercent}|${it.timestamp}"
    }

    fun decode(raw: String?): List<AdaptiveTradeOutcome> = raw.orEmpty()
        .split(';')
        .mapNotNull { row ->
            val p = row.split('|')
            if (p.size != 3) null else runCatching {
                AdaptiveTradeOutcome(p[0], p[1].toDouble(), p[2].toLong())
            }.getOrNull()?.takeIf { it.regime.isNotBlank() && it.pnlPercent.isFinite() && it.timestamp > 0L }
        }
}
