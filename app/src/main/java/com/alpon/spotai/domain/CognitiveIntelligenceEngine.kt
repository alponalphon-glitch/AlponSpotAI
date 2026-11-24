package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle
import kotlin.math.abs

/** Unified, explainable decision-support layer. Scores are heuristic, never probabilities. */
data class ScenarioView(
    val name: String,
    val bias: String,
    val trigger: String,
    val invalidation: String,
    val evidenceScore: Int
)

data class LiquidityView(
    val bias: String,
    val buyPressure: Double?,
    val sellPressure: Double?,
    val relativeVolume: Double?,
    val nearestPool: String,
    val warning: String
)

data class MistakeView(
    val sampleCount: Int,
    val weakRegime: String?,
    val winRate: Double?,
    val averageReturn: Double?,
    val lessons: List<String>
)

data class RiskView(
    val status: String,
    val allowed: Boolean,
    val reasons: List<String>,
    val riskNote: String
)

data class CognitiveIntelligenceReport(
    val regime: MarketRegimeReport?,
    val scenarios: List<ScenarioView>,
    val liquidity: LiquidityView,
    val mistakes: MistakeView,
    val risk: RiskView,
    val disclaimer: String
)

object CognitiveIntelligenceEngine {
    fun analyze(
        candles: List<Candle>,
        regime: MarketRegimeReport?,
        structure: MarketStructureReport?,
        smartMoney: SmartMoneyReport?,
        signal: Signal?,
        ai: AIAnalysisReport?,
        outcomes: List<AdaptiveTradeOutcome>,
        lossGuardAllowed: Boolean = true,
        lossGuardReason: String? = null,
        adaptiveRiskMultiplier: Double = 1.0
    ): CognitiveIntelligenceReport {
        val flow = OrderFlowAnalysis.analyze(candles)
        val last = candles.lastOrNull()
        val score = ai?.directionalScore ?: 0.0
        val bullish = score >= 20.0 || structure?.trend == "BULLISH"
        val bearish = score <= -20.0 || structure?.trend == "BEARISH"
        val scenarios = listOf(
            ScenarioView(
                "BULLISH", if (bullish) "SUPPORTED" else "CONDITIONAL",
                "Closed candle above ${structure?.swingPoints?.lastOrNull { it.type == "HIGH" }?.price?.let { "%.6f".format(it) } ?: "recent resistance"} with volume confirmation",
                "Price closes below ${signal?.stop?.takeIf { it > 0.0 }?.let { "%.6f".format(it) } ?: "the confirmed swing low"}",
                (score.coerceAtLeast(0.0)).toInt()
            ),
            ScenarioView(
                "BEARISH", if (bearish) "SUPPORTED" else "CONDITIONAL",
                "Closed candle below ${structure?.swingPoints?.lastOrNull { it.type == "LOW" }?.price?.let { "%.6f".format(it) } ?: "recent support"} with selling-volume confirmation",
                "Price closes above ${signal?.entry?.takeIf { it > 0.0 }?.let { "%.6f".format(it) } ?: "the confirmed swing high"}",
                (abs(score).takeIf { score < 0.0 } ?: 0.0).toInt()
            ),
            ScenarioView(
                "NEUTRAL", if (!bullish && !bearish) "PRIMARY" else "ALTERNATIVE",
                "Price remains inside recent support/resistance or directional components disagree",
                "A confirmed close outside the range with volume and structure agreement",
                (100.0 - abs(score)).toInt().coerceIn(0, 100)
            )
        )

        val pools = smartMoney?.liquidityPools.orEmpty()
        val nearest = last?.close?.let { px -> pools.minByOrNull { abs(it.price - px) } }
        val liquidityBias = when {
            flow?.bias == 1 || smartMoney?.bias == "BULLISH" -> "BUY-SIDE PRESSURE"
            flow?.bias == -1 || smartMoney?.bias == "BEARISH" -> "SELL-SIDE PRESSURE"
            else -> "BALANCED / UNCLEAR"
        }
        val liquidity = LiquidityView(
            liquidityBias,
            flow?.buyPressure,
            flow?.sellPressure,
            flow?.relativeVolume,
            nearest?.let { "${it.type} @ ${"%.6f".format(it.price)}${if (it.swept) " (swept)" else ""}" } ?: "No confirmed nearby equal-high/low pool",
            when {
                flow == null -> "Order-flow data unavailable; candle volume cannot reveal full order-book depth."
                flow.volumeSpike && abs(flow.cvdPercent) < 3.0 -> "Volume spike without clear taker-flow direction; avoid assuming breakout strength."
                smartMoney?.breakoutQuality?.contains("FALSE_BREAKOUT") == true -> "Possible false breakout; wait for close and retest."
                else -> "Uses Binance candle taker-volume and SMC levels; this is not a live depth/order-book heatmap."
            }
        )

        val valid = outcomes.filter { it.pnlPercent.isFinite() && it.regime.isNotBlank() }
        val grouped = valid.groupBy { it.regime.uppercase() }
            .filterValues { it.size >= 8 }
            .mapValues { (_, xs) -> xs.sortedByDescending { it.timestamp }.take(30) }
        val weak = grouped.entries.mapNotNull { (key, xs) ->
            val win = xs.count { it.pnlPercent > 0.0 } * 100.0 / xs.size
            val avg = xs.map { it.pnlPercent }.average()
            if (win < 50.0 || avg <= 0.0) Triple(key, win, avg) else null
        }.minByOrNull { it.second + it.third.coerceAtMost(0.0) }
        val lessons = mutableListOf<String>()
        if (valid.isEmpty()) lessons += "No closed Paper Trading outcomes yet; mistake diagnosis is in warm-up."
        if (valid.size in 1..7) lessons += "Only ${valid.size} outcomes saved; wait for at least 8 before regime-specific conclusions."
        if (weak != null) lessons += "${weak.first}: ${"%.1f".format(weak.second)}% wins and ${"%.3f".format(weak.third)}% average over ${grouped[weak.first]?.size} recent paper outcomes; reduce exposure and review setups in this regime."
        if (valid.size >= 8 && weak == null) lessons += "No sufficiently sampled weak regime identified; continue collecting paper outcomes."
        lessons += "Outcome statistics show correlation, not the exact cause of each losing trade."
        val mistakes = MistakeView(
            valid.size, weak?.first, weak?.second, weak?.third, lessons
        )

        val gate = ai?.qualityGate
        val blockers = gate?.blockers.orEmpty().toMutableList()
        if (regime == null) blockers += "Market regime is unavailable."
        if (smartMoney == null) blockers += "Liquidity/structure evidence is incomplete."
        if (regime?.volatility == "HIGH") blockers += "High volatility: new BUY entries remain blocked."
        if (smartMoney?.breakoutQuality?.contains("FALSE_BREAKOUT") == true) blockers += "False-breakout risk: wait for confirmation."
        if (!lossGuardAllowed) blockers += (lossGuardReason ?: "Paper account loss guard is active.")
        val allowed = gate?.allowed == true && blockers.isEmpty() && lossGuardAllowed
        val adaptiveNote = if (adaptiveRiskMultiplier < 1.0) "Adaptive paper feedback has reduced the position allowance to ${(adaptiveRiskMultiplier * 100.0).toInt()}% of baseline. " else ""
        val risk = RiskView(
            if (allowed) "ENTRY CHECKS PASSED" else "WAIT / ENTRY BLOCKED",
            allowed,
            blockers.distinct(),
            adaptiveNote + "Existing capital limits remain in force: 0.5% default risk per trade, 25% maximum position allowance, daily/weekly loss limits and loss-streak pause. Paper analysis only; no live order is sent."
        )

        return CognitiveIntelligenceReport(
            regime, scenarios, liquidity, mistakes, risk,
            "Rule-based decision support, not a trained neural network. Scenario scores are evidence scores, not probabilities or guaranteed forecasts."
        )
    }
}
