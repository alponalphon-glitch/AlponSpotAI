package com.alpon.spotai.domain

import kotlin.math.max

/** Conservative, deterministic trade-entry gate. It does not predict returns. */
data class TradeQualityDecision(
    val allowed: Boolean,
    val action: String,
    val qualityScore: Int,
    val blockers: List<String>,
    val checks: List<String>,
    val estimatedNetRewardRisk: Double?
)

data class ProtectionLimits(
    val riskPerTradePercent: Double = 0.5,
    val maxCapitalPerTradePercent: Double = 25.0,
    val dailyLossLimitPercent: Double = 2.0,
    val weeklyLossLimitPercent: Double = 5.0,
    val maxConsecutiveLosses: Int = 3,
    val minimumConfidence: Int = 60,
    val minimumDirectionalScore: Double = 30.0,
    val minimumAgreementRatio: Double = 0.67,
    val minimumNetRewardRisk: Double = 1.5,
    val feeRate: Double = 0.001,
    val slippageRate: Double = 0.0005
) {
    fun isValid(): Boolean =
        riskPerTradePercent in 0.05..2.0 &&
        maxCapitalPerTradePercent in 1.0..100.0 &&
        dailyLossLimitPercent in 0.5..10.0 &&
        weeklyLossLimitPercent in 1.0..20.0 &&
        maxConsecutiveLosses in 1..10 &&
        minimumConfidence in 1..100 &&
        minimumDirectionalScore in 0.0..100.0 &&
        minimumAgreementRatio in 0.0..1.0 &&
        minimumNetRewardRisk in 1.0..5.0 &&
        feeRate in 0.0..0.01 && slippageRate in 0.0..0.01
}

object CapitalProtectionEngine {
    val DEFAULT_LIMITS = ProtectionLimits()

    fun evaluateEntry(
        candidateAction: String,
        technicalAction: String?,
        confidence: Int,
        directionalScore: Double,
        activeComponents: Int,
        agreementRatio: Double,
        highVolatility: Boolean,
        possibleFalseBreakout: Boolean,
        entry: Double?,
        stop: Double?,
        target: Double?,
        limits: ProtectionLimits = DEFAULT_LIMITS
    ): TradeQualityDecision {
        // Spot SELL is an exit/reduction instruction, never a new short entry.
        if (candidateAction == "SELL") return TradeQualityDecision(
            true, "SELL", 100, emptyList(), listOf("Spot exit is permitted; no short position is opened."), null
        )
        if (candidateAction != "BUY") return TradeQualityDecision(
            false, "WAIT", 0, listOf("The combined analysis does not produce a BUY setup."), emptyList(), null
        )

        val blockers = mutableListOf<String>()
        val checks = mutableListOf<String>()
        if (!limits.isValid()) blockers += "Protection limits are invalid; entry is blocked."
        if (technicalAction != "BUY") blockers += "Technical analysis does not independently confirm BUY."
        if (confidence < limits.minimumConfidence) blockers += "Confidence is below ${limits.minimumConfidence} (not a win probability)."
        if (!directionalScore.isFinite() || directionalScore < limits.minimumDirectionalScore) blockers += "Directional score is below the entry threshold."
        if (activeComponents < 4) blockers += "Fewer than 4 independent analysis components are active."
        if (!agreementRatio.isFinite() || agreementRatio < limits.minimumAgreementRatio) blockers += "Analysis components do not agree strongly enough."
        if (highVolatility) blockers += "High-volatility regime: new entry blocked."
        if (possibleFalseBreakout) blockers += "Possible false breakout: wait for candle close and retest."

        val validPrices = entry != null && stop != null && target != null &&
            entry.isFinite() && stop.isFinite() && target.isFinite() &&
            entry > 0.0 && stop > 0.0 && target > entry && stop < entry
        var netRr: Double? = null
        if (!validPrices) {
            blockers += "Entry, stop-loss or target levels are invalid."
        } else {
            val e = entry!! * (1.0 + limits.slippageRate)
            val s = stop!! * (1.0 - limits.slippageRate)
            val t = target!! * (1.0 - limits.slippageRate)
            val risk = (e - s) + (e + s) * limits.feeRate
            val reward = (t - e) - (e + t) * limits.feeRate
            netRr = if (risk > 0.0) reward / risk else null
            if (netRr == null || !netRr.isFinite() || netRr < limits.minimumNetRewardRisk) {
                blockers += "Net reward/risk after estimated fees and slippage is below ${limits.minimumNetRewardRisk}:1."
            } else checks += "Net reward/risk after estimated costs: %.2f:1".format(netRr)
        }

        if (technicalAction == "BUY") checks += "Technical BUY confirmation present."
        if (confidence >= limits.minimumConfidence) checks += "Confidence threshold passed (heuristic score, not win probability)."
        if (activeComponents >= 4) checks += "At least 4 independent analysis components are active."
        if (agreementRatio >= limits.minimumAgreementRatio) checks += "Directional agreement threshold passed."
        if (!highVolatility) checks += "No high-volatility block."
        if (!possibleFalseBreakout) checks += "No false-breakout warning."

        val quality = (checks.size * 100 / 7).coerceIn(0, 100)
        val allowed = blockers.isEmpty()
        return TradeQualityDecision(allowed, if (allowed) "BUY" else "WAIT", quality, blockers, checks, netRr)
    }

    /** Stateful loss-streak / daily / weekly circuit breaker for a live or paper account. */
    class LossGuard(
        private val limits: ProtectionLimits = DEFAULT_LIMITS,
        initialState: String? = null
    ) {
        private var dayKey: Long? = null
        private var weekKey: Long? = null
        private var dayStartEquity = 0.0
        private var weekStartEquity = 0.0
        private var consecutiveLosses = 0
        private var haltedForDay = false
        private var haltedForWeek = false

        init {
            val parts = initialState?.split('|')
            if (parts?.size == 7) {
                dayKey = parts[0].toLongOrNull()
                weekKey = parts[1].toLongOrNull()
                dayStartEquity = parts[2].toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
                weekStartEquity = parts[3].toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
                consecutiveLosses = parts[4].toIntOrNull()?.coerceIn(0, 100) ?: 0
                haltedForDay = parts[5].toBooleanStrictOrNull() ?: false
                haltedForWeek = parts[6].toBooleanStrictOrNull() ?: false
            }
        }

        fun snapshot(): String = listOf(
            dayKey?.toString() ?: "", weekKey?.toString() ?: "",
            dayStartEquity.toString(), weekStartEquity.toString(),
            consecutiveLosses.toString(), haltedForDay.toString(), haltedForWeek.toString()
        ).joinToString("|")

        fun canOpenNewPosition(equity: Double, epochMillis: Long): Pair<Boolean, String> {
            if (!limits.isValid() || !equity.isFinite() || equity <= 0.0) return false to "Invalid protection state; trading blocked."
            val utcDay = Math.floorDiv(epochMillis, 86_400_000L)
            val utcWeek = Math.floorDiv(utcDay + 3L, 7L)
            if (dayKey != utcDay) {
                dayKey = utcDay
                dayStartEquity = equity
                haltedForDay = false
            }
            if (weekKey != utcWeek) {
                weekKey = utcWeek
                weekStartEquity = equity
                haltedForWeek = false
            }
            if (haltedForWeek) return false to "Weekly loss limit reached; new entries paused until next UTC week."
            if (haltedForDay) return false to "Daily loss limit reached; new entries paused until next UTC day."
            if (consecutiveLosses >= limits.maxConsecutiveLosses) return false to "Maximum consecutive losses reached; pause and review."
            return true to "Capital protection checks passed."
        }

        fun recordClosedTrade(netPnl: Double, equityAfterTrade: Double, epochMillis: Long) {
            if (!netPnl.isFinite() || !equityAfterTrade.isFinite() || equityAfterTrade <= 0.0) return
            canOpenNewPosition(equityAfterTrade, epochMillis)
            if (netPnl < 0.0) consecutiveLosses++ else if (netPnl > 0.0) consecutiveLosses = 0
            val dayLoss = if (dayStartEquity > 0.0) (dayStartEquity - equityAfterTrade) / dayStartEquity * 100.0 else 0.0
            val weekLoss = if (weekStartEquity > 0.0) (weekStartEquity - equityAfterTrade) / weekStartEquity * 100.0 else 0.0
            if (dayLoss >= limits.dailyLossLimitPercent) haltedForDay = true
            if (weekLoss >= limits.weeklyLossLimitPercent) haltedForWeek = true
        }

        fun consecutiveLossCount(): Int = consecutiveLosses
    }
}
