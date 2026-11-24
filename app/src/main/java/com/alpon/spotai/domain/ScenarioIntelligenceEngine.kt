package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle
import kotlin.math.abs
import kotlin.math.max

/** Five-part, evidence-based scenario layer. It is analytical only and never submits orders. */
data class MarketScenario(val name: String, val bias: String, val trigger: String, val invalidation: String, val evidenceScore: Int)
data class ScenarioIntelligenceReport(
    val bullish: MarketScenario,
    val bearish: MarketScenario,
    val sideways: MarketScenario,
    val timeframeAgreement: String,
    val liquidityTrapWarning: String,
    val calibration: String,
    val calibrationSamples: Int,
    val recommendation: String,
    val reasons: List<String>
)

object ScenarioIntelligenceEngine {
    fun analyze(
        candles: List<Candle>, interval: String, mtf: MultiTimeframeSignal?,
        structure: MarketStructureReport?, regime: MarketRegimeReport?,
        outcomes: List<AdaptiveTradeOutcome>
    ): ScenarioIntelligenceReport {
        val data = candles.filter { it.closeTime > 0L && it.closeTime <= System.currentTimeMillis() }
            .sortedBy { it.time }.takeLast(120)
        val last = data.lastOrNull()
        val price = last?.close?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val recent = data.takeLast(20)
        val high = recent.maxOfOrNull { it.high } ?: 0.0
        val low = recent.minOfOrNull { it.low } ?: 0.0
        val range = (high - low).takeIf { it > 0.0 } ?: price * 0.01
        val score = mtf?.score ?: 50
        val bull = (score - 50).coerceIn(0, 50) * 2
        val bear = (50 - score).coerceIn(0, 50) * 2
        val neutral = (100 - max(bull, bear)).coerceIn(0, 100)
        val bullish = MarketScenario("BULLISH", if (bull >= 60) "FAVORABLE" else "CONDITIONAL", "Confirmed close above ${fmt(high)} and successful retest", "Close back below ${fmt(high)} / structure support", bull)
        val bearish = MarketScenario("BEARISH", if (bear >= 60) "FAVORABLE" else "CONDITIONAL", "Confirmed close below ${fmt(low)} and failed retest", "Close back above ${fmt(low)} / structure resistance", bear)
        val sideways = MarketScenario("SIDEWAYS", if (neutral >= 55) "ELEVATED" else "LOWER", "Price remains between ${fmt(low)} and ${fmt(high)}", "Confirmed range breakout with follow-through", neutral)
        val readings = mtf?.readings.orEmpty()
        val higher = readings.filter { it.label in setOf("1M", "1W", "3D", "1D", "12H", "8H", "4H") }
        val lower = readings.filter { it.label in setOf("2H", "1H", "30M", "15M", "5M") }
        val agreement = if (higher.isEmpty() || lower.isEmpty()) "MTF unavailable — insufficient confirmed frames"
            else "Higher ${higher.count { it.direction == "BULLISH" }} bullish / ${higher.count { it.direction == "BEARISH" }} bearish; lower ${lower.count { it.direction == "BULLISH" }} bullish / ${lower.count { it.direction == "BEARISH" }} bearish"
        val trap = when {
            structure == null -> "Liquidity map unavailable — avoid assuming a sweep."
            structure.liquiditySweep.contains("BULLISH", true) -> "Possible downside liquidity sweep; wait for bullish reclaim confirmation."
            structure.liquiditySweep.contains("BEARISH", true) -> "Possible upside liquidity sweep; wait for bearish rejection confirmation."
            structure.events.any { it.name.contains("BREAK", true) } -> "Structure break detected; verify close and retest to filter false breakout."
            else -> "No confirmed liquidity trap signal; nearby swing levels remain watch zones."
        }
        val regimeKey = regime?.regime?.uppercase()
        val sample = outcomes.filter { it.regime.uppercase() == regimeKey && it.pnlPercent.isFinite() }.takeLast(50)
        val calibration = if (sample.size < 10) "UNCALIBRATED — ${sample.size}/10 same-regime Paper Trading outcomes; no win-rate claim."
            else "Same-regime Paper Trading: ${sample.count { it.pnlPercent > 0.0 }}/${sample.size} profitable (${(sample.count { it.pnlPercent > 0.0 } * 100 / sample.size)}%); historical sample, not a forecast."
        val recommendation = when {
            data.size < 220 -> "NO TRADE — fewer than 220 closed candles available."
            mtf == null -> "NO TRADE — required multi-timeframe confirmation unavailable."
            regime == null -> "NO TRADE — market regime is not established."
            bull >= 60 && higher.count { it.direction == "BEARISH" } < 3 && lower.count { it.direction == "BULLISH" } >= 3 -> "BULLISH SCENARIO LEADS — wait for breakout close and retest; not an order."
            bear >= 60 && higher.count { it.direction == "BULLISH" } < 3 && lower.count { it.direction == "BEARISH" } >= 3 -> "BEARISH SCENARIO LEADS — wait for breakdown close and retest; Spot is analysis only."
            else -> "NO TRADE — scenarios conflict or lack confirmation."
        }
        val reasons = buildList {
            if (mtf == null) add("Multi-timeframe data missing")
            if (structure == null) add("Market structure unavailable")
            if (regime == null) add("Market regime unavailable")
            if (sample.size < 10) add("Not enough same-regime Paper Trading results to calibrate confidence")
            if (range <= price * 0.0001) add("Recent price range is too narrow for reliable scenario levels")
        }
        return ScenarioIntelligenceReport(bullish, bearish, sideways, agreement, trap, calibration, sample.size, recommendation, reasons)
    }
    private fun fmt(value: Double) = if (value.isFinite() && value > 0.0) String.format(java.util.Locale.US, "%.8f", value).trimEnd('0').trimEnd('.') else "unavailable"
}
