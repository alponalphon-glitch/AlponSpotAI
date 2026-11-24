package com.alpon.spotai.domain

/**
 * Connects the existing NEXORA intelligence engines into one auditable control plane.
 * This layer does not create synthetic data, train models on future candles, or place live orders.
 */
data class AutonomousModuleStatus(
    val name: String,
    val state: String,
    val detail: String,
    val safetyCritical: Boolean = false
)

data class AutonomousOrchestrationReport(
    val mode: String,
    val readyModules: Int,
    val totalModules: Int,
    val modules: List<AutonomousModuleStatus>,
    val blockers: List<String>,
    val summary: String
)

object AutonomousEngineOrchestrator {
    fun evaluate(
        integrity: DataIntegrityReport,
        analysts: List<AnalystOpinion>,
        redTeam: RedTeamReport,
        scenarioRisk: ScenarioRiskReport,
        marketShock: MarketShockReport,
        research: NexoraResearchReport,
        adaptive: NexoraAdaptiveReport,
        unifiedBrain: UnifiedBrainReport,
        external: com.alpon.spotai.data.ExternalMarketContext?,
        paperMemoryCount: Int,
        walkForward: WalkForwardResult?
    ): AutonomousOrchestrationReport {
        val modules = listOf(
            AutonomousModuleStatus("24/7 Market Intelligence", if (integrity.valid && integrity.checkedCandles > 0) "ACTIVE WHEN DATA PIPELINE RUNS" else "WAITING FOR VALID DATA", "${integrity.checkedCandles} validated candles; background uptime is managed separately."),
            AutonomousModuleStatus("Multi-Agent Intelligence", if (analysts.count { kotlin.math.abs(it.score) >= 20 } >= 3) "ACTIVE" else "LIMITED EVIDENCE", "${analysts.size} independent rule-based analyst opinions; not separate LLM agents."),
            AutonomousModuleStatus("Deep Learning", if (adaptive.deepLearning.acceptedAsEvidence) "HOLDOUT ACCEPTED" else if (adaptive.deepLearning.available) "SHADOW / REJECTED" else "WARMING UP", adaptive.deepLearning.note),
            AutonomousModuleStatus("Walk-Forward Probability Model", if (adaptive.probabilisticForecast?.acceptedAsEvidence == true) "OOS GATES PASSED / SHADOW" else if (adaptive.probabilisticForecast?.available == true) "OOS GATES FAILED / SHADOW" else "WARMING UP", adaptive.probabilisticForecast?.note ?: "Probabilistic model is unavailable."),
            AutonomousModuleStatus("Reinforcement Learning", if (adaptive.reinforcementLearning.acceptedAsEvidence) "HOLDOUT ACCEPTED" else if (adaptive.reinforcementLearning.available) "SHADOW / REJECTED" else "WARMING UP", adaptive.reinforcementLearning.note),
            AutonomousModuleStatus("Strategy Evolution", if (adaptive.strategyEvolution.promotedToShadow) "SHADOW PROMOTED" else "NOT PROMOTED", adaptive.strategyEvolution.note),
            AutonomousModuleStatus("Intelligent Risk Manager", if (scenarioRisk.sufficientHistory) "STRESS TEST AVAILABLE" else "RISK DATA INCOMPLETE", "Historical downside stress and stop coverage are checked; trade-entry limits remain authoritative.", true),
            AutonomousModuleStatus("Self-Criticism Engine", if (redTeam.robust) "COUNTER-CHECK PASSED" else "COUNTER-EVIDENCE FOUND", redTeam.counterArguments.take(3).joinToString("; ").ifBlank { "No counter-argument returned." }, true),
            AutonomousModuleStatus("Global Intelligence", if (external?.verificationReady == true) "VERIFIED FEED" else "UNVERIFIED / UNAVAILABLE", "External news and market context is only used when cross-source verification passes."),
            AutonomousModuleStatus("Autonomous Execution", "PAPER-ONLY CONTROL", "This orchestrator never submits live orders. Live execution requires a separate explicit user authorization and exchange-side controls.", true),
            AutonomousModuleStatus("Long-Term Memory", if (paperMemoryCount > 0) "PAPER HISTORY CONNECTED" else "COLLECTING HISTORY", "$paperMemoryCount paper outcomes supplied to the existing learning pipeline."),
            AutonomousModuleStatus("Self-Monitoring", if (marketShock.safeForNewEntry && integrity.valid) "NORMAL" else "GUARD / WARNING", (marketShock.findings + integrity.reasons).take(3).joinToString("; ").ifBlank { "Integrity and market-shock guards are clear." }, true),
            AutonomousModuleStatus("Unified Brain / Strategy Validation", if (unifiedBrain.blockers.isEmpty() && walkForward != null) "VALIDATED SAMPLE" else "WAIT / INCOMPLETE", "${unifiedBrain.evidenceCount} evidence modules; Walk-Forward ${if (walkForward == null) "not run" else "available"}.", true)
        )
        val blockers = buildList {
            if (!integrity.valid) add("Autonomous pipeline blocked: market data integrity failed.")
            if (!marketShock.safeForNewEntry) add("Autonomous pipeline guarded by Market Shock Circuit Breaker.")
            if (!redTeam.robust) add("Autonomous pipeline guarded by Self-Criticism / Red Team counter-evidence.")
            if (!scenarioRisk.sufficientHistory) add("Autonomous pipeline has insufficient historical downside-risk evidence.")
        }
        val ready = modules.count { it.state in setOf("ACTIVE", "HOLDOUT ACCEPTED", "SHADOW PROMOTED", "STRESS TEST AVAILABLE", "COUNTER-CHECK PASSED", "VERIFIED FEED", "PAPER HISTORY CONNECTED", "NORMAL", "VALIDATED SAMPLE", "OOS GATES PASSED / SHADOW") }
        return AutonomousOrchestrationReport(
            mode = "AUTONOMOUS_PAPER_FIRST",
            readyModules = ready,
            totalModules = modules.size,
            modules = modules,
            blockers = blockers,
            summary = "$ready/${modules.size} modules currently ready; execution remains paper-only in this orchestration layer."
        )
    }
}
