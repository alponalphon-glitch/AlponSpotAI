package com.alpon.spotai.domain

import kotlin.math.max
import kotlin.math.min

/** Deterministic long-position manager for simulation/paper trading. It never places exchange orders. */
data class DynamicTradeState(
    val entryPrice: Double,
    val atrAtEntry: Double,
    val peakPrice: Double,
    val stopPrice: Double,
    val trailingActive: Boolean,
    val takeProfitPrice: Double? = null
)

data class DynamicTradeUpdate(
    val state: DynamicTradeState,
    val shouldExit: Boolean,
    val exitReason: String,
    val unrealizedR: Double
)

object DynamicTradeManager {
    /**
     * Initial stop remains structure/ATR based. At +1R the stop moves to break-even
     * (with a small fee buffer), then follows the peak by current ATR. Stop never loosens.
     * A 2.2R target is a safety exit, not a forced time-based close.
     */
    fun updateLong(
        state: DynamicTradeState,
        marketPrice: Double,
        currentAtr: Double,
        opposingSignal: Boolean,
        opposingSignalStrength: Int,
        signalStrengthToExit: Int = 78,
        activationProfitPercent: Double = 0.0,
        trailAtrMultiplier: Double = 1.7
    ): DynamicTradeUpdate? {
        if (!state.entryPrice.isFinite() || state.entryPrice <= 0.0 ||
            !state.atrAtEntry.isFinite() || state.atrAtEntry <= 0.0 ||
            !state.peakPrice.isFinite() || state.peakPrice <= 0.0 ||
            !state.stopPrice.isFinite() || state.stopPrice <= 0.0 ||
            !marketPrice.isFinite() || marketPrice <= 0.0) return null

        val atr = currentAtr.takeIf { it.isFinite() && it > 0.0 } ?: state.atrAtEntry
        val initialRisk = (state.entryPrice - state.stopPrice).coerceAtLeast(state.entryPrice * 0.0001)
        val peak = max(state.peakPrice, marketPrice)
        val rAtPeak = (peak - state.entryPrice) / initialRisk
        val active = state.trailingActive || rAtPeak >= 1.0 ||
            (activationProfitPercent > 0.0 && (peak - state.entryPrice) / state.entryPrice * 100.0 >= activationProfitPercent)
        val feeProtectedBreakEven = state.entryPrice * 1.0025
        val candidate = if (active) max(feeProtectedBreakEven, peak - atr * trailAtrMultiplier.coerceIn(1.0, 3.5)) else state.stopPrice
        // For a long, the stop only ratchets upward and stays below the current/peak price.
        val nextStop = max(state.stopPrice, candidate).coerceAtMost(peak * 0.999999)
        val next = state.copy(peakPrice = peak, stopPrice = nextStop, trailingActive = active)
        val r = (marketPrice - state.entryPrice) / initialRisk
        val stopHit = marketPrice <= nextStop
        val targetHit = state.takeProfitPrice?.let { it.isFinite() && it > state.entryPrice && marketPrice >= it } == true
        val signalExit = opposingSignal && opposingSignalStrength >= signalStrengthToExit && r > 0.0
        val reason = when {
            stopHit && active -> "TRAILING_STOP: price retraced; the stop followed the peak and protected the position."
            stopHit -> "INITIAL_STOP: the adaptive protective stop was reached."
            targetHit -> "TAKE_PROFIT: the planned reward target was reached."
            signalExit -> "STRONG_OPPOSING_SIGNAL: the trend weakened while the position was profitable."
            else -> "HOLD: no exit condition; the position remains under adaptive protection."
        }
        return DynamicTradeUpdate(next, stopHit || targetHit || signalExit, reason, r)
    }
}
