package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle

/** Additive, conservative strategic diagnostics for the unified decision report.
 * These are proxies only; unavailable feeds/costs are explicitly marked unavailable.
 */
data class CentralBrainStrategicReport(
    val liquidityIntelligence: String, val liquidityDetail: String,
    val eventImpact: String, val eventImpactDetail: String,
    val analystCouncil: String, val analystCouncilDetail: String,
    val executionReality: String, val executionRealityDetail: String,
    val marketKnowledgeGraph: String, val marketKnowledgeGraphDetail: String
)

object CentralBrainStrategicEngine {
    fun evaluate(
        candles: List<Candle>,
        structure: MarketStructureReport?,
        external: com.alpon.spotai.data.ExternalMarketContext?,
        unified: UnifiedBrainReport,
        derivatives: FuturesDerivativesReport?
    ): CentralBrainStrategicReport {
        val closed = candles.filter { it.closeTime == 0L || it.closeTime <= System.currentTimeMillis() }
            .filter { it.close.isFinite() && it.close > 0.0 && it.high.isFinite() && it.low.isFinite() && it.high >= it.low }
            .sortedBy { it.time }
        val current = closed.lastOrNull()?.close
        val supports = structure?.support.orEmpty().filter { it.price.isFinite() && it.price > 0.0 }
        val resistances = structure?.resistance.orEmpty().filter { it.price.isFinite() && it.price > 0.0 }
        val nearSupport = current?.let { p -> supports.minByOrNull { kotlin.math.abs(it.price-p) } }
        val nearResistance = current?.let { p -> resistances.minByOrNull { kotlin.math.abs(it.price-p) } }
        val sweep = structure?.liquiditySweep?.takeIf { it.isNotBlank() } ?: "No sweep evidence"
        val liquidity = if (structure == null || current == null) "UNAVAILABLE / WARM-UP" else "STRUCTURE PROXY AVAILABLE"
        val liquidityDetail = if (current == null) "Price or market-structure levels are unavailable; no liquidity map is inferred." else
            "Nearest support ${nearSupport?.price?.let { "%.6f".format(it) } ?: "n/a"}; nearest resistance ${nearResistance?.price?.let { "%.6f".format(it) } ?: "n/a"}; sweep: $sweep. These are candle-derived proxies, not order-book liquidity."
        val eventStatus = if (external?.verificationReady == true) "NEWS CONTEXT AVAILABLE — NO ECONOMIC CALENDAR" else "EVENT FEED UNVERIFIED"
        val eventDetail = external?.let { "Verified-feed flag ${it.verificationReady}; bullish headlines ${it.bullishNews}; bearish headlines ${it.bearishNews}; conflict ${it.newsConflict}. No scheduled macro calendar or causal impact model is connected." } ?: "External event/news context is absent. Economic calendar impact is not assessed."
        val opinions = unified.evidence
        val analystCouncil = when {
            opinions.size < 3 -> "INSUFFICIENT PANEL"
            unified.conflicts.isNotEmpty() -> "SPLIT COUNCIL"
            unified.direction == "NEUTRAL" -> "NEUTRAL COUNCIL"
            else -> "CONSENSUS PROXY"
        }
        val analystDetail = "Existing evidence modules: ${opinions.size}; direction ${unified.direction}; ${unified.conflicts.size} conflict(s). This reuses rule-based module outputs, not independent trained agents."
        val executionStatus = "NOT CONNECTED — COST INPUTS REQUIRED"
        val executionDetail = "Spread, fee schedule, expected slippage, order size, and order-book depth are not provided to this layer; execution realism cannot be estimated and is not assumed."
        val graphNodes = mutableListOf<String>()
        external?.let {
            if (it.btcDominancePct != null) graphNodes += "BTC dominance ${"%.1f%%".format(it.btcDominancePct)}"
            if (it.globalMarketCapChange24hPct != null) graphNodes += "Global market-cap change ${"%.2f%%".format(it.globalMarketCapChange24hPct)}"
            if (it.coinGeckoChange24hPct != null) graphNodes += "Asset 24h change ${"%.2f%%".format(it.coinGeckoChange24hPct)}"
        }
        structure?.let { graphNodes += "Structure ${it.trend}" }
        derivatives?.takeIf { it.available }?.let { graphNodes += "Derivatives ${it.directionalBias}; risk ${it.riskLevel}" }
        val graphStatus = if (graphNodes.size >= 2) "RELATIONSHIP CONTEXT PROXY" else "WARM-UP / LIMITED LINKS"
        val graphDetail = if (graphNodes.isEmpty()) "No linked context values available; knowledge graph connections are not inferred." else graphNodes.joinToString(" • ") + ". Relationships are descriptive and not causal claims."
        return CentralBrainStrategicReport(liquidity, liquidityDetail, eventStatus, eventDetail, analystCouncil, analystDetail, executionStatus, executionDetail, graphStatus, graphDetail)
    }
}
