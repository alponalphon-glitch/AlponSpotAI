package com.alpon.spotai.domain

/** Compact central-brain synthesis of capabilities that already exist plus explicitly unavailable data. */
data class CentralBrainExtensionReport(
    val marketMemoryStatus: String,
    val marketMemoryDetail: String,
    val setupFingerprint: String,
    val setupContext: String,
    val multiSourceStatus: String,
    val multiSourceDetail: String,
    val portfolioStatus: String,
    val portfolioDetail: String,
    val strategyLabStatus: String,
    val strategyLabDetail: String,
    val firewallVerdict: String,
    val firewallDetail: String
)

object CentralBrainExtensionEngine {
    fun evaluate(
        technicalQuality: TechnicalQualityReport?,
        regime: MarketRegimeReport?,
        mtf: MultiTimeframeSignal?,
        marketMemory: MarketMemoryReport?,
        adaptive: NexoraAdaptiveReport?,
        external: com.alpon.spotai.data.ExternalMarketContext?,
        integrity: DataIntegrityReport?,
        finalAction: String,
        blockers: List<String>
    ): CentralBrainExtensionReport {
        val memory = marketMemory
        val memoryStatus = if (memory?.available == true) "ACTIVE ANALOG" else "WARM-UP / NO MATCH"
        val memoryDetail = memory?.note ?: "Market Memory evidence is unavailable. No analog result is inferred."
        val tq = technicalQuality
        val regimeLabel = regime?.regime ?: "REGIME UNKNOWN"
        val mtfLabel = mtf?.alignment ?: "MTF UNAVAILABLE"
        val fingerprint = listOf(
            tq?.breakout?.takeIf { it.isNotBlank() } ?: "BREAKOUT UNKNOWN",
            tq?.candlePattern?.takeIf { it.isNotBlank() } ?: "PATTERN UNKNOWN",
            tq?.direction ?: "DIRECTION UNKNOWN",
            regimeLabel
        ).joinToString(" | ")
        val setupContext = "Setup fingerprint is descriptive only; outcome history is not attributed to this setup yet. $mtfLabel"
        val sourceReady = external?.verificationReady == true
        val sourceStatus = if (sourceReady && integrity?.valid == true) "CROSS-CHECK READY" else "PARTIAL / UNVERIFIED"
        val sourceDetail = buildString {
            append("External context: ").append(if (sourceReady) "verified" else "not verified")
            append("; candle integrity: ").append(if (integrity?.valid == true) "valid" else "not confirmed")
            external?.let { if (it.newsConflict) append("; news conflict flagged") }
        }
        val strategy = adaptive?.strategyEvolution
        val labStatus = when {
            strategy?.promotedToShadow == true -> "SHADOW CANDIDATE"
            strategy?.available == true -> "CANDIDATE NOT PROMOTED"
            else -> "WARM-UP"
        }
        val labDetail = strategy?.note ?: "Strategy lab output unavailable; no promotion is assumed."
        val firewall = if (finalAction in setOf("BUY", "SELL") && blockers.isEmpty()) "PASS — ALL CURRENT GATES CLEAR" else "BLOCK / WAIT"
        val firewallDetail = if (blockers.isEmpty()) "No current blockers recorded; decision remains limited to the existing rule gates." else blockers.take(4).joinToString(" • ")
        return CentralBrainExtensionReport(
            marketMemoryStatus = memoryStatus,
            marketMemoryDetail = memoryDetail,
            setupFingerprint = fingerprint,
            setupContext = setupContext,
            multiSourceStatus = sourceStatus,
            multiSourceDetail = sourceDetail,
            portfolioStatus = "NOT CONNECTED",
            portfolioDetail = "Portfolio holdings and cross-position exposure are not supplied to this analysis; portfolio risk is not calculated.",
            strategyLabStatus = labStatus,
            strategyLabDetail = labDetail,
            firewallVerdict = firewall,
            firewallDetail = firewallDetail
        )
    }
}


/** Six advanced validation capabilities integrated into the central brain.
 * Deterministic diagnostics only: no predictive accuracy is claimed without stored predictions/outcomes.
 */
data class CentralBrainAdvancedReport(
    val regimeTransition: String, val regimeTransitionDetail: String,
    val uncertainty: String, val uncertaintyDetail: String,
    val adversarial: String, val adversarialDetail: String,
    val counterfactual: String, val counterfactualDetail: String,
    val calibration: String, val calibrationDetail: String,
    val modelDrift: String, val modelDriftDetail: String
)

object CentralBrainAdvancedEngine {
    fun evaluate(candles: List<com.alpon.spotai.data.Candle>, unified: UnifiedBrainReport, adaptive: NexoraAdaptiveReport?): CentralBrainAdvancedReport {
        val c = candles.filter { it.closeTime > 0 && it.closeTime <= System.currentTimeMillis() && it.close > 0.0 && it.open > 0.0 && it.high >= it.low }.sortedBy { it.time }
        fun returns(data: List<com.alpon.spotai.data.Candle>) = data.zipWithNext().map { (a,b) -> b.close/a.close-1.0 }.filter { it.isFinite() }
        fun vol(data: List<com.alpon.spotai.data.Candle>): Double? { val r=returns(data); if(r.size<12) return null; val m=r.average(); return kotlin.math.sqrt(r.sumOf{(it-m)*(it-m)}/r.size) }
        val recent=c.takeLast(24); val prior=c.dropLast(24).takeLast(24)
        val recentVol=vol(recent); val priorVol=vol(prior)
        val recentMove=if(recent.size>=12) recent.last().close/recent.first().close-1.0 else null
        val priorMove=if(prior.size>=12) prior.last().close/prior.first().close-1.0 else null
        val transition = when { recentVol==null || priorVol==null || recentMove==null || priorMove==null -> "WARM-UP"; kotlin.math.abs(recentMove-priorMove)>.025 || recentVol/priorVol.coerceAtLeast(1e-9)>1.8 || recentVol/priorVol.coerceAtLeast(1e-9)<.55 -> "TRANSITION RISK"; else -> "NO STRONG TRANSITION FLAG" }
        val transitionDetail = "Recent 24-candle move ${recentMove?.let{"%.2f%%".format(it*100)} ?: "n/a"}; volatility change ${if(recentVol!=null&&priorVol!=null) "%.2fx".format(recentVol/priorVol.coerceAtLeast(1e-9)) else "n/a"}. Heuristic change detection, not a forecast."
        val uncertaintyScore=(unified.blockers.size*12+unified.conflicts.size*18+(10-unified.evidenceCount.coerceAtMost(10))*5).coerceIn(0,100)
        val uncertainty=when { unified.evidenceCount<5 || unified.conflicts.isNotEmpty() -> "HIGH / HOLD"; uncertaintyScore>=35 -> "ELEVATED"; else -> "MODERATE" }
        val uncertaintyDetail="${unified.evidenceCount} evidence modules; ${unified.conflicts.size} conflicts; ${unified.blockers.size} blockers. Score is a rule-based uncertainty indicator, not probability."
        val adversarialFlags=mutableListOf<String>()
        if(recent.size<12) adversarialFlags+="short sample"
        if(recentVol!=null && recentVol>.025) adversarialFlags+="high volatility"
        if(unified.conflicts.isNotEmpty()) adversarialFlags+="cross-module conflict"
        if(unified.blockers.isNotEmpty()) adversarialFlags+="decision blockers"
        val adversarial=if(adversarialFlags.isEmpty()) "NO BASIC STRESS FLAGS" else "CAUTION • ${adversarialFlags.size} flags"
        val adversarialDetail=adversarialFlags.ifEmpty{listOf("Basic stress checks passed; this is not a full simulated market attack test.")}.joinToString("; ")
        val cf=when { unified.direction=="NEUTRAL" -> "WAIT BASELINE"; unified.blockers.isNotEmpty() || unified.conflicts.isNotEmpty() -> "WAIT FAVORED"; else -> "DIRECTIONAL CASE ONLY" }
        val counterDetail="Current ${unified.direction} evidence (${unified.directionalScore.toInt()}) vs WAIT. No alternate candle path is simulated; counterfactual testing remains descriptive."
        val forecast = adaptive?.probabilisticForecast
        val calibration = when {
            forecast?.acceptedAsEvidence == true -> "OOS CALIBRATION DIAGNOSTICS PASSED / RAW SCORE NOT CALIBRATED"
            forecast?.available == true -> "OOS CALIBRATION GATE FAILED"
            adaptive?.deepLearning?.available == true -> "NOT CALIBRATED / FORECAST MODEL WARM-UP"
            else -> "NO PREDICTION VALIDATION SAMPLE"
        }
        val calibrationDetail = forecast?.note
            ?: "Probability calibration requires timestamped prediction records paired with later outcomes. Current model output alone cannot establish reliability."
        val drift=when { recentVol==null || priorVol==null || recentMove==null || priorMove==null -> "WARM-UP"; recentVol/priorVol.coerceAtLeast(1e-9)>2.0 || recentVol/priorVol.coerceAtLeast(1e-9)<.5 || kotlin.math.abs(recentMove-priorMove)>.04 -> "DRIFT / REGIME CHANGE FLAG"; else -> "NO STRONG DRIFT FLAG" }
        val driftDetail="Recent-vs-prior 24 candle comparison: volatility ratio ${if(recentVol!=null&&priorVol!=null) "%.2fx".format(recentVol/priorVol.coerceAtLeast(1e-9)) else "n/a"}; move difference ${if(recentMove!=null&&priorMove!=null) "%.2f pp".format((recentMove-priorMove)*100) else "n/a"}. Simple monitoring proxy."
        return CentralBrainAdvancedReport(transition,transitionDetail,uncertainty,uncertaintyDetail,adversarial,adversarialDetail,cf,counterDetail,calibration,calibrationDetail,drift,driftDetail)
    }
}
