package com.alpon.spotai.domain

/**
 * Single aggregation point for every specialist report produced during one market snapshot.
 * This is an explainable deterministic control plane, not an external LLM or a collection of
 * independently trained agents. It never submits exchange orders.
 */
data class CentralBrainNode(
    val name: String,
    val status: String,
    val direction: String,
    val detail: String,
    val decisionInput: Boolean
)

data class PortfolioExposureSnapshot(
    val openSymbols: List<String>,
    val investedNotional: Double,
    val equity: Double,
    val isPaperPortfolio: Boolean = true
)

data class NexoraCentralAiBrainReport(
    val title: String,
    val marketMode: String,
    val finalAction: String,
    val signalConfidence: Int,
    val directionalScore: Double,
    val activeNodes: Int,
    val totalNodes: Int,
    val decisionInputs: Int,
    val blockers: List<String>,
    val nodes: List<CentralBrainNode>,
    val executionPolicy: String,
    val summary: String
)

object NexoraCentralAiBrainEngine {
    fun assemble(
        marketMode: String,
        action: String,
        ai: AIAnalysisReport?,
        analysts: List<AnalystOpinion>,
        integrity: DataIntegrityReport,
        unified: UnifiedBrainReport,
        technical: TechnicalQualityReport,
        research: NexoraResearchReport,
        adaptive: NexoraAdaptiveReport,
        advanced: AdvancedBrainsReport,
        strategic: CentralBrainStrategicReport,
        cognitive: CognitiveExtensionReport,
        global: GlobalAnalyticalCapabilitiesReport,
        redTeam: RedTeamReport,
        scenario: ScenarioRiskReport,
        shock: MarketShockReport,
        derivatives: FuturesDerivativesReport?,
        external: com.alpon.spotai.data.ExternalMarketContext?,
        walkForward: WalkForwardResult?,
        master: MasterControlBrainReport,
        blockers: List<String>,
        historicalMemory: HistoricalWaitReport?,
        portfolio: PortfolioExposureSnapshot?,
        paperAutoTradingEnabled: Boolean,
        riskPlan: AdaptiveRiskPlan?,
        paperRisk: MonteCarloPaperRiskReport? = null,
        validationGovernance: NexoraValidationGovernanceReport? = null
    ): NexoraCentralAiBrainReport {
        val nodes = listOf(
            CentralBrainNode("Market Data & Integrity", if (integrity.valid) "VERIFIED" else "BLOCKED", "NEUTRAL", "${integrity.checkedCandles} closed candles; ${integrity.timeframeCount} timeframe feeds", true),
            CentralBrainNode("AI Signal Generator", ai?.action ?: "UNAVAILABLE", ai?.action ?: "NEUTRAL", ai?.let { "Confidence score ${it.confidence}; directional score ${"%+.1f".format(it.directionalScore)}; ${it.components.size} components." } ?: "No AI signal report supplied.", true),
            CentralBrainNode("Independent Analyst Council", if (analysts.size >= 3) "ACTIVE" else "LIMITED", "NEUTRAL", "${analysts.size} independent rule-based analyst opinions; they are not separate LLM agents.", true),
            CentralBrainNode("Technical Analysis", if (technical.available) "ACTIVE" else "UNAVAILABLE", technical.direction, "Score ${technical.score}; ${technical.evidence.size} evidence items", true),
            CentralBrainNode("Multi-timeframe / Unified Evidence", if (unified.evidenceCount >= 5) "ACTIVE" else "LIMITED", unified.direction, "${unified.evidenceCount} evidence modules; ${unified.conflicts.size} conflicts", true),
            CentralBrainNode("Quant Research", if (research.available) "ACTIVE" else "WARM-UP", if (research.directionalScore > 10) "BULLISH" else if (research.directionalScore < -10) "BEARISH" else "NEUTRAL", research.note, true),
            CentralBrainNode("Deep Learning", if (adaptive.deepLearning.acceptedAsEvidence) "HOLDOUT ACCEPTED" else if (adaptive.deepLearning.available) "SHADOW ONLY" else "WARM-UP", "NEUTRAL", adaptive.deepLearning.note, adaptive.deepLearning.acceptedAsEvidence),
            CentralBrainNode("Reinforcement Learning", if (adaptive.reinforcementLearning.acceptedAsEvidence) "HOLDOUT ACCEPTED" else if (adaptive.reinforcementLearning.available) "SHADOW ONLY" else "WARM-UP", "NEUTRAL", adaptive.reinforcementLearning.note, adaptive.reinforcementLearning.acceptedAsEvidence),
            CentralBrainNode("Strategy Evolution", if (adaptive.strategyEvolution.promotedToShadow) "SHADOW PROMOTED" else "NOT PROMOTED", "NEUTRAL", adaptive.strategyEvolution.note, adaptive.strategyEvolution.promotedToShadow),
            CentralBrainNode("Liquidity / Market Memory", if (advanced.liquidity.available || advanced.marketMemory.available) "PROXY AVAILABLE" else "WARM-UP", "NEUTRAL", "${advanced.liquidity.note} ${advanced.marketMemory.note}", false),
            CentralBrainNode("Execution Quality", advanced.execution.grade, "NEUTRAL", advanced.execution.warnings.joinToString(" "), true),
            CentralBrainNode("Strategic Intelligence", "CONNECTED", "NEUTRAL", "Liquidity ${strategic.liquidityIntelligence}; event ${strategic.eventImpact}; execution ${strategic.executionReality}", false),
            CentralBrainNode("Cognitive Extensions", if (cognitive.blockers.isEmpty()) "REVIEWED" else "GUARDED", "NEUTRAL", cognitive.summary, true),
            CentralBrainNode("Global Analytical Research", global.scientificGate, "NEUTRAL", "${global.horizonDetail} ${global.thesisBreakerDetail}", false),
            CentralBrainNode("Red Team / Counter-analysis", if (redTeam.robust) "PASSED" else "COUNTER-EVIDENCE", "NEUTRAL", redTeam.counterArguments.joinToString("; "), true),
            CentralBrainNode("Scenario Risk", if (scenario.sufficientHistory) "STRESS TEST AVAILABLE" else "INSUFFICIENT HISTORY", "NEUTRAL", scenario.explanation, true),
            CentralBrainNode("Market Shock Guard", if (shock.safeForNewEntry) "CLEAR" else "BLOCKED", "NEUTRAL", shock.findings.joinToString("; "), true),
            CentralBrainNode("Futures Derivatives", if (derivatives?.available == true && derivatives.blockers.isEmpty()) "AVAILABLE" else "GUARDED / UNAVAILABLE", derivatives?.directionalBias ?: "NEUTRAL", derivatives?.observations?.joinToString("; ").orEmpty(), marketMode == "FUTURES"),
            CentralBrainNode("External Market / News", if (external?.verificationReady == true) "VERIFIED" else "UNVERIFIED / MISSING", "NEUTRAL", "External data is admitted only after source verification.", true),
            CentralBrainNode("Walk-Forward Validation", if (walkForward != null) "RESULT AVAILABLE" else "NOT RUN", "NEUTRAL", LearningValidationEngine.assess(walkForward).status, true),
            CentralBrainNode("Historical Memory / Intelligent Waiting", historicalMemory?.status ?: "LOADING / UNAVAILABLE", "NEUTRAL", historicalMemory?.rationale ?: "No historical candle snapshot loaded yet.", historicalMemory != null),
            CentralBrainNode("Portfolio Intelligence", if (portfolio != null && portfolio.equity.isFinite() && portfolio.equity > 0.0) "PAPER SNAPSHOT CONNECTED" else "NOT CONNECTED", "NEUTRAL", portfolio?.let { "${it.openSymbols.size} open paper position(s): ${it.openSymbols.joinToString().ifBlank { "none" }}; invested ${"%.2f".format(it.investedNotional)} / equity ${"%.2f".format(it.equity)}. Not a live exchange account balance." } ?: "No portfolio snapshot supplied.", marketMode == "BINANCE_SPOT" || marketMode == "SPOT"),
            CentralBrainNode("Adaptive Risk & Money Management", if (riskPlan?.valid == true) "RISK PLAN VERIFIED" else "RISK PLAN NOT READY", "NEUTRAL", riskPlan?.let { "Adaptive stop ${"%.2f".format(it.stopDistancePercent)}%; ATR multiplier ${"%.2f".format(it.atrMultiplier)}; risk budget ${"%.2f".format(it.riskBudget)}; max notional ${"%.2f".format(it.maxNotional)}; target ${"%.2f".format(it.takeProfitPrice)}. ${it.rationale}" } ?: "No approved adaptive risk plan; a BUY entry must remain blocked.", true),
            CentralBrainNode(
                "Monte Carlo Paper Risk Lab",
                if (paperRisk?.available == true) "BOOTSTRAP STRESS AVAILABLE" else "WARM-UP",
                "NEUTRAL",
                paperRisk?.let {
                    "${it.sampleCount} closed outcomes; ${it.paths} paths over ${it.horizonTrades} trades; p05 ending return ${it.p05EndingReturnPercent?.let { v -> "%.2f%%".format(v) } ?: "—"}; median ${it.medianEndingReturnPercent?.let { v -> "%.2f%%".format(v) } ?: "—"}; p95 max drawdown ${it.p95MaximumDrawdownPercent?.let { v -> "%.2f%%".format(v) } ?: "—"}; resampled paths with ≥10% drawdown ${it.resampledDrawdown10PercentFrequency?.let { v -> "%.1f%%".format(v) } ?: "—"}. ${it.note}"
                } ?: "Need at least 20 closed paper trades. This diagnostic does not affect BUY/SELL decisions.",
                false
            ),
            CentralBrainNode("DL Evidence Governance", validationGovernance?.deepLearningStatus ?: "GOVERNANCE NOT CONNECTED", "NEUTRAL", validationGovernance?.deepLearningDetail ?: "No maturity report supplied.", false),
            CentralBrainNode("RL Evidence Governance", validationGovernance?.reinforcementLearningStatus ?: "GOVERNANCE NOT CONNECTED", "NEUTRAL", validationGovernance?.reinforcementLearningDetail ?: "No maturity report supplied.", false),
            CentralBrainNode("Long-Term Paper Validation", validationGovernance?.paperForwardStatus ?: "GOVERNANCE NOT CONNECTED", "NEUTRAL", validationGovernance?.paperForwardDetail ?: "No maturity report supplied.", true),
            CentralBrainNode("Futures Margin / Liquidation Gate", validationGovernance?.futuresMarginStatus ?: "UNVERIFIED • FUTURES EXECUTION DISABLED", "NEUTRAL", validationGovernance?.futuresMarginDetail ?: "Verified exchange liquidation and account margin data are required.", marketMode == "FUTURES"),
            CentralBrainNode("Monte Carlo Evidence Guard", validationGovernance?.monteCarloStatus ?: "GOVERNANCE NOT CONNECTED", "NEUTRAL", validationGovernance?.monteCarloDetail ?: "No maturity report supplied.", false),
            CentralBrainNode("Paper Trading Executor", if (marketMode == "BINANCE_SPOT" && paperAutoTradingEnabled) "AUTO PAPER ENABLED" else "MANUAL / ANALYSIS ONLY", "NEUTRAL", "Only approved Spot BUY decisions can create simulated positions; one attempt per confirmed candle; no exchange order endpoint is called.", false),
            CentralBrainNode("Master Control / Final Gate", master.gateStatus, action, master.centralSummary, true)
        )
        val active = nodes.count { node ->
            node.status !in setOf("UNAVAILABLE", "WARM-UP", "LIMITED", "NOT RUN", "NOT PROMOTED", "SHADOW ONLY", "UNVERIFIED / MISSING", "GUARDED / UNAVAILABLE", "INSUFFICIENT HISTORY", "BLOCKED", "RISK PLAN NOT READY", "EXPERIMENTAL", "INCOMPLETE", "UNVERIFIED")
        }
        val inputs = nodes.count { it.decisionInput }
        val confidence = ai?.confidence?.coerceIn(0, 100) ?: unified.confidence.coerceIn(0, 100)
        val policy = when (marketMode) {
            "FUTURES" -> "ANALYSIS AND SIGNALS ONLY • automatic Futures execution disabled until verified leverage, margin, liquidation and position-size safeguards are configured."
            "FOREX" -> "ANALYSIS AND SIGNALS ONLY • no broker order execution is connected."
            else -> "PAPER TRADING ONLY • auto mode ${if (paperAutoTradingEnabled) "ON" else "OFF"} • no Binance live order endpoint is called by this brain."
        }
        return NexoraCentralAiBrainReport(
            title = "NEXORA CENTRAL AI BRAIN",
            marketMode = marketMode,
            finalAction = action,
            signalConfidence = confidence,
            directionalScore = unified.directionalScore,
            activeNodes = active,
            totalNodes = nodes.size,
            decisionInputs = inputs,
            blockers = blockers.distinct(),
            nodes = nodes,
            executionPolicy = policy,
            summary = "One central snapshot reviewed ${nodes.size} connected intelligence modules; $inputs are marked as decision inputs. Final result: $action. Readiness counts available evidence, not trading accuracy or guaranteed profit."
        )
    }
}
