package com.alpon.spotai.domain

/** Internal audit for ten advanced capabilities. Never exposed as menu/navigation content. */
data class NexoraAdvancedTenReport(
    val allowed: Boolean,
    val blockers: List<String>,
    val audit: List<String>
)

/**
 * Coordinates existing evidence into ten additional hidden capabilities.
 * These are deterministic evidence gates, not ten independent LLMs or newly trained models.
 */
object NexoraAdvancedTenCore {
    fun evaluate(
        action: String?,
        analysts: List<AnalystOpinion>,
        unified: UnifiedBrainReport,
        ensemble: TechnicalEnsembleReport?,
        advanced: AdvancedBrainsReport,
        redTeam: RedTeamReport,
        scenario: ScenarioRiskReport,
        shock: MarketShockReport,
        integrity: DataIntegrityReport,
        adaptive: NexoraAdaptiveReport,
        portfolio: PortfolioExposureSnapshot?
    ): NexoraAdvancedTenReport {
        val proposed = action?.uppercase() ?: "WAIT"
        val directional = proposed == "BUY" || proposed == "SELL"
        val target = if (proposed == "BUY") "BULLISH" else "BEARISH"
        val blockers = mutableListOf<String>()
        val audit = mutableListOf<String>()

        // 1. Neural/technical ensemble: compare existing model and analyst outputs without counting them as independent truth.
        val directionalOpinions = analysts.filter { it.direction == "BULLISH" || it.direction == "BEARISH" }
        val aligned = directionalOpinions.count { it.direction == target }
        val opposing = directionalOpinions.size - aligned
        val ensembleBias = ensemble?.bias?.uppercase()
        audit += "01 Ensemble intelligence: analysts=${directionalOpinions.size}, aligned=$aligned, opposing=$opposing, technicalEnsemble=${ensembleBias ?: "UNAVAILABLE"}, unified=${unified.direction}; correlated evidence is not treated as independent votes."
        if (directional && directionalOpinions.size >= 3 && opposing >= 3 && opposing > aligned) {
            blockers += "Advanced ensemble found majority counter-direction evidence; WAIT."
        }
        if (directional && ensembleBias in setOf("BULLISH", "BEARISH") && ensembleBias != target && unified.direction != target) {
            blockers += "Technical ensemble and unified brain oppose the proposed direction; WAIT."
        }

        // 2. Liquidity intelligence: candle-derived volume profile only, not order-book depth.
        val liquidity = advanced.liquidity
        audit += "02 Liquidity intelligence: available=${liquidity.available}, levels=${liquidity.levels.size}, nearest=${liquidity.nearestLevel}; ${liquidity.note}"

        // 3. Cross-market intelligence: aligned benchmark returns only.
        val cross = advanced.correlation
        audit += "03 Cross-market intelligence: benchmark=${cross.benchmark}, available=${cross.available}, correlation=${cross.correlation}, alignedSamples=${cross.sampleCount}; descriptive correlation is not causation."

        // 4. Adversarial risk: combine existing independent downside, red-team and shock checks.
        val downsideSafe = proposed != "BUY" || (scenario.sufficientHistory && scenario.stopCoversHistoricalStress)
        val adversarialSafe = redTeam.robust && downsideSafe && shock.safeForNewEntry
        audit += "04 Adversarial risk: redTeam=${redTeam.robust}, downsideCheckApplicable=${proposed == "BUY"}, downsideHistory=${scenario.sufficientHistory}, stopCoversStress=${scenario.stopCoversHistoricalStress}, shockSafe=${shock.safeForNewEntry}."
        if (directional && !adversarialSafe) blockers += "Adversarial risk review did not pass all available downside and shock checks; WAIT."

        // 5. Uncertainty intelligence: missing or materially split evidence reduces permission to act.
        val evidenceCount = unified.evidenceCount
        val hasStrongConflict = unified.conflicts.isNotEmpty() || (directionalOpinions.size >= 3 && opposing > aligned && opposing >= 2)
        audit += "05 Uncertainty intelligence: unifiedEvidence=$evidenceCount, strongConflict=$hasStrongConflict, confidenceScore=${unified.confidence}; score is not a calibrated probability."
        if (directional && (evidenceCount < 3 || hasStrongConflict)) {
            blockers += "Uncertainty gate: evidence is insufficient or materially conflicting; WAIT."
        }

        // 6. Strategy evolution: experimental candidates stay in shadow until their own validation gate accepts them.
        val evolution = adaptive.strategyEvolution
        audit += "06 Strategy evolution: available=${evolution.available}, promotedToShadow=${evolution.promotedToShadow}, OOS trades=${evolution.outOfSampleTrades}; no live strategy promotion is performed."

        // 7. Market anomaly/manipulation watch: report observable candle anomalies; never assert manipulation without order-level proof.
        audit += "07 Market anomaly watch: severity=${shock.severity}, rangeRatio=${shock.latestRangeToMedian}, volumeRatio=${shock.latestVolumeToMedian}, findings=${shock.findings.size}; spoofing/manipulation cannot be confirmed from candles alone."
        if (directional && !shock.safeForNewEntry) blockers += "Market anomaly watch activated the shock circuit breaker; WAIT."

        // 8. Model health: stale/invalid data fails closed; unvalidated learned models remain shadow evidence.
        val dl = adaptive.deepLearning
        val rl = adaptive.reinforcementLearning
        audit += "08 Model health: dataIntegrity=${integrity.valid}, DL available=${dl.available}/holdout=${dl.acceptedAsEvidence}, RL available=${rl.available}/OOS=${rl.acceptedAsEvidence}; experimental models cannot authorize a trade alone."
        if (directional && !integrity.valid) blockers += "Model health rejected the decision because market data integrity failed."

        // 9. Portfolio intelligence: snapshot is paper-only; concentration is calculated only when valid inputs exist.
        val exposurePct = portfolio?.takeIf {
            it.equity.isFinite() && it.equity > 0.0 && it.investedNotional.isFinite() && it.investedNotional >= 0.0
        }?.let { it.investedNotional / it.equity * 100.0 }
        audit += "09 Portfolio intelligence: snapshot=${portfolio != null}, paper=${portfolio?.isPaperPortfolio}, openSymbols=${portfolio?.openSymbols?.size ?: 0}, investedPct=${exposurePct?.let { "%.2f".format(it) } ?: "UNAVAILABLE"}; per-position weights/correlation are not inferred when absent."
        if (directional && exposurePct != null && exposurePct >= 85.0) {
            blockers += "Portfolio exposure is at or above 85% of reported equity; no additional entry is allowed."
        }

        // 10. Decision audit: retain an internal trace of the exact evidence and gate outcome.
        audit += "10 Decision audit: proposed=$proposed, unified=${unified.direction}, score=${unified.directionalScore}, evidence=${unified.evidenceCount}, blockersBeforeFinal=${blockers.size}; trace is internal and not a persistent external audit ledger."

        return NexoraAdvancedTenReport(blockers.isEmpty(), blockers.distinct(), audit)
    }
}
