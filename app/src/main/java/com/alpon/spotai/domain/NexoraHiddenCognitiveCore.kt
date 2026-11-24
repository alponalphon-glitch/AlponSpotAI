package com.alpon.spotai.domain

/** Internal-only report. It is consumed by the central decision gate and is not a UI/menu model. */
data class HiddenCognitiveCoreReport(
    val allowed: Boolean,
    val blockers: List<String>,
    val internalAudit: List<String>
)

/**
 * NEXORA's hidden cognitive orchestration layer.
 * It coordinates existing evidence-producing engines; it does not pretend that every
 * capability is a separately trained model or that research outputs guarantee returns.
 * All learning/research outputs remain shadow evidence and cannot place exchange orders.
 */
object NexoraHiddenCognitiveCore {
    fun evaluate(
        proposedAction: String?,
        regime: MarketRegimeReport?,
        mtf: MultiTimeframeSignal?,
        unified: UnifiedBrainReport,
        integrity: DataIntegrityReport,
        redTeam: RedTeamReport,
        shock: MarketShockReport,
        paperOutcomes: List<AdaptiveTradeOutcome>,
        adaptive: NexoraAdaptiveReport,
        learning: AdaptiveLearningDecision?,
        advanced: AdvancedBrainsReport,
        walkForward: WalkForwardResult?
    ): HiddenCognitiveCoreReport {
        val action = proposedAction?.uppercase() ?: "WAIT"
        val directional = action == "BUY" || action == "SELL"
        val target = if (action == "BUY") "BULLISH" else "BEARISH"
        val blockers = mutableListOf<String>()
        val audit = mutableListOf<String>()

        // 1. Cognitive reasoning: compare the proposed direction with the unified brain and MTF evidence.
        val readings = mtf?.readings.orEmpty().filter { it.direction in setOf("BULLISH", "BEARISH") }
        val aligned = readings.count { it.direction == target }
        val opposing = readings.size - aligned
        audit += "01 Cognitive reasoning: unifiedScore=${unified.directionalScore}; MTF aligned=$aligned opposing=$opposing sample=${readings.size}."
        if (directional && readings.size >= 7 && opposing >= 5 && opposing > aligned) {
            blockers += "Cognitive reasoning found strong multi-timeframe counter-evidence; WAIT."
        }

        // 2. Long-term experience memory: only closed Paper Trading outcomes supplied by the app are used.
        val regimeKey = regime?.regime?.uppercase()
        val history = paperOutcomes.filter { it.pnlPercent.isFinite() && it.timestamp > 0L &&
            (regimeKey == null || it.regime.equals(regimeKey, ignoreCase = true))
        }.sortedByDescending { it.timestamp }.take(100)
        audit += "02 Experience memory: closed paper outcomes=${history.size}; regime=${regimeKey ?: "UNKNOWN"}; no live-account history is assumed."
        if (directional && history.size >= 12) {
            val wins = history.count { it.pnlPercent > 0.0 }
            val winRate = wins * 100.0 / history.size
            val average = history.map { it.pnlPercent }.average()
            if (winRate < 40.0 && average <= 0.0) blockers += "Experience memory found weak recent paper performance in this regime; pause and review."
        }

        // 3. Deep learning: use the existing chronological holdout model only when accepted by its own gate.
        val dl = adaptive.deepLearning
        audit += "03 Deep learning: available=${dl.available}; holdoutAccepted=${dl.acceptedAsEvidence}; train=${dl.trainSamples}; test=${dl.testSamples}."
        // DL is evidence only; absence does not fabricate a signal or independently authorize a trade.

        // 4. Reinforcement learning: OOS metrics are shadow evidence, never execution authority.
        val rl = adaptive.reinforcementLearning
        audit += "04 Reinforcement learning: available=${rl.available}; OOS accepted=${rl.acceptedAsEvidence}; trades=${rl.outOfSampleTrades}; drawdown=${rl.outOfSampleDrawdownPct}."

        // 5. Meta-learning: strategy evolution may be considered only after its own OOS promotion gate.
        val evolution = adaptive.strategyEvolution
        audit += "05 Meta-learning: strategy evolution available=${evolution.available}; promotedToShadow=${evolution.promotedToShadow}; OOS trades=${evolution.outOfSampleTrades}."

        // 6. Market-regime intelligence: classify context; unknown regime is explicitly reported.
        audit += "06 Market regime: ${regime?.regime ?: "UNAVAILABLE"}; trend=${regime?.trend ?: "UNKNOWN"}; volatility=${regime?.volatility ?: "UNKNOWN"}."
        if (directional && regime == null) blockers += "Market regime is unavailable; central brain cannot verify the market state."

        // 7. Multi-agent debate: independent analyst consensus is challenged by red-team evidence.
        audit += "07 Multi-agent debate: redTeamRobust=${redTeam.robust}; counterArguments=${redTeam.counterArguments.size}; unified direction=${unified.direction}."
        if (directional && !redTeam.robust) blockers += "Multi-agent debate has unresolved counter-evidence; WAIT."

        // 8. Self-criticism: explicitly search for disconfirming evidence before accepting a direction.
        audit += "08 Self-criticism: ${if (redTeam.counterArguments.isEmpty()) "no counter-arguments returned" else redTeam.counterArguments.joinToString(" | ")}."
        if (directional && redTeam.counterArguments.isNotEmpty() && !redTeam.robust) {
            blockers += "Self-criticism rejected the proposal because counter-evidence remains unresolved."
        }

        // 9. Predictive analytics: calibrated walk-forward forecast is reported only as a research estimate.
        val forecast = adaptive.probabilisticForecast ?: rl.probabilisticForecast
        audit += "09 Predictive analytics: available=${forecast?.available == true}; accepted=${forecast?.acceptedAsEvidence == true}; folds=${forecast?.folds ?: 0}; probability is not a guarantee."

        // 10. Adaptive risk: weak/blocked learning feedback can never increase baseline risk.
        audit += "10 Adaptive risk: learningAllowed=${learning?.allowed}; riskMultiplier=${learning?.riskMultiplier}; sample=${learning?.sampleCount ?: 0}."
        if (directional && learning?.allowed == false) blockers += "Adaptive risk intelligence blocked this setup based on paper-trading feedback."

        // 11. Continuous validation: require actual walk-forward test evidence; no fabricated validation status.
        val wf = walkForward
        audit += "11 Continuous validation: folds=${wf?.totalTestFolds ?: 0}; OOS trades=${wf?.outOfSampleTrades ?: 0}; profitable folds=${wf?.profitableTestFolds ?: 0}."
        if (directional && (wf == null || wf.totalTestFolds <= 0 || wf.outOfSampleTrades <= 0)) {
            blockers += "Continuous validation has no usable out-of-sample trade evidence; WAIT."
        }

        // 12. Autonomous research: use strategy doctor and historical market memory as bounded research tools.
        audit += "12 Autonomous research: strategyDoctor=${advanced.strategyDoctor.status}; paperTrades=${advanced.strategyDoctor.sampleTrades}; marketMemory=${advanced.marketMemory.available}; liquidityProxy=${advanced.liquidity.available}."

        // Cross-cutting fail-closed data/safety checks.
        if (directional && !integrity.valid) blockers += "Hidden cognitive core: market data integrity failed."
        if (directional && !shock.safeForNewEntry) blockers += "Hidden cognitive core: market-shock circuit breaker is active."
        audit += "Safety boundary: dataIntegrity=${integrity.valid}; shockSafe=${shock.safeForNewEntry}; exchange order execution is not performed by this core."

        return HiddenCognitiveCoreReport(blockers.isEmpty(), blockers.distinct(), audit)
    }
}
