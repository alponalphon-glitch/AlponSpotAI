package com.alpon.spotai.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class RiskPlan(
    val action: String,
    val entry: Double,
    val stop: Double?,
    val tp1: Double?,
    val tp2: Double?,
    val tp3: Double?,
    val rr: Double,
    val stopDistance: Double,
    val riskCash: Double?,
    val quantity: Double?,
    val notional: Double?,
    val effectiveRisk: Double?,
    val volatilityFactor: Double,
    val warning: String
)

object RiskManagementEngine {

    fun calculate(
        action: String,
        entry: Double,
        atr: Double,
        support: Double,
        resistance: Double,
        volatilityRatio: Double,
        accountBalance: Double? = null,
        riskPercent: Double? = null,
        maxCapitalPercent: Double = 25.0,
        feeRate: Double = 0.001,
        slippageRate: Double = 0.0005
    ): RiskPlan? {
        if (!entry.isFinite() || entry <= 0.0) return null
        if (!atr.isFinite() || atr <= 0.0) return null

        if (action == "SELL") {
            return RiskPlan(
                action, entry, null, null, null, null,
                0.0, 0.0, null, null, null, null,
                1.0, "SELL: exit or reduce an existing Spot holding. No short position is opened."
            )
        }

        if (action != "BUY") {
            return RiskPlan(
                action, entry, null, null, null, null,
                0.0, 0.0, null, null, null, null,
                1.0, "WAIT: no position should be opened."
            )
        }

        val validRatio = volatilityRatio.takeIf {
            it.isFinite() && it > 0.0
        } ?: 1.0

        val volatilityFactor = sqrt(validRatio).coerceIn(0.85, 1.6)
        val atrMultiplier = 1.6 * volatilityFactor
        val buffer = atr * 0.15

        val atrStop = entry - atr * atrMultiplier
        val stop = if (support.isFinite() && support > 0.0 && support < entry) {
            min(atrStop, support - buffer)
        } else atrStop

        val distance = abs(entry - stop)
        if (!stop.isFinite() || stop <= 0.0 || stop >= entry || !distance.isFinite() || distance <= 0.0) return null

        val tp1 = entry + distance
        val tp2 = entry + distance * 2.0
        val tp3 = entry + distance * 3.0

        val balance = accountBalance?.takeIf {
            it.isFinite() && it > 0.0
        }
        val percent = riskPercent?.takeIf {
            it.isFinite() && it in 0.05..2.0
        }
        if (!maxCapitalPercent.isFinite() || maxCapitalPercent !in 1.0..100.0) return null
        val maxPercent = maxCapitalPercent
        val safeFee = feeRate.takeIf { it.isFinite() && it in 0.0..0.01 } ?: return null
        val safeSlippage = slippageRate.takeIf { it.isFinite() && it in 0.0..0.01 } ?: return null
        val entryFill = entry * (1.0 + safeSlippage)
        val stopFill = stop * (1.0 - safeSlippage)
        // Include estimated entry/exit fees and adverse slippage in per-unit risk.
        val riskPerUnit = (entryFill - stopFill) + (entryFill + stopFill) * safeFee
        if (!riskPerUnit.isFinite() || riskPerUnit <= 0.0) return null

        val riskCash = if (balance != null && percent != null) balance * percent / 100.0 else null
        val quantity = if (balance != null && riskCash != null) {
            val riskBasedQty = riskCash / riskPerUnit
            val capitalLimitedQty = balance * maxPercent / 100.0 / entryFill
            min(riskBasedQty, capitalLimitedQty)
        } else null

        val notional = quantity?.times(entryFill)
        val effectiveRisk = quantity?.times(riskPerUnit)

        val warning = when {
            validRatio >= 1.5 ->
                "High volatility: position sizing requires extra caution."
            quantity != null && effectiveRisk != null &&
                riskCash != null && effectiveRisk < riskCash ->
                "Position size capped by the configured %.1f%% capital allocation limit.".format(maxPercent)
            else -> "Risk estimate includes configured fees and slippage; gaps and execution failures can still exceed it."
        }

        return RiskPlan(
            action, entry, stop, tp1, tp2, tp3,
            3.0, distance, riskCash, quantity, notional,
            effectiveRisk, volatilityFactor, warning
        )
    }
}
