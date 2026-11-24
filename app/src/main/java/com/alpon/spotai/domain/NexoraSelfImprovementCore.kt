package com.alpon.spotai.domain

/** Hidden seven-capability self-improvement report; internal audit only, never a menu model. */
data class NexoraSelfImprovementReport(
    val allowed: Boolean,
    val blockers: List<String>,
    val audit: List<String>
)

/**
 * Evidence-bounded self-review layer. It uses closed Paper Trading outcomes and existing
 * walk-forward/data-quality reports. It does not claim autonomous consciousness or
 * replay data that the application has not persisted.
 */
object NexoraSelfImprovementCore {
    fun evaluate(
        proposedAction: String?,
        regime: MarketRegimeReport?,
        paperOutcomes: List<AdaptiveTradeOutcome>,
        advanced: AdvancedBrainsReport,
        walkForward: WalkForwardResult?,
        adaptive: NexoraAdaptiveReport,
        integrity: DataIntegrityReport,
        redTeam: RedTeamReport,
        shock: MarketShockReport
    ): NexoraSelfImprovementReport {
        val action = proposedAction?.uppercase() ?: "WAIT"
        val directional = action == "BUY" || action == "SELL"
        val blockers = mutableListOf<String>()
        val audit = mutableListOf<String>()
        val validOutcomes = paperOutcomes.filter { it.timestamp > 0L && it.pnlPercent.isFinite() }
            .sortedByDescending { it.timestamp }
        val regimeKey = regime?.regime?.uppercase()
        val regimeHistory = validOutcomes.filter { regimeKey == null || it.regime.equals(regimeKey, true) }.take(100)

        // 1. Prediction vs reality: realized paper returns are the only available outcome proxy.
        val avg = regimeHistory.takeIf { it.isNotEmpty() }?.map { it.pnlPercent }?.average()
        val wins = regimeHistory.count { it.pnlPercent > 0.0 }
        val winRate = regimeHistory.takeIf { it.isNotEmpty() }?.let { wins * 100.0 / it.size }
        audit += "01 Prediction vs Reality: closedPaper=${regimeHistory.size}, regime=${regimeKey ?: "ALL/UNKNOWN"}, winRate=${winRate?.let { "%.2f".format(it) } ?: "UNAVAILABLE"}%, averageRealizedReturn=${avg?.let { "%.4f".format(it) } ?: "UNAVAILABLE"}%; no directional forecast accuracy is inferred because trade-direction labels are not stored in this outcome record."
        if (directional && regimeHistory.size >= 15 && avg != null && avg <= 0.0 && winRate != null && winRate < 40.0) {
            blockers += "Prediction-vs-reality review: recent closed Paper Trading outcomes in this regime are materially weak; WAIT for review."
        }

        // 2. Failure autopsy: categorize observable loss patterns without inventing causal explanations.
        val losses = regimeHistory.filter { it.pnlPercent < 0.0 }
        val lossAverage = losses.takeIf { it.isNotEmpty() }?.map { it.pnlPercent }?.average()
        audit += "02 Failure autopsy: losingPaperTrades=${losses.size}, meanLoss=${lossAverage?.let { "%.4f".format(it) } ?: "UNAVAILABLE"}%; causal attribution requires stored entry/exit, fees, slippage and market snapshot, so no unsupported cause is assigned."

        // 3. Knowledge graph: connect only measured correlation and available liquidity observations.
        audit += "03 Knowledge graph: benchmark=${advanced.correlation.benchmark}, correlation=${advanced.correlation.correlation}, correlationSamples=${advanced.correlation.sampleCount}, liquidityLevels=${advanced.liquidity.levels.size}; relationships are descriptive, not causal."

        // 4. Regime memory: maintain separate outcome summaries by observed regime label.
        val regimeSummaries = validOutcomes.groupBy { it.regime.trim().uppercase().ifBlank { "UNKNOWN" } }
            .mapValues { (_, rows) -> "n=${rows.size},avg=${"%.4f".format(rows.map { it.pnlPercent }.average())}%" }
        audit += "04 Regime memory: ${regimeSummaries.entries.sortedBy { it.key }.joinToString("; ").ifBlank { "no closed paper history" }}."

        // 5. Overfitting detector: compare training reliability against truly unseen test folds.
        val wf = walkForward
        val reliableTrainingFolds = wf?.folds?.count { it.trainingReliable } ?: 0
        val profitableTestFolds = wf?.profitableTestFolds ?: 0
        val totalTestFolds = wf?.totalTestFolds ?: 0
        val oosTrades = wf?.outOfSampleTrades ?: 0
        val oosReturn = wf?.averageOutOfSampleReturnPercent
        val overfitWarning = totalTestFolds >= 3 && reliableTrainingFolds >= 3 && oosTrades >= 10 &&
            (profitableTestFolds == 0 || (oosReturn != null && oosReturn.isFinite() && oosReturn <= 0.0))
        audit += "05 Overfitting detector: reliableTrainingFolds=$reliableTrainingFolds, profitableOOSFolds=$profitableTestFolds/$totalTestFolds, OOSTrades=$oosTrades, averageOOSReturn=${oosReturn?.let { "%.4f".format(it) } ?: "UNAVAILABLE"}, warning=$overfitWarning."
        if (directional && overfitWarning) blockers += "Overfitting detector: strategy training evidence is not confirmed by unseen walk-forward tests; WAIT."

        // 6. Decision replay: explicitly report whether immutable decision snapshots are available.
        audit += "06 Decision replay: historical outcome records=${validOutcomes.size}; full deterministic replay is UNAVAILABLE without persisted timestamped market/feature/model/config snapshots."

        // 7. Intelligence supervisor: fail closed on broken data, shock state or unresolved counter-evidence.
        val supervisorHealthy = integrity.valid && shock.safeForNewEntry && redTeam.robust
        audit += "07 Intelligence supervisor: dataIntegrity=${integrity.valid}, shockSafe=${shock.safeForNewEntry}, redTeamRobust=${redTeam.robust}, DLholdout=${adaptive.deepLearning.acceptedAsEvidence}, RLOOS=${adaptive.reinforcementLearning.acceptedAsEvidence}, healthy=$supervisorHealthy."
        if (directional && !supervisorHealthy) blockers += "Intelligence supervisor: one or more critical data, shock or counter-evidence checks failed; WAIT."

        return NexoraSelfImprovementReport(blockers.isEmpty(), blockers.distinct(), audit)
    }
}
