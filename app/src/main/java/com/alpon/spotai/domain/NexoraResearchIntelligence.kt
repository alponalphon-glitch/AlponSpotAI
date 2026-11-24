package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/** Quantitative research layer. All values are deterministic measurements/proxies, not trained AI or forecasts. */
data class ResearchDiscipline(val name:String,val status:String,val finding:String)
data class ResearchCapability(val name:String,val status:String,val finding:String)
data class NexoraResearchReport(
    val available:Boolean,
    val directionalScore:Double,
    val returnVolatilityPct:Double?,
    val lastReturnZScore:Double?,
    val maxDrawdownPct:Double?,
    val trendEfficiency:Double?,
    val volatilityClusterRatio:Double?,
    val strategyOosReturnPct:Double?,
    val strategyOosTrades:Int,
    val earlyWarnings:List<String>,
    val disciplines:List<ResearchDiscipline>,
    val capabilities:List<ResearchCapability>,
    val note:String
)

object NexoraResearchIntelligence {
    fun evaluate(
        candles:List<Candle>, benchmark:CorrelationBrainReport?, liquidity:LiquidityMapReport?,
        execution:ExecutionQualityReport?, doctor:StrategyDoctorReport?, memory:MarketMemoryReport?,
        walkForward:WalkForwardResult?, external:com.alpon.spotai.data.ExternalMarketContext?,
        shock:MarketShockReport?, redTeam:RedTeamReport?, scenario:ScenarioRiskReport?
    ):NexoraResearchReport {
        val now=System.currentTimeMillis()
        val c=candles.filter{it.closeTime>0L&&it.closeTime<=now&&it.open.isFinite()&&it.high.isFinite()&&it.low.isFinite()&&it.close.isFinite()&&it.volume.isFinite()&&it.close>0&&it.low>0&&it.high>=it.low&&it.volume>=0}.sortedBy{it.time}.takeLast(600)
        val warnings=mutableListOf<String>()
        var vol:Double?=null; var z:Double?=null; var dd:Double?=null; var efficiency:Double?=null; var cluster:Double?=null; var oos:Double?=null; var trades=0; var score=0.0
        if(c.size>=100){
            val closes=c.map{it.close}; val rets=closes.zipWithNext{a,b->b/a-1.0}.filter{it.isFinite()};
            fun sd(xs:List<Double>):Double { if(xs.size<2)return 0.0; val m=xs.average(); return sqrt(xs.sumOf{(it-m)*(it-m)}/(xs.size-1)) }
            val recent=rets.takeLast(60); vol=sd(recent)*100.0
            val last=rets.lastOrNull()?:0.0; val base=rets.dropLast(1).takeLast(50); val s=sd(base); z=if(s>1e-12)(last-base.average())/s else 0.0
            var peak=closes.first(); var maxDd=0.0; closes.forEach{peak=maxOf(peak,it);if(peak>0)maxDd=maxOf(maxDd,(peak-it)/peak*100.0)}; dd=maxDd
            val window=closes.takeLast(50); val net=abs(window.last()-window.first()); val path=window.zipWithNext{a,b->abs(b-a)}.sum(); efficiency=if(path>0)net/path else 0.0
            val shortSd=sd(rets.takeLast(10)); val longSd=sd(rets.dropLast(10).takeLast(40)); cluster=if(longSd>1e-12)shortSd/longSd else null
            val trend=when{window.last()>window.first()*1.01->1.0;window.last()<window.first()*.99->-1.0;else->0.0}
            score=(trend*35.0+(if(efficiency!!>.45)trend*20.0 else 0.0)+(if(last>0)8.0 else if(last<0)-8.0 else 0.0)).coerceIn(-70.0,70.0)
            if(abs(z?:0.0)>=3.0)warnings+="Return anomaly: last closed-candle return is ${"%.1f".format(z)} standard deviations from the recent mean."
            if((cluster?:0.0)>=2.5)warnings+="Volatility clustering: recent realized volatility is at least 2.5× its prior baseline."
            if((dd?:0.0)>=10.0)warnings+="Historical sample drawdown reached ${"%.1f".format(dd)}%; capital survival review required."
            // Fixed EMA(12/26) hypothesis; chronological 70/30 split, no parameter search on test segment.
            fun ema(xs:List<Double>,p:Int):Double { if(xs.size<p)return Double.NaN; val k=2.0/(p+1); var e=xs.take(p).average(); for(i in p until xs.size)e=xs[i]*k+e*(1-k); return e }
            val split=(closes.size*.7).toInt().coerceAtLeast(60); var pnl=1.0; var position=0; var count=0
            for(i in split until closes.size){ val hist=closes.take(i); if(hist.size<26)continue; val fast=ema(hist,12);val slow=ema(hist,26);val desired=if(fast>slow)1 else if(fast<slow)-1 else 0; val r=closes[i]/closes[i-1]-1.0; if(position!=0){pnl*=1.0+position*r;count++}; position=desired }
            if(count>0){oos=(pnl-1.0)*100.0;trades=count}
        } else warnings+="Quant research needs at least 100 valid closed candles; its metrics are unavailable."
        val discipline=listOf(
            ResearchDiscipline("Market microstructure",if(execution?.takerBuyRatio!=null)"PROXY" else "LIMITED", "Taker-flow and trade-count proxies; no full depth/iceberg feed."),
            ResearchDiscipline("Liquidity science",if(liquidity?.available==true)"ACTIVE" else "WARM-UP",liquidity?.note?:"Liquidity proxy unavailable."),
            ResearchDiscipline("Quant statistics",if(vol!=null)"ACTIVE" else "WARM-UP","Realized return volatility, last-return Z-score and sample drawdown."),
            ResearchDiscipline("Volatility modelling",if(cluster!=null)"ACTIVE" else "WARM-UP","Short/long realized-volatility clustering ratio; not a fitted GARCH model."),
            ResearchDiscipline("Regime classification",if(efficiency!=null)"ACTIVE" else "WARM-UP","Trend-efficiency ratio and directional drift proxy."),
            ResearchDiscipline("Pattern recognition",if(c.size>=80)"ACTIVE" else "WARM-UP","Technical Quality engine supplies confirmed-candle patterns and divergence."),
            ResearchDiscipline("Cross-market research",if(benchmark?.available==true)"ACTIVE" else "LIMITED",benchmark?.note?:"Aligned benchmark return series unavailable."),
            ResearchDiscipline("Event intelligence",if(external?.verificationReady==true)"VERIFIED FEED" else "UNVERIFIED","External news context is used only when cross-source verification is ready; no macro calendar feed is assumed."),
            ResearchDiscipline("Strategy laboratory",if(oos!=null)"SHADOW TEST" else "WARM-UP","Fixed EMA 12/26 chronological holdout; trades=$trades, OOS return=${oos?.let{"%.2f%%".format(it)}?:"N/A"}; Walk-Forward=${walkForward?.let{"available"}?:"not run"}; ${doctor?.status?:"strategy doctor unavailable"}. Not strategy promotion."),
            ResearchDiscipline("Adversarial validation",if(redTeam?.robust==true)"PASSED CHECKS" else "COUNTER-EVIDENCE",redTeam?.counterArguments?.take(2)?.joinToString("; ")?:"Red-team review unavailable.")
        )
        val caps=listOf(
            ResearchCapability("Market Memory",if(memory?.available==true)"ACTIVE" else "WARM-UP",memory?.note?:"Historical analogue unavailable."),
            ResearchCapability("Strategy Inventor",if(oos!=null)"SHADOW CANDIDATE" else "WAITING DATA","Tests one fixed baseline hypothesis with a chronological holdout; Strategy Doctor=${doctor?.status?:"warming up"}; no automatic live deployment."),
            ResearchCapability("Early Warning Brain",if(warnings.isEmpty()&&shock?.safeForNewEntry==true)"NORMAL" else "WARNING",(warnings+shock?.findings.orEmpty()).take(3).joinToString("; ").ifBlank{"No abnormality found in available measurements."}),
            ResearchCapability("Multi-Agent Debate",if(redTeam!=null)"CONNECTED" else "UNAVAILABLE","Bull/bear counter-evidence is reviewed by deterministic analyst and red-team rules, not separate LLM agents."),
            ResearchCapability("Capital Survival",if((dd?:0.0)>=10.0||scenario?.stopCoversHistoricalStress==false)"CAUTION" else "MONITORING","Sample drawdown and scenario stress are reviewed; existing risk limits remain authoritative.")
        )
        return NexoraResearchReport(c.size>=100,score,vol,z,dd,efficiency,cluster,oos,trades,warnings.distinct(),discipline,caps,
            "Research measurements are deterministic and sample-dependent. They are not trained AI, calibrated probabilities, macro-event coverage or a guarantee of future returns.")
    }
}
