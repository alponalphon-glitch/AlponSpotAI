package com.alpon.spotai.domain

/** Final central-brain diagnostics. This is a snapshot layer, not a persistent live monitor. */
data class CentralBrainFinalReport(
    val decisionTrace: String,
    val decisionTraceDetail: String,
    val liveMonitor: String,
    val liveMonitorDetail: String,
    val conflictResolver: String,
    val conflictResolverDetail: String,
    val evidenceLearning: String,
    val evidenceLearningDetail: String
)

object CentralBrainFinalEngine {
    fun evaluate(
        finalAction: String,
        unified: UnifiedBrainReport,
        integrity: DataIntegrityReport,
        blockers: List<String>,
        analysts: List<AnalystOpinion>,
        paperOutcomes: List<AdaptiveTradeOutcome>,
        regime: String
    ): CentralBrainFinalReport {
        val trace = "FINAL $finalAction • ${unified.direction} • ${unified.evidenceCount} evidence modules • ${unified.conflicts.size} conflicts • ${blockers.size} blocker(s)"
        val traceDetail = buildString {
            append("Unified direction: ").append(unified.direction)
            append("; directional score: ").append(unified.directionalScore)
            append("; evidence modules: ").append(unified.evidenceCount)
            append("; integrity: ").append(if (integrity.valid) "VALID" else "INVALID")
            append("; blockers: ").append(blockers.ifEmpty { listOf("none recorded") }.joinToString(" | "))
        }
        val monitorStatus = when {
            !integrity.valid -> "MONITOR ALERT — DATA INTEGRITY"
            blockers.isNotEmpty() -> "MONITOR ALERT — BLOCKED CONDITIONS"
            finalAction == "WAIT" -> "MONITOR SNAPSHOT — WAIT"
            else -> "MONITOR SNAPSHOT — CONDITIONS CURRENTLY CLEAR"
        }
        val monitorDetail = "One-time decision snapshot only. This layer does not run continuously or auto-cancel signals after new candles arrive; refresh the analysis to re-evaluate."
        val directional = analysts.filter { it.direction in setOf("BUY", "SELL", "BULLISH", "BEARISH") }
        val buyVotes = directional.count { it.direction in setOf("BUY", "BULLISH") }
        val sellVotes = directional.count { it.direction in setOf("SELL", "BEARISH") }
        val conflictStatus = when {
            directional.isEmpty() -> "NO ANALYST EVIDENCE"
            buyVotes > 0 && sellVotes > 0 -> "CONFLICT — WAIT PREFERRED"
            buyVotes == sellVotes -> "NO CLEAR DIRECTION"
            else -> "DIRECTIONAL PANEL — RULE-BASED"
        }
        val conflictDetail = "Directional analyst panel: $buyVotes bullish/BUY vs $sellVotes bearish/SELL across ${directional.size} directional opinions. This is a descriptive conflict check; it does not override the established decision gates."
        val sample = paperOutcomes.filter { it.regime.equals(regime, true) && it.pnlPercent.isFinite() }
        val wins = sample.count { it.pnlPercent > 0.0 }
        val winRate = if (sample.isNotEmpty()) wins * 100.0 / sample.size else null
        val avg = sample.takeIf { it.isNotEmpty() }?.map { it.pnlPercent }?.average()
        val learningStatus = when {
            sample.size < 8 -> "EVIDENCE WARM-UP (${sample.size}/8)"
            sample.size < 12 -> "LOW SAMPLE — RISK NOT INCREASED"
            else -> "PAPER EVIDENCE SUMMARY — NOT A PROMOTION"
        }
        val learningDetail = "Closed paper outcomes for regime $regime: n=${sample.size}; win rate ${winRate?.let { "%.1f%%".format(it) } ?: "n/a"}; average PnL ${avg?.let { "%.3f%%".format(it) } ?: "n/a"}. Metrics are descriptive only; no strategy promotion or risk increase is performed here."
        return CentralBrainFinalReport(trace, traceDetail, monitorStatus, monitorDetail, conflictStatus, conflictDetail, learningStatus, learningDetail)
    }
}
