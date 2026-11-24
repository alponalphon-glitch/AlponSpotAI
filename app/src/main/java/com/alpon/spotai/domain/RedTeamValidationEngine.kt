package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle
import kotlin.math.abs
import kotlin.math.max

/**
 * Adversarial review for a proposed Spot BUY. It actively searches for reasons
 * the setup may fail instead of adding another bullish vote. All scores are
 * deterministic evidence checks, not probabilities or trained-model outputs.
 */
data class RedTeamReport(
    val robust: Boolean,
    val verdict: String,
    val stressNetRewardRisk: Double?,
    val sensitivityStatus: String,
    val counterArguments: List<String>,
    val checkedClosedCandles: Int
)

object RedTeamValidationEngine {
    fun evaluate(
        candles: List<Candle>,
        signal: Signal?,
        ai: AIAnalysisReport?,
        now: Long = System.currentTimeMillis(),
        feeRate: Double = 0.001,
        slippageRate: Double = 0.0005,
        minimumNetRewardRisk: Double = 1.5
    ): RedTeamReport {
        if (ai?.action != "BUY") {
            return RedTeamReport(true, "NOT APPLICABLE — no BUY proposal to challenge", null,
                "No BUY sensitivity test required", emptyList(), 0)
        }

        val flags = mutableListOf<String>()
        val closed = candles.filter { it.closeTime > 0L && it.closeTime <= now }.sortedBy { it.time }
        if (closed.size < 30) {
            flags += "Red-Team check has fewer than 30 confirmed closed candles; BUY evidence is insufficient."
        }

        if (closed.size >= 30) {
            val recent = closed.takeLast(21)
            val previous = recent.dropLast(1)
            val latest = recent.last()
            val ranges = previous.map { (it.high - it.low).coerceAtLeast(0.0) }.filter { it.isFinite() && it > 0.0 }
            val medianRange = median(ranges)
            val latestRange = latest.high - latest.low
            val body = abs(latest.close - latest.open)
            val upperWick = latest.high - max(latest.open, latest.close)
            val closeLocation = if (latestRange > 0.0) (latest.close - latest.low) / latestRange else 0.5

            if (medianRange > 0.0 && latestRange >= medianRange * 3.5) {
                flags += "Adversarial check: latest closed candle range is at least 3.5× its recent median; volatility shock may invalidate normal entries."
            }
            if (latestRange > 0.0 && upperWick / latestRange >= 0.60 && closeLocation <= 0.55) {
                flags += "Adversarial check: strong upper-wick rejection on the latest closed candle challenges the BUY thesis."
            }

            val priorVolumes = previous.map { it.volume }.filter { it.isFinite() && it >= 0.0 }
            val medianVolume = median(priorVolumes)
            val volumeRatio = if (medianVolume > 0.0) latest.volume / medianVolume else 0.0
            if (volumeRatio >= 4.0 && latest.close < latest.open && latestRange > 0.0 && body / latestRange < 0.45) {
                flags += "Adversarial check: extreme volume with a weak bearish close may indicate selling absorption or a failed breakout."
            }

            val priorClose = previous.lastOrNull()?.close ?: 0.0
            if (priorClose > 0.0 && latest.open > priorClose * 1.02 && latest.close < latest.open) {
                flags += "Adversarial check: gap-up above 2% was rejected by the latest closed candle."
            }

            val closes = closed.map { it.close }
            if (closes.size >= 30) {
                val shortNow = closes.takeLast(5).average()
                val shortBefore = closes.dropLast(5).takeLast(5).average()
                val longNow = closes.takeLast(15).average()
                val longBefore = closes.dropLast(15).takeLast(15).average()
                val shortReturn = if (shortBefore > 0.0) (shortNow / shortBefore) - 1.0 else 0.0
                val longReturn = if (longBefore > 0.0) (longNow / longBefore) - 1.0 else 0.0
                if (shortReturn < -0.002 && longReturn > 0.002) {
                    flags += "Regime-transition warning: short-term momentum is reversing against the broader uptrend; wait for stabilization."
                }
            }
        }

        val score = ai.directionalScore
        val sensitivity = if (!score.isFinite()) {
            flags += "Directional evidence score is invalid; robustness cannot be established."
            "INVALID SCORE"
        } else if (score - 10.0 < 25.0) {
            flags += "Fragile signal: a 10-point adverse evidence adjustment would remove the minimum BUY threshold."
            "FRAGILE — fails a 10-point adverse score shock"
        } else {
            "PASSED — remains above the BUY threshold after a 10-point adverse score shock"
        }

        val stressedRr = stressRewardRisk(signal, feeRate, slippageRate)
        if (stressedRr == null) {
            flags += "Adversarial cost test failed: valid entry, Stop Loss, Take Profit and ATR are required."
        } else if (stressedRr < minimumNetRewardRisk) {
            flags += "Adversarial cost test failed: stressed net reward/risk is %.2f:1, below %.2f:1.".format(stressedRr, minimumNetRewardRisk)
        }

        val robust = flags.isEmpty()
        return RedTeamReport(
            robust = robust,
            verdict = if (robust) "PASSED — no critical counter-evidence found in the tested scenarios" else "REJECT / WAIT — counter-evidence makes the BUY setup fragile",
            stressNetRewardRisk = stressedRr,
            sensitivityStatus = sensitivity,
            counterArguments = flags.distinct(),
            checkedClosedCandles = closed.size
        )
    }

    private fun stressRewardRisk(
        signal: Signal?,
        feeRate: Double,
        slippageRate: Double
    ): Double? {
        if (signal == null || !feeRate.isFinite() || !slippageRate.isFinite() ||
            feeRate !in 0.0..0.02 || slippageRate !in 0.0..0.02) return null
        val entry = signal.entry
        val stop = signal.stop
        val target = signal.tp2
        val atr = signal.atr
        if (!entry.isFinite() || !stop.isFinite() || !target.isFinite() || !atr.isFinite() ||
            entry <= 0.0 || stop <= 0.0 || target <= entry || stop >= entry || atr <= 0.0) return null

        // Stress execution costs to 2× baseline and assume entry is 0.10 ATR worse.
        val stressedEntry = entry * (1.0 + slippageRate * 2.0) + atr * 0.10
        val stressedStop = stop * (1.0 - slippageRate * 2.0)
        val stressedTarget = target * (1.0 - slippageRate * 2.0)
        val stressedFee = feeRate * 2.0
        val risk = (stressedEntry - stressedStop) + (stressedEntry + stressedStop) * stressedFee
        val reward = (stressedTarget - stressedEntry) - (stressedEntry + stressedTarget) * stressedFee
        if (!risk.isFinite() || !reward.isFinite() || risk <= 0.0 || reward <= 0.0) return null
        return (reward / risk).takeIf { it.isFinite() && it > 0.0 }
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }
}
