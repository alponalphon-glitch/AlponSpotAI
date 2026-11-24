package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class TechnicalQualityReport(
    val available: Boolean,
    val direction: String,
    val score: Double,
    val trendStrength: Double?,
    val rsiWilder: Double?,
    val macdHistogram: Double?,
    val relativeVolume: Double?,
    val bullishDivergence: Boolean,
    val bearishDivergence: Boolean,
    val breakout: String,
    val candlePattern: String,
    val support: Double?,
    val resistance: Double?,
    val warnings: List<String>,
    val evidence: List<String>
)

/** Independent quality checks on confirmed OHLCV candles; this is deterministic TA, not ML. */
object TechnicalQualityEngine {
    fun analyze(input: List<Candle>): TechnicalQualityReport {
        val now = System.currentTimeMillis()
        val c = input.filter { it.closeTime > 0L && it.closeTime <= now && it.open.isFinite() && it.high.isFinite() && it.low.isFinite() && it.close.isFinite() && it.volume.isFinite() && it.close > 0 && it.high >= it.low && it.volume >= 0 }
            .sortedBy { it.time }.takeLast(500)
        if (c.size < 80) return TechnicalQualityReport(false,"NEUTRAL",0.0,null,null,null,null,false,false,"UNAVAILABLE","UNAVAILABLE",null,null,listOf("Need at least 80 valid closed candles for independent technical-quality checks."),emptyList())
        val close=c.map{it.close}; val price=close.last()
        fun ema(values:List<Double>,p:Int):Double { if(values.size<p)return Double.NaN; val k=2.0/(p+1); var e=values.take(p).average(); for(i in p until values.size)e=values[i]*k+e*(1-k); return e }
        fun rsiW(p:Int):Double { if(close.size<=p)return Double.NaN; val d=close.zipWithNext{a,b->b-a}; var g=d.take(p).sumOf{it.coerceAtLeast(0.0)}/p; var l=d.take(p).sumOf{(-it).coerceAtLeast(0.0)}/p; for(i in p until d.size){g=(g*(p-1)+d[i].coerceAtLeast(0.0))/p;l=(l*(p-1)+(-d[i]).coerceAtLeast(0.0))/p}; return if(l<=1e-12)100.0 else 100.0-100.0/(1+g/l) }
        val period=14
        val trs=mutableListOf<Double>(); val plus=mutableListOf<Double>(); val minus=mutableListOf<Double>()
        for(i in 1 until c.size){val a=c[i-1];val b=c[i];trs+=max(b.high-b.low,max(abs(b.high-a.close),abs(b.low-a.close)));val up=b.high-a.high;val dn=a.low-b.low;plus+=if(up>dn&&up>0)up else 0.0;minus+=if(dn>up&&dn>0)dn else 0.0}
        val recentTr=trs.takeLast(period).average().coerceAtLeast(1e-12); val pdi=plus.takeLast(period).sum()/recentTr/period*100; val mdi=minus.takeLast(period).sum()/recentTr/period*100
        val dx=if(pdi+mdi>0)abs(pdi-mdi)/(pdi+mdi)*100 else 0.0
        val rsi=rsiW(14)
        val macdLine=ema(close,12)-ema(close,26); val macdSeries=(0 until close.size).mapNotNull{i-> if(i<26)null else ema(close.take(i+1),12)-ema(close.take(i+1),26)}
        val signal=if(macdSeries.size>=9)ema(macdSeries,9) else Double.NaN; val hist=macdLine-signal
        val volAvg=c.takeLast(21).dropLast(1).map{it.volume}.average(); val relVol=if(volAvg>0)c.last().volume/volAvg else Double.NaN
        val e20=ema(close,20);val e50=ema(close,50);val e200=ema(close,200)
        val scoreParts=mutableListOf<Double>()
        scoreParts+=when{price>e20&&e20>e50&&e50>e200->1.0;price<e20&&e20<e50&&e50<e200->-1.0;price>e20->.25;price<e20->-.25;else->0.0}
        scoreParts+=when{pdi>mdi&&dx>=20->.8;pdi<mdi&&dx>=20->-.8;else->0.0}
        scoreParts+=when{hist>0->.7;hist<0->-.7;else->0.0}
        scoreParts+=when{rsi>=55&&rsi<=72->.5;rsi<=45&&rsi>=28->-.5;else->0.0}
        scoreParts+=when{relVol.isFinite()&&relVol>=1.3->if(c.last().close>c.last().open).6 else if(c.last().close<c.last().open)-.6 else 0.0;else->0.0}
        val n=20;val prev=c.dropLast(1).takeLast(n);val priorHigh=prev.maxOf{it.high};val priorLow=prev.minOf{it.low}
        val breakout=when{price>priorHigh->"BULLISH BREAKOUT";price<priorLow->"BEARISH BREAKDOWN";else->"RANGE / NO CONFIRMED BREAKOUT"}
        scoreParts+=when(breakout){"BULLISH BREAKOUT"->.8;"BEARISH BREAKDOWN"->-.8;else->0.0}
        // Compare two confirmed swing windows for simple regular RSI/price divergence.
        fun swingLow(start:Int,end:Int)= (start until end).minByOrNull{c[it].low}
        fun swingHigh(start:Int,end:Int)= (start until end).maxByOrNull{c[it].high}
        val split=c.size-28; val lowA=swingLow(split-28,split-14); val lowB=swingLow(split-14,split); val highA=swingHigh(split-28,split-14); val highB=swingHigh(split-14,split)
        fun rsiAt(index:Int):Double { val values=close.take(index+1); if(values.size<=14)return Double.NaN; val changes=values.zipWithNext{a,b->b-a}; var ag=changes.take(14).sumOf{it.coerceAtLeast(0.0)}/14;var al=changes.take(14).sumOf{(-it).coerceAtLeast(0.0)}/14;for(j in 14 until changes.size){ag=(ag*13+changes[j].coerceAtLeast(0.0))/14;al=(al*13+(-changes[j]).coerceAtLeast(0.0))/14};return if(al<=1e-12)100.0 else 100.0-100.0/(1+ag/al) }
        val bullishDiv=lowA!=null&&lowB!=null&&c[lowB].low<c[lowA].low&&rsiAt(lowB)>rsiAt(lowA)
        val bearishDiv=highA!=null&&highB!=null&&c[highB].high>c[highA].high&&rsiAt(highB)<rsiAt(highA)
        if(bullishDiv)scoreParts+=.45 else if(bearishDiv)scoreParts+=-.45
        val last=c.last();val body=abs(last.close-last.open);val range=(last.high-last.low).coerceAtLeast(1e-12);val upper=last.high-max(last.open,last.close);val lower=min(last.open,last.close)-last.low
        val pattern=when{last.close>last.open&&lower>=body*2&&upper<=body->"BULLISH HAMMER";last.close<last.open&&upper>=body*2&&lower<=body->"BEARISH SHOOTING STAR";last.close>last.open&&c[c.lastIndex-1].close<c[c.lastIndex-1].open&&last.close>=c[c.lastIndex-1].open&&last.open<=c[c.lastIndex-1].close->"BULLISH ENGULFING";last.close<last.open&&c[c.lastIndex-1].close>c[c.lastIndex-1].open&&last.open>=c[c.lastIndex-1].close&&last.close<=c[c.lastIndex-1].open->"BEARISH ENGULFING";body/range<.1->"DOJI / INDECISION";else->"NO HIGH-CONVICTION CANDLE PATTERN"}
        scoreParts+=when(pattern){"BULLISH HAMMER","BULLISH ENGULFING"->.35;"BEARISH SHOOTING STAR","BEARISH ENGULFING"->-.35;else->0.0}
        val score=(scoreParts.average()*100).coerceIn(-100.0,100.0);val direction=when{score>=20->"BULLISH";score<=-20->"BEARISH";else->"NEUTRAL"}
        val warnings=buildList{if(dx<18)add("Directional movement separation is weak; trend-following confirmation is limited.");if(relVol.isFinite()&&relVol<.7)add("Relative volume is below average; breakout confirmation is weak.");if(bullishDiv&&direction=="BEARISH")add("Bullish RSI/price divergence conflicts with the bearish aggregate.");if(bearishDiv&&direction=="BULLISH")add("Bearish RSI/price divergence conflicts with the bullish aggregate.");if(pattern=="DOJI / INDECISION")add("Latest closed candle is indecision; wait for follow-through.");if(breakout.contains("BREAKOUT")&&relVol.isFinite()&&relVol<1.2)add("Breakout lacks strong relative-volume confirmation.")}
        val evidence=listOf("Wilder RSI(14) ${"%.1f".format(rsi)}","DI separation (DX proxy) ${"%.1f".format(dx)} ( +DI ${"%.1f".format(pdi)} / -DI ${"%.1f".format(mdi)} )","MACD histogram ${"%.6f".format(hist)}","Relative volume ${if(relVol.isFinite())"%.2f".format(relVol)+"x" else "N/A"}",breakout,pattern,"EMA trend stack: ${if(price>e20&&e20>e50&&e50>e200)"bullish" else if(price<e20&&e20<e50&&e50<e200)"bearish" else "mixed"}")
        return TechnicalQualityReport(true,direction,score,dx.takeIf{it.isFinite()},rsi.takeIf{it.isFinite()},hist.takeIf{it.isFinite()},relVol.takeIf{it.isFinite()},bullishDiv,bearishDiv,breakout,pattern,c.takeLast(50).minOf{it.low},c.takeLast(50).maxOf{it.high},warnings,evidence)
    }
}
