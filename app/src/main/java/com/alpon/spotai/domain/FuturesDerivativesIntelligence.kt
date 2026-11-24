package com.alpon.spotai.domain

import com.alpon.spotai.data.FuturesDerivativesSnapshot
import kotlin.math.abs

/** Interprets derivative-market positioning; it is a risk overlay, not a liquidation-price oracle. */
data class FuturesDerivativesReport(
    val available:Boolean,
    val directionalBias:String,
    val directionalScore:Int,
    val riskLevel:String,
    val markIndexPremiumPercent:Double?,
    val fundingPercent:Double?,
    val openInterestChangePercent:Double?,
    val longShortRatio:Double?,
    val takerBuySellRatio:Double?,
    val observations:List<String>,
    val blockers:List<String>
)

object FuturesDerivativesIntelligence {
    fun analyze(snapshot:FuturesDerivativesSnapshot?,now:Long=System.currentTimeMillis()):FuturesDerivativesReport {
        if(snapshot==null) return FuturesDerivativesReport(false,"UNKNOWN",0,"UNKNOWN",null,null,null,null,null,
            listOf("Funding, mark/index, open interest, positioning and taker-flow data are required."),
            listOf("Futures derivatives feed is unavailable; directional Futures decision is blocked."))
        val blockers= mutableListOf<String>()
        val notes= mutableListOf<String>()
        if(snapshot.symbol.isBlank() || snapshot.markPrice<=0.0 || snapshot.indexPrice<=0.0 || snapshot.openInterest<=0.0 ||
            !snapshot.fundingRate.isFinite() || !snapshot.openInterestChangePercent.isFinite() ||
            snapshot.longShortRatio<=0.0 || snapshot.takerBuySellRatio<=0.0) blockers += "Futures derivatives contain invalid or incomplete values."
        if(snapshot.observedAt<=0L || now-snapshot.observedAt>20*60_000L || snapshot.observedAt>now+60_000L)
            blockers += "Futures positioning data is stale or has an invalid timestamp."
        val premium=(snapshot.markPrice/snapshot.indexPrice-1.0)*100.0
        val funding=snapshot.fundingRate*100.0
        var score=0.0
        // Funding is a crowding / carry input, not a stand-alone direction signal.
        when {
            snapshot.fundingRate>=0.0005 -> { score-=12.0; notes += "Positive funding is elevated: long positions may be crowded." }
            snapshot.fundingRate<=-0.0005 -> { score+=12.0; notes += "Negative funding is elevated: short positions may be crowded." }
            else -> notes += "Funding is not at an extreme threshold."
        }
        when {
            snapshot.longShortRatio>=1.8 -> { score-=18.0; notes += "Global long/short ratio is long-heavy; squeeze risk is elevated." }
            snapshot.longShortRatio<=0.55 -> { score+=18.0; notes += "Global long/short ratio is short-heavy; squeeze risk is elevated." }
            snapshot.longShortRatio>1.15 -> { score-=6.0; notes += "Accounts are moderately net-long." }
            snapshot.longShortRatio<0.87 -> { score+=6.0; notes += "Accounts are moderately net-short." }
            else -> notes += "Long/short positioning is relatively balanced."
        }
        when {
            snapshot.takerBuySellRatio>=1.2 -> { score+=20.0; notes += "Aggressive taker buying is dominant." }
            snapshot.takerBuySellRatio<=0.83 -> { score-=20.0; notes += "Aggressive taker selling is dominant." }
            else -> notes += "Taker buy/sell flow is balanced."
        }
        when {
            snapshot.openInterestChangePercent>=10.0 -> { notes += "Open interest expanded sharply; leverage and liquidation risk may be building." }
            snapshot.openInterestChangePercent<=-10.0 -> { notes += "Open interest contracted sharply; positions may be closing or being liquidated." }
            snapshot.openInterestChangePercent>2.0 -> { notes += "Open interest is expanding moderately." }
            snapshot.openInterestChangePercent< -2.0 -> { notes += "Open interest is declining moderately." }
            else -> notes += "Open interest is broadly stable over the sampled window."
        }
        if(abs(premium)>=0.30) { score += if(premium>0) -8.0 else 8.0; notes += "Mark/index basis is stretched (${"%.3f".format(premium)}%)." }
        else notes += "Mark/index basis is contained."
        if(snapshot.openInterestChangePercent>=10.0 && snapshot.longShortRatio>=1.8 && snapshot.fundingRate>0.0)
            blockers += "Crowded leveraged-long build-up: avoid initiating a new LONG until positioning cools."
        if(snapshot.openInterestChangePercent>=10.0 && snapshot.longShortRatio<=0.55 && snapshot.fundingRate<0.0)
            blockers += "Crowded leveraged-short build-up: avoid initiating a new SHORT until positioning cools."
        if(abs(premium)>=0.75) blockers += "Mark/index dislocation is unusually large; wait for basis normalization."
        val directional=score.coerceIn(-100.0,100.0).toInt()
        val bias=when { directional>=20 -> "BULLISH"; directional<=-20 -> "BEARISH"; else -> "NEUTRAL" }
        val risk=when { blockers.isNotEmpty() || abs(premium)>=0.5 || abs(snapshot.fundingRate)>=0.001 -> "HIGH"; abs(snapshot.openInterestChangePercent)>=10.0 || abs(snapshot.fundingRate)>=0.0005 -> "ELEVATED"; else -> "NORMAL" }
        return FuturesDerivativesReport(true,bias,directional,risk,premium,funding,snapshot.openInterestChangePercent,
            snapshot.longShortRatio,snapshot.takerBuySellRatio,notes,blockers.distinct())
    }
}
