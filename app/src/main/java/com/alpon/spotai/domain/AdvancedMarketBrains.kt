package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle
import com.alpon.spotai.domain.AdaptiveTradeOutcome
import kotlin.math.sqrt
import kotlin.math.abs

/** Five deterministic specialist engines. They expose evidence and explicit data limitations. */
data class LiquidityMapReport(val available:Boolean,val levels:List<Pair<Double,Double>>,val nearestLevel:Double?,val note:String)
data class CorrelationBrainReport(val available:Boolean,val benchmark:String,val correlation:Double?,val sampleCount:Int,val note:String)
data class ExecutionQualityReport(val available:Boolean,val quoteTurnover:Double?,val takerBuyRatio:Double?,val averageTrades:Double?,val grade:String,val warnings:List<String>)
data class StrategyDoctorReport(val status:String,val sampleTrades:Int,val winRate:Double?,val averageReturn:Double?,val diagnosis:List<String>)
data class MarketMemoryReport(val available:Boolean,val matchedWindowStart:Long?,val similarity:Double?,val forwardReturnPercent:Double?,val samples:Int,val note:String)
data class AdvancedBrainsReport(val liquidity:LiquidityMapReport,val correlation:CorrelationBrainReport,val execution:ExecutionQualityReport,val strategyDoctor:StrategyDoctorReport,val marketMemory:MarketMemoryReport)

object AdvancedMarketBrains {
    fun evaluate(candles:List<Candle>, benchmarkCandles:List<Candle>, benchmark:String, outcomes:List<AdaptiveTradeOutcome>, walkForward:WalkForwardResult?, currentPrice:Double):AdvancedBrainsReport {
        val closed=candles.filter{it.closeTime>0 && it.closeTime<=System.currentTimeMillis() && it.close>0 && it.volume>=0}.sortedBy{it.time}
        val liquidity=liquidityMap(closed,currentPrice)
        val correlation=correlation(closed,benchmarkCandles,benchmark)
        val execution=executionQuality(closed)
        val doctor=strategyDoctor(outcomes,walkForward)
        val memory=marketMemory(closed)
        return AdvancedBrainsReport(liquidity,correlation,execution,doctor,memory)
    }
    private fun liquidityMap(c:List<Candle>,price:Double):LiquidityMapReport {
        if(c.size<40 || price<=0) return LiquidityMapReport(false, emptyList(),null,"Need at least 40 closed candles for a volume-weighted liquidity proxy.")
        val lo=c.takeLast(120).minOf{it.low}; val hi=c.takeLast(120).maxOf{it.high}; if(hi<=lo) return LiquidityMapReport(false,emptyList(),null,"Price range is not usable.")
        val bins=DoubleArray(24); c.takeLast(120).forEach{x-> val typical=(x.high+x.low+x.close)/3.0; val i=(((typical-lo)/(hi-lo))*23).toInt().coerceIn(0,23); bins[i]+=x.quoteVolume.takeIf{it>0} ?: x.volume*x.close }
        val max=bins.maxOrNull()?:0.0; val levels=bins.indices.filter{bins[it]>0}.sortedByDescending{bins[it]}.take(5).map{ i-> (lo+(hi-lo)*i/23.0) to if(max>0) bins[i]/max else 0.0 }
        val nearest=levels.minByOrNull{abs(it.first-price)}?.first
        return LiquidityMapReport(levels.isNotEmpty(),levels,nearest,"Volume-by-price approximation from candles; not exchange order-book depth or true resting liquidity.")
    }
    private fun correlation(a:List<Candle>,b:List<Candle>,name:String):CorrelationBrainReport {
        if(a.size<35 || b.size<35) return CorrelationBrainReport(false,name,null,0,"Benchmark candle history unavailable; cross-asset correlation is not guessed.")
        val bm=b.associateBy{it.time}; val pairs=a.zipWithNext().mapNotNull{(x,y)-> val bx=bm[x.time]?:return@mapNotNull null; val by=bm[y.time]?:return@mapNotNull null; if(x.close<=0||y.close<=0||bx.close<=0||by.close<=0)null else (y.close/x.close-1.0) to (by.close/bx.close-1.0) }
        if(pairs.size<30) return CorrelationBrainReport(false,name,null,pairs.size,"Fewer than 30 time-aligned return pairs; correlation withheld.")
        val xs=pairs.map{it.first}; val ys=pairs.map{it.second}; val mx=xs.average(); val my=ys.average(); val cov=xs.indices.sumOf{(xs[it]-mx)*(ys[it]-my)}; val dx=sqrt(xs.sumOf{(it-mx)*(it-mx)}); val dy=sqrt(ys.sumOf{(it-my)*(it-my)}); val r=if(dx*dy>0) cov/(dx*dy) else 0.0
        return CorrelationBrainReport(true,name,r.coerceIn(-1.0,1.0),pairs.size,"Pearson correlation of aligned closed-candle returns; correlation is descriptive, not causal.")
    }
    private fun executionQuality(c:List<Candle>):ExecutionQualityReport {
        if(c.size<30) return ExecutionQualityReport(false,null,null,null,"UNAVAILABLE",listOf("Need 30 closed candles to assess execution proxies."))
        val s=c.takeLast(30); val quote=s.map{it.quoteVolume.takeIf{v->v>0}?:it.volume*it.close}.average(); val taker=s.mapNotNull{it.takerBuyBaseVolume.takeIf{v->it.volume>0 && v>=0}?.let{v->v/it.volume}}.average().takeIf{it.isFinite()}; val trades=s.map{it.tradeCount.toDouble()}.filter{it>0}.average().takeIf{it.isFinite()&&it>0}
        val warnings=mutableListOf<String>(); warnings+="Bid/ask spread and order-book depth are not supplied by the current 24h ticker; exact slippage cannot be certified."; if(quote<100_000) warnings+="Low recent quote turnover proxy; market impact may be elevated."; if(taker==null) warnings+="Taker-flow fields are unavailable for this feed."
        val grade=if(quote>=1_000_000 && trades!=null && taker!=null)"PROXY GOOD" else "PROXY LIMITED"
        return ExecutionQualityReport(true,quote,taker,trades,grade,warnings)
    }
    private fun strategyDoctor(o:List<AdaptiveTradeOutcome>,wf:WalkForwardResult?):StrategyDoctorReport {
        val valid=o.filter{it.pnlPercent.isFinite()}.takeLast(100); val diagnoses=mutableListOf<String>()
        if(valid.size<8) diagnoses+="Insufficient closed paper trades; collect at least 8 before regime-level diagnosis."
        val win=valid.takeIf{it.isNotEmpty()}?.count{it.pnlPercent>0}?.let{it*100.0/valid.size}; val avg=valid.takeIf{it.isNotEmpty()}?.map{it.pnlPercent}?.average()
        if(win!=null&&win<45) diagnoses+="Recent paper win rate is weak; do not increase risk."
        if(avg!=null&&avg<=0) diagnoses+="Average paper outcome is non-positive; review entry filters and fees."
        if(wf==null) diagnoses+="Walk-Forward evidence is missing; strategy robustness is unverified." else { if(wf.profitableTestFolds<wf.totalTestFolds/2.coerceAtLeast(1)) diagnoses+="Fewer than half of out-of-sample folds were profitable."; if(wf.averageOutOfSampleDrawdownPercent>8) diagnoses+="Out-of-sample drawdown is elevated." }
        if(diagnoses.isEmpty()) diagnoses+="No critical warning in available recent outcomes; continue shadow/paper validation."
        return StrategyDoctorReport(if(wf==null||valid.size<8)"WARMING UP" else if(diagnoses.any{it.contains("weak")||it.contains("non-positive")||it.contains("elevated")||it.contains("Fewer")})"REVIEW REQUIRED" else "MONITORING",valid.size,win,avg,diagnoses)
    }
    private fun marketMemory(c:List<Candle>):MarketMemoryReport {
        if(c.size<100) return MarketMemoryReport(false,null,null,null,0,"Need at least 100 closed candles for historical analog search.")
        val n=12; val current=c.takeLast(n); val start=c.size-2*n-3; if(start<0) return MarketMemoryReport(false,null,null,null,0,"Not enough non-overlapping history.")
        fun features(w:List<Candle>):DoubleArray { val base=w.first().close.takeIf{it>0}?:1.0; val returns=w.zipWithNext().map{it.second.close/it.first.close-1}; return doubleArrayOf((w.last().close/base-1),returns.map{abs(it)}.average(),w.map{(it.high-it.low)/it.close}.average(),w.map{it.volume}.average().let{v-> if(v>0)w.last().volume/v else 1.0}) }
        val f=features(current); var best=Double.POSITIVE_INFINITY; var bestIndex=-1; var bestForward:Double?=null; var samples=0
        for(i in 0..(c.size-2*n-4)) { if(i>=c.size-2*n) break; val w=c.subList(i,i+n); val g=features(w); val d=sqrt(f.indices.sumOf{k->val scale=if(k==0)0.15 else if(k==1)0.08 else if(k==2)0.08 else 1.0; ((f[k]-g[k])/scale).let{it*it}}); samples++; if(d<best){best=d;bestIndex=i; val after=c.getOrNull(i+n+2); bestForward=if(after!=null&&w.last().close>0)(after.close/w.last().close-1)*100 else null} }
        val sim=(100.0/(1.0+best)).toInt().coerceIn(0,100).toDouble(); return MarketMemoryReport(bestIndex>=0,c.getOrNull(bestIndex)?.time,sim,bestForward,samples,"Nearest historical 12-candle shape analogue; similarity is heuristic and does not predict future returns.")
    }
}
