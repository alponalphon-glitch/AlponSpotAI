package com.alpon.spotai.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Adaptive, explainable risk plan for LONG/Spot paper positions.
 * Stop distance is market-structure/ATR based, not a fixed 1% rule.
 * Position size is reduced as the stop widens so planned account risk stays bounded.
 */
data class AdaptiveRiskPlan(
    val valid: Boolean,
    val stopPrice: Double,
    val takeProfitPrice: Double,
    val stopDistancePercent: Double,
    val riskBudget: Double,
    val estimatedRiskAtStop: Double,
    val maxNotional: Double,
    val atrMultiplier: Double,
    val rationale: String
)

object AdaptiveRiskMoneyManager {
    fun planLong(
        entry: Double,
        atr: Double,
        support: Double,
        volatilityRatio: Double,
        accountEquity: Double,
        riskPercent: Double = 0.5,
        maxAllocationPercent: Double = 25.0,
        feeRate: Double = 0.001,
        slippageRate: Double = 0.0005
    ): AdaptiveRiskPlan? {
        if (!entry.isFinite() || entry <= 0.0 || !atr.isFinite() || atr <= 0.0 ||
            !accountEquity.isFinite() || accountEquity <= 0.0 ||
            riskPercent !in 0.05..1.0 || maxAllocationPercent !in 1.0..100.0 ||
            feeRate !in 0.0..0.01 || slippageRate !in 0.0..0.01) return null

        val vol = volatilityRatio.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        val atrMultiplier = (1.65 * sqrt(vol).coerceIn(0.9, 1.65)).coerceIn(1.5, 2.8)
        val atrStop = entry - atr * atrMultiplier
        val structuralStop = if (support.isFinite() && support > 0.0 && support < entry) {
            min(atrStop, support - atr * 0.12)
        } else atrStop
        val stop = structuralStop
        val distancePct = (entry - stop) / entry * 100.0
        // Very tight stops are noise-prone; very wide stops make the setup unsuitable.
        if (!stop.isFinite() || stop <= 0.0 || distancePct !in 0.60..8.0) return AdaptiveRiskPlan(
            false, stop.takeIf { it.isFinite() && it > 0.0 } ?: 0.0, 0.0,
            distancePct.takeIf { it.isFinite() } ?: 0.0,
            accountEquity * riskPercent / 100.0, 0.0, 0.0, atrMultiplier,
            "No entry: adaptive stop distance is outside the 0.60%-8.0% operating band; wait for a cleaner setup."
        )

        val riskBudget = accountEquity * riskPercent / 100.0
        val entryFill = entry * (1.0 + slippageRate)
        val stopFill = stop * (1.0 - slippageRate)
        val riskPerUnit = (entryFill - stopFill) + (entryFill + stopFill) * feeRate
        if (!riskPerUnit.isFinite() || riskPerUnit <= 0.0) return null
        val riskSizedNotional = (riskBudget / riskPerUnit) * entryFill
        val allocationCap = accountEquity * maxAllocationPercent / 100.0
        val maxNotional = min(riskSizedNotional, allocationCap)
        val estimatedRisk = maxNotional / entryFill * riskPerUnit
        val target = entry + (entry - stop) * 2.2
        return AdaptiveRiskPlan(
            true, stop, target, distancePct, riskBudget, estimatedRisk, maxNotional,
            atrMultiplier,
            "Stop uses ATR x %.2f and nearby support; size is capped to %.2f%% equity risk and %.1f%% allocation. Target is 2.2R before costs.".format(
                atrMultiplier, riskPercent, maxAllocationPercent
            )
        )
    }
}
