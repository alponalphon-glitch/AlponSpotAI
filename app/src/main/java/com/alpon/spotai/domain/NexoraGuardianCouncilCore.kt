package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle
import kotlin.math.abs

/** Five hidden safety/reliability capabilities. Rule-based evidence checks, not trained agents. */
data class NexoraGuardianCouncilReport(
    val allowed: Boolean,
    val blockers: List<String>,
    val audit: List<String>
)

object NexoraGuardianCouncilCore {
    fun evaluate(
        action: String?, symbol: String,
        candles: List<Candle>, integrity: DataIntegrityReport,
        analysts: List<AnalystOpinion>, consensusPercent: Int,
        smartMoney: SmartMoneyReport?, advanced: AdvancedBrainsReport,
        portfolio: PortfolioExposureSnapshot?
    ): NexoraGuardianCouncilReport {
        val proposed = action?.uppercase() ?: "WAIT"
        val directional = proposed == "BUY" || proposed == "SELL"
        val blockers = mutableListOf<String>()
        val audit = mutableListOf<String>()
        val closed = candles.filter { it.closeTime > 0L && it.closeTime <= System.currentTimeMillis() }
            .sortedBy { it.time }
        // 1. Market Data Guardian: integrity plus a local sanity pass on recent closed candles.
        val recent = closed.takeLast(30)
        val malformed = recent.any { c ->
            !c.open.isFinite() || !c.high.isFinite() || !c.low.isFinite() || !c.close.isFinite() ||
                !c.volume.isFinite() || c.open <= 0.0 || c.close <= 0.0 || c.low <= 0.0 ||
                c.high < maxOf(c.open, c.close, c.low) || c.volume < 0.0
        }
        val guardianHealthy = integrity.valid && recent.size >= 20 && !malformed
        audit += "08 Market Data Guardian: integrity=${integrity.valid}, recentClosed=${recent.size}, malformed=$malformed, healthy=$guardianHealthy."
        if (directional && !guardianHealthy) blockers += "Market Data Guardian: recent candle validation is incomplete or invalid; WAIT."

        // 2. Signal confidence: use actual analyst agreement, never reinterpret a score as probability.
        val directionalAnalysts = analysts.filter { it.direction in setOf("BULLISH", "BEARISH") && abs(it.score) >= 20 }
        val matching = directionalAnalysts.count {
            (proposed == "BUY" && it.direction == "BULLISH") || (proposed == "SELL" && it.direction == "BEARISH")
        }
        val agreement = if (directionalAnalysts.isEmpty()) 0 else matching * 100 / directionalAnalysts.size
        val confidenceLabel = when {
            directionalAnalysts.size < 3 -> "INSUFFICIENT_EVIDENCE"
            agreement >= 75 -> "STRONG_AGREEMENT"
            agreement >= 60 -> "MIXED_AGREEMENT"
            else -> "LOW_AGREEMENT"
        }
        audit += "09 Signal Confidence: analystAgreement=$agreement%, directionalAnalysts=${directionalAnalysts.size}, aggregateConsensus=$consensusPercent%, label=$confidenceLabel; this is agreement, not calibrated probability."
        if (directional && directionalAnalysts.size >= 3 && agreement < 60) blockers += "Signal Confidence Engine: fewer than 60% of meaningful analysts support the proposed direction; WAIT."

        // 3. Liquidity Trap Detector: use the existing SMC breakout classification and swept pools.
        val breakout = smartMoney?.breakoutQuality ?: "UNAVAILABLE"
        val trap = breakout.contains("FALSE_BREAKOUT", true) ||
            (proposed == "BUY" && breakout == "POSSIBLE_BULLISH_FALSE_BREAKOUT") ||
            (proposed == "SELL" && breakout == "POSSIBLE_BEARISH_FALSE_BREAKOUT")
        val sweptPools = smartMoney?.liquidityPools?.count { it.swept } ?: 0
        audit += "10 Liquidity Trap Detector: breakout=$breakout, sweptLiquidityPools=$sweptPools; candle-derived SMC evidence only, not live order-book liquidity."
        if (directional && trap) blockers += "Liquidity Trap Detector: possible false breakout challenges the proposed direction; wait for confirmed close/retest."

        // 4. Portfolio guard: prevent duplicate exposure and flag high benchmark concentration.
        val open = portfolio?.openSymbols.orEmpty().map { it.uppercase() }.toSet()
        val duplicate = symbol.uppercase() in open
        val exposureRatio = portfolio?.takeIf { it.equity > 0.0 && it.investedNotional.isFinite() }?.let { it.investedNotional / it.equity }
        val corr = advanced.correlation.correlation
        val correlatedConcentration = corr != null && corr.isFinite() && abs(corr) >= 0.85 && (exposureRatio ?: 0.0) >= 0.50
        audit += "11 Portfolio Correlation Guard: duplicateSymbol=$duplicate, investedToEquity=${exposureRatio?.let { "%.3f".format(it) } ?: "UNAVAILABLE"}, benchmarkCorrelation=${corr?.let { "%.3f".format(it) } ?: "UNAVAILABLE"}, concentratedCorrelation=$correlatedConcentration; benchmark proxy is not a full pairwise portfolio covariance matrix."
        if (proposed == "BUY" && duplicate) blockers += "Portfolio Guard: this symbol is already present in the open portfolio; duplicate exposure blocked."
        if (proposed == "BUY" && correlatedConcentration) blockers += "Portfolio Guard: high benchmark correlation plus elevated invested exposure; new correlated exposure paused."

        // 5. AI Council: compare independent deterministic analyst directions.
        val councilConflict = directionalAnalysts.size >= 3 &&
            directionalAnalysts.count { it.direction != if (proposed == "BUY") "BULLISH" else "BEARISH" } >= 2
        val councilStatus = when {
            directionalAnalysts.size < 3 -> "NOT_ENOUGH_ANALYSTS"
            councilConflict -> "CONFLICT"
            else -> "CONSENSUS_REVIEWED"
        }
        audit += "12 AI Council: status=$councilStatus, analysts=${directionalAnalysts.size}, supporting=$matching, opposing=${directionalAnalysts.size - matching}; deterministic specialist engines, not independent LLM agents."
        if (directional && councilConflict) blockers += "AI Council: independent analyst engines materially disagree; WAIT for stronger alignment."

        return NexoraGuardianCouncilReport(blockers.isEmpty(), blockers.distinct(), audit)
    }
}
