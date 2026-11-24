package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle
import kotlin.math.abs
import kotlin.math.sqrt

/** Parameter-sweep ensemble. Thousands of configurations are correlated hypotheses, not independent indicators. */
data class TechnicalEnsembleReport(
    val configurationCount:Int,
    val directionalScore:Double,
    val bias:String,
    val familyScores:Map<String,Double>,
    val trendlineSlopePercent:Double,
    val support:Double,
    val resistance:Double,
    val warnings:List<String>
)

object TechnicalEnsembleEngine {
    fun analyze(input:List<Candle>):TechnicalEnsembleReport? {
        val now=System.currentTimeMillis()
        val candles=input.filter { it.closeTime>0L && it.closeTime<=now }
            .filter { it.time>0L && it.close.isFinite() && it.high.isFinite() && it.low.isFinite() && it.volume.isFinite() && it.close>0.0 && it.high>=it.low && it.volume>=0.0 }
            .takeLast(600)
        if(candles.size<300) return null
        val close=candles.map{it.close}; val high=candles.map{it.high}; val low=candles.map{it.low}
        val price=close.last(); val families=linkedMapOf<String,MutableList<Double>>()
        var count=0
        fun vote(family:String,value:Double){ if(value.isFinite()){families.getOrPut(family){mutableListOf()}.add(value.coerceIn(-1.0,1.0));count++} }
        fun ema(period:Int,values:List<Double> = close):Double {
            if(values.size<period) return Double.NaN
            val k=2.0/(period+1.0); var e=values.take(period).average()
            for(i in period until values.size) e=values[i]*k+e*(1-k)
            return e
        }
        fun emaSeries(period:Int,values:List<Double>):List<Double> {
            if(values.isEmpty()) return emptyList()
            val k=2.0/(period+1.0);var e=values.first();val out=ArrayList<Double>(values.size)
            values.forEachIndexed { i,v -> if(i>0)e=v*k+e*(1-k);out+=e }
            return out
        }
        fun rsi(period:Int):Double {
            if(close.size<=period) return Double.NaN
            var gain=0.0;var loss=0.0
            for(i in 1..period){val d=close[i]-close[i-1];if(d>0)gain+=d else loss-=d}
            var ag=gain/period;var al=loss/period
            for(i in period+1 until close.size){val d=close[i]-close[i-1];ag=(ag*(period-1)+d.coerceAtLeast(0.0))/period;al=(al*(period-1)+(-d).coerceAtLeast(0.0))/period}
            return if(al<=1e-12)100.0 else 100.0-100.0/(1.0+ag/al)
        }
        fun mean(values:List<Double>)=values.average()
        fun stdev(values:List<Double>):Double {val m=mean(values);return sqrt(values.sumOf{(it-m)*(it-m)}/values.size.coerceAtLeast(1))}

        // EMA crossover and slope grid: 200+ fast/slow combinations.
        val fastPeriods=(5..35 step 2).toList(); val slowPeriods=(40..240 step 10).toList()
        val fastValues=fastPeriods.associateWith{ema(it)};val slowValues=slowPeriods.associateWith{ema(it)}
        for(f in fastPeriods) for(s in slowPeriods) if(f<s){val a=fastValues[f]!!;val b=slowValues[s]!!;vote("EMA GRID",if(a>b)1.0 else -1.0)}
        for(period in 5..100) { val e=ema(period); val old=ema(period,close.dropLast(5));vote("EMA SLOPE",if(e>old)1.0 else -1.0) }

        // RSI period/threshold matrix; equal-weight family aggregation prevents period-count domination.
        for(period in 5..100){val x=rsi(period);for(os in listOf(20.0,25.0,30.0,35.0)){
            val ob=100.0-os
            val v=when{ x<=os -> 1.0; x>=ob -> -1.0; x>55 -> 0.5; x<45 -> -0.5; else -> 0.0 }
            vote("RSI GRID",v)
        }}

        // MACD fast/slow/signal grid.
        for(f in listOf(5,7,9,12,15,18)) for(s in listOf(21,26,30,34,40,50,60)) if(f<s){
            val fastLine=emaSeries(f,close);val slowLine=emaSeries(s,close)
            val macdLine=fastLine.zip(slowLine){a,b->a-b}
            for(sig in listOf(5,7,9,12)){
                val signalLine=ema(sig,macdLine);val macd=macdLine.last()
                vote("MACD GRID",when{macd>signalLine && macd>0->1.0;macd<signalLine && macd<0->-1.0;macd>0->0.5;macd<0->-0.5;else->0.0})
            }
        }

        // Bollinger mean-reversion / expansion matrix.
        for(period in 10..60 step 2){val xs=close.takeLast(period);val m=mean(xs);val sd=stdev(xs).coerceAtLeast(1e-12);val z=(price-m)/sd
            for(mult in listOf(1.5,1.8,2.0,2.2,2.5)){vote("BOLLINGER GRID",when{z<=-mult->1.0;z>=mult->-1.0;z>0.5->0.35;z< -0.5->-0.35;else->0.0})}}

        // Donchian breakout windows.
        for(period in 10..200 step 5){val hh=high.takeLast(period).maxOrNull()!!;val ll=low.takeLast(period).minOrNull()!!;val priorHigh=high.dropLast(1).takeLast(period).maxOrNull()!!;val priorLow=low.dropLast(1).takeLast(period).minOrNull()!!
            vote("DONCHIAN BREAKOUT",when{price>priorHigh->1.0;price<priorLow->-1.0;price>(hh+ll)/2->0.25;price<(hh+ll)/2->-0.25;else->0.0})}

        // ATR-normalized trend / volatility stop variants.
        for(period in listOf(7,10,14,20,28,40,55)) for(mult in listOf(1.5,2.0,2.5,3.0,3.5,4.0)){
            val xs=candles.takeLast(period+1);val atr=xs.drop(1).mapIndexed{i,c->val prev=xs[i];maxOf(c.high-c.low,abs(c.high-prev.close),abs(c.low-prev.close))}.average()
            val basis=ema(period);vote("ATR TREND GRID",when{price>basis+atr*mult*0.15->1.0;price<basis-atr*mult*0.15->-1.0;price>basis->0.25;price<basis->-0.25;else->0.0})}

        // ADX/DI directional grid.
        for(period in listOf(7,10,14,20,28,40)) for(threshold in listOf(15.0,20.0,25.0,30.0,35.0)){
            val xs=candles.takeLast(period+1);var plus=0.0;var minus=0.0;var tr=0.0
            for(i in 1 until xs.size){val a=xs[i-1];val b=xs[i];val up=b.high-a.high;val dn=a.low-b.low;plus+=if(up>dn&&up>0)up else 0.0;minus+=if(dn>up&&dn>0)dn else 0.0;tr+=maxOf(b.high-b.low,abs(b.high-a.close),abs(b.low-a.close))}
            val pdi=if(tr>0)plus/tr*100 else 0.0;val mdi=if(tr>0)minus/tr*100 else 0.0;val dx=if(pdi+mdi>0)abs(pdi-mdi)/(pdi+mdi)*100 else 0.0
            vote("ADX DI GRID",if(dx>=threshold)if(pdi>mdi)1.0 else -1.0 else 0.0)}

        // Regression trendlines on multiple horizons, normalized by price.
        var latestSlope=0.0
        for(window in 20..300 step 5){val xs=close.takeLast(window);val mx=(window-1)/2.0;val my=mean(xs);var num=0.0;var den=0.0
            xs.forEachIndexed{i,v->num+=(i-mx)*(v-my);den+=(i-mx)*(i-mx)};val slope=if(den>0)num/den else 0.0
            val slopePct=if(price>0)slope*window/price*100.0 else 0.0;if(window==100)latestSlope=slopePct
            vote("REGRESSION TRENDLINES",when{slopePct>0.35->1.0;slopePct< -0.35->-1.0;else->0.0})}

        // Volume impulse and money-flow direction.
        for(period in 5..100 step 5){val xs=candles.takeLast(period);val up=xs.filter{it.close>it.open}.sumOf{it.volume};val down=xs.filter{it.close<it.open}.sumOf{it.volume};val total=up+down
            vote("VOLUME FLOW GRID",if(total>0)((up-down)/total).coerceIn(-1.0,1.0) else 0.0)}

        // Fibonacci retracement confluence across several swing windows.
        for(window in listOf(34,55,89,120,160,200,240,300)) {val hh=high.takeLast(window).maxOrNull()!!;val ll=low.takeLast(window).minOrNull()!!;val range=hh-ll
            if(range>0){val pos=(price-ll)/range;val nearest=listOf(0.236,0.382,0.5,0.618,0.786).minBy{abs(pos-it)};vote("FIBONACCI CONFLUENCE",when{pos<nearest && pos<0.5->1.0;pos>nearest && pos>0.5->-1.0;else->0.0})}}

        val familyScores=families.mapValues{(_,v)->v.average()*100.0}
        val score=familyScores.values.average().takeIf{it.isFinite()}?:0.0
        val bias=when{score>=15.0->"BULLISH";score<=-15.0->"BEARISH";else->"NEUTRAL"}
        val support=low.takeLast(200).minOrNull()?:return null;val resistance=high.takeLast(200).maxOrNull()?:return null
        val warnings=buildList { if(familyScores.values.count{abs(it)>=20.0}<3)add("Fewer than three parameter families show a meaningful directional edge.");if(familyScores.values.any{it>25}&&familyScores.values.any{it< -25})add("Trend, momentum and mean-reversion parameter families conflict.") }
        return TechnicalEnsembleReport(count,score,bias,familyScores,latestSlope,support,resistance,warnings)
    }
}
