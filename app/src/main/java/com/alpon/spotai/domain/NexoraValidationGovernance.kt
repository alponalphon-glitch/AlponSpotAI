package com.alpon.spotai.domain

/**
 * Evidence and readiness governance for research models, paper forward observation,
 * Futures risk controls and Monte Carlo diagnostics. This report never authorizes orders.
 */
data class NexoraValidationGovernanceReport(
    val deepLearningStatus: String,
    val deepLearningDetail: String,
    val reinforcementLearningStatus: String,
    val reinforcementLearningDetail: String,
    val paperObservationDays: Long,
    val paperClosedTrades: Int,
    val paperRegimes: Int,
    val paperForwardStatus: String,
    val paperForwardDetail: String,
    val futuresMarginStatus: String,
    val futuresMarginDetail: String,
    val monteCarloStatus: String,
    val monteCarloDetail: String,
    val futuresExecutionAuthorized: Boolean = false,
    val liveExecutionAuthorized: Boolean = false
)

object NexoraValidationGovernanceEngine {
    private const val DAY_MS = 86_400_000L
    private const val MIN_FORWARD_DAYS = 30L
    private const val MIN_CLOSED_TRADES = 100
    private const val MIN_REGIMES = 3

    fun evaluate(
        adaptive: NexoraAdaptiveReport,
        paperOutcomes: List<AdaptiveTradeOutcome>,
        derivatives: FuturesDerivativesReport?,
        paperRisk: MonteCarloPaperRiskReport?
    ): NexoraValidationGovernanceReport {
        val dl = adaptive.deepLearning
        val rl = adaptive.reinforcementLearning
        val validOutcomes = paperOutcomes.filter {
            it.timestamp > 0L && it.pnlPercent.isFinite() && it.regime.isNotBlank()
        }
        val first = validOutcomes.minOfOrNull { it.timestamp }
        val last = validOutcomes.maxOfOrNull { it.timestamp }
        val days = if (first != null && last != null && last >= first) (last - first) / DAY_MS else 0L
        val regimes = validOutcomes.map { it.regime.trim().uppercase() }.distinct().size
        val paperReady = days >= MIN_FORWARD_DAYS && validOutcomes.size >= MIN_CLOSED_TRADES && regimes >= MIN_REGIMES
        val dlStatus = if (dl.acceptedAsEvidence) "PRELIMINARY HOLDOUT PASS • SHADOW ONLY" else if (dl.available) "EXPERIMENTAL • NOT QUALIFIED" else "WARM-UP • NO EVIDENCE"
        val rlStatus = if (rl.acceptedAsEvidence) "PRELIMINARY OOS PASS • SHADOW ONLY" else if (rl.available) "EXPERIMENTAL • NOT QUALIFIED" else "WARM-UP • NO EVIDENCE"
        val paperDetail = "Observed timestamp span: $days/$MIN_FORWARD_DAYS days; ${validOutcomes.size}/$MIN_CLOSED_TRADES valid closed paper trades; $regimes/$MIN_REGIMES distinct regimes. This is outcome-history coverage, not proof that the app ran continuously or that future performance will persist."
        val futuresDetail = when {
            derivatives == null -> "No derivatives snapshot supplied. Verified exchange liquidation price, maintenance-margin tier, margin mode, leverage, position notional, wallet equity and liquidation-distance checks are not connected to this controller."
            else -> "Derivatives context is ${if (derivatives.available) "available" else "unavailable"}, but its snapshot does not verify account-specific leverage, margin mode, maintenance-margin tier, position size or exchange liquidation price. A directional derivatives feed is not a liquidation calculator."
        }
        val mcDetail = paperRisk?.note ?: "No Monte Carlo report supplied. A minimum sample of closed paper outcomes is required."
        return NexoraValidationGovernanceReport(
            deepLearningStatus = dlStatus,
            deepLearningDetail = "${dl.note} Neural model remains a small research prototype; even a holdout pass only permits low-weight shadow evidence, never autonomous execution.",
            reinforcementLearningStatus = rlStatus,
            reinforcementLearningDetail = "${rl.note} Q-learning remains a research policy; an out-of-sample pass does not establish robust profitability across unseen market regimes.",
            paperObservationDays = days,
            paperClosedTrades = validOutcomes.size,
            paperRegimes = regimes,
            paperForwardStatus = if (paperReady) "MINIMUM PAPER EVIDENCE THRESHOLD MET • CONTINUE MONITORING" else "LONG-TERM PAPER VALIDATION INCOMPLETE",
            paperForwardDetail = paperDetail,
            futuresMarginStatus = "UNVERIFIED • FUTURES EXECUTION DISABLED",
            futuresMarginDetail = futuresDetail,
            monteCarloStatus = if (paperRisk?.available == true) "HISTORICAL BOOTSTRAP STRESS ONLY" else "WARM-UP • INSUFFICIENT PAPER HISTORY",
            monteCarloDetail = mcDetail,
            futuresExecutionAuthorized = false,
            liveExecutionAuthorized = false
        )
    }
}
