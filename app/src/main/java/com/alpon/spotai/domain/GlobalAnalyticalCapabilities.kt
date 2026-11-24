package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle
import kotlin.math.abs
import kotlin.math.sqrt

/** Additive research diagnostics; uses supplied market data only and never claims causal proof. */
data class GlobalAnalyticalCapabilitiesReport(
    val causality: String, val causalityDetail: String,
    val horizonMatrix: String, val horizonDetail: String,
    val hiddenPatterns: String, val hiddenPatternDetail: String,
    val contagion: String, val contagionDetail: String,
    val thesisBreaker: String, val thesisBreakerDetail: String,
    val forecastDistribution: String, val forecastDistributionDetail: String,
    val scientificGate: String, val scientificGateDetail: String
)

object GlobalAnalyticalCapabilitiesEngine {
    fun evaluate(
        candles: List<Candle>, benchmarkCandles: List<Candle>, mtf: MultiTimeframeSignal?,
        analysts: List<AnalystOpinion>, walkForward: WalkForwardResult?
    ): GlobalAnalyticalCapabilitiesReport {
        val closed = candles.filter { it.closeTime > 0L && it.closeTime <= System.currentTimeMillis() &&
            it.close.isFinite() && it.close > 0.0 && it.high.isFinite() && it.low.isFinite() && it.volume.isFinite() && it.volume >= 0.0 }
            .sortedBy { it.time }
        val causalityDetail = if (closed.size >= 40) {
            "Observed candle returns and volume proxies are available (${closed.size} closed candles). This layer describes associations only; causal attribution requires controlled tests and external factors."
        } else "Insufficient closed candles for association diagnostics. No cause is inferred."
        val causality = if (closed.size >= 40) "ASSOCIATION PROXIES ONLY" else "WARM-UP / UNAVAILABLE"

        val frames = mtf?.readings.orEmpty().filter { it.label.isNotBlank() }
        val horizonMatrix = if (frames.isNotEmpty()) "MULTI-TIMEFRAME READINGS PRESENT (${frames.size})" else "UNAVAILABLE"
        val horizonDetail = if (frames.isNotEmpty()) frames.joinToString(" • ") { "${it.label}: ${it.direction} (${it.score})" } + ". Directions are supplied MTF outputs; this layer does not create new timeframe candles." else "No multi-timeframe readings supplied; multiple horizons are not fabricated."

        val pattern = discoverPattern(closed)
        val hiddenPatterns = pattern.first
        val hiddenPatternDetail = pattern.second

        val contagion = "LIMITED CROSS-ASSET PROXY"
        val contagionDetail = correlationDetail(closed, benchmarkCandles)

        val directional = analysts.filter { it.direction.uppercase() in setOf("BUY", "SELL", "BULLISH", "BEARISH") }
        val bulls = directional.count { it.direction.uppercase() in setOf("BUY", "BULLISH") }
        val bears = directional.count { it.direction.uppercase() in setOf("SELL", "BEARISH") }
        val thesisBreaker = when {
            directional.isEmpty() -> "NO THESIS TO BREAK"
            bulls > 0 && bears > 0 -> "COUNTER-EVIDENCE FOUND"
            else -> "COUNTER-EVIDENCE LIMITED"
        }
        val opposingAnalysts = directional.filter {
            if (bulls >= bears) it.direction.uppercase() in setOf("SELL", "BEARISH")
            else it.direction.uppercase() in setOf("BUY", "BULLISH")
        }.take(3).joinToString("; ") { it.analyst + ": " + it.rationale }
        val thesisBreakerDetail = "Opposing rule-based analyst count: bullish=$bulls, bearish=$bears. ${opposingAnalysts.ifBlank { "No opposing analyst rationale available." }} This is a rules-based countercheck, not an adversarial AI model."

        val forecast = forecastFiveBar(closed)
        val forecastDistribution = forecast.first
        val forecastDistributionDetail = forecast.second

        val scientificGate = when {
            walkForward == null -> "BLOCKED — WALK-FORWARD NOT RUN"
            walkForward.totalTestFolds <= 0 -> "BLOCKED — NO TEST FOLDS"
            walkForward.profitableTestFolds < (walkForward.totalTestFolds / 2.0) -> "REVIEW — WEAK OOS FOLDS"
            walkForward.averageOutOfSampleDrawdownPercent > 8.0 -> "REVIEW — HIGH OOS DRAWDOWN"
            else -> "EVIDENCE AVAILABLE — NOT AUTOMATICALLY PROMOTED"
        }
        val scientificGateDetail = if (walkForward == null) "Walk-forward validation result missing. No promotion allowed." else "Test folds ${walkForward.profitableTestFolds}/${walkForward.totalTestFolds}; average out-of-sample drawdown ${"%.2f%%".format(walkForward.averageOutOfSampleDrawdownPercent)}. This is only one validation input; it does not certify strategy quality."
        return GlobalAnalyticalCapabilitiesReport(causality, causalityDetail, horizonMatrix, horizonDetail, hiddenPatterns, hiddenPatternDetail, contagion, contagionDetail, thesisBreaker, thesisBreakerDetail, forecastDistribution, forecastDistributionDetail, scientificGate, scientificGateDetail)
    }

    private fun discoverPattern(c: List<Candle>): Pair<String, String> {
        val n = 8; val forward = 5
        if (c.size < n * 3 + forward) return "WARM-UP / INSUFFICIENT HISTORY" to "Pattern search requires at least ${n*3+forward} closed candles."
        fun vector(w: List<Candle>): DoubleArray {
            val base = w.first().close.takeIf { it > 0.0 } ?: 1.0
            val returns = w.zipWithNext().map { (a,b) -> b.close/a.close-1.0 }
            val avgRange = w.map { (it.high-it.low)/it.close }.average()
            val avgVol = w.map { it.volume }.average().coerceAtLeast(1e-12)
            return doubleArrayOf(w.last().close/base-1.0, returns.map { abs(it) }.average(), avgRange, w.last().volume/avgVol)
        }
        val current = vector(c.takeLast(n)); var matches=0; var futureReturns=mutableListOf<Double>();
        val lastSafe = c.size-n-forward
        for (i in 0..lastSafe) {
            val past = c.subList(i, i+n)
            val f = vector(past)
            val distance = sqrt(current.indices.sumOf { k -> val scale = when(k){0->0.04;1->0.02;2->0.02;else->1.0}; val d=(current[k]-f[k])/scale; d*d })
            if (distance < 2.2) { matches++; val entry=past.last().close; val future=c[i+n+forward-1].close; if(entry>0 && future.isFinite()) futureReturns += (future/entry-1.0)*100.0 }
        }
        val detail = if(futureReturns.isEmpty()) "No sufficiently similar historical pattern matched the current 8-candle feature shape; heuristic search only." else "Heuristic matches=$matches; average subsequent ${forward}-candle return ${"%.3f%%".format(futureReturns.average())}; matched sample is not predictive evidence and may be regime-dependent."
        return (if(matches>0) "PATTERN ANALOGS FOUND ($matches)" else "NO CLOSE ANALOGS") to detail
    }

    private fun correlationDetail(a: List<Candle>, b: List<Candle>): String {
        if(a.size<30 || b.size<30) return "Benchmark candles not supplied or sample too short; cross-asset contagion is unavailable rather than inferred."
        val bm=b.associateBy { it.time }; val pairs=a.zipWithNext().mapNotNull { (x,y) -> val bx=bm[x.time] ?: return@mapNotNull null; val by=bm[y.time] ?: return@mapNotNull null; if(x.close<=0 || y.close<=0 || bx.close<=0 || by.close<=0) null else (y.close/x.close-1.0) to (by.close/bx.close-1.0) }
        if(pairs.size<25) return "Only ${pairs.size} aligned return pairs; insufficient for cross-asset association."
        val xs=pairs.map{it.first}; val ys=pairs.map{it.second}; val mx=xs.average(); val my=ys.average(); val cov=xs.indices.sumOf{(xs[it]-mx)*(ys[it]-my)}; val dx=sqrt(xs.sumOf{(it-mx)*(it-mx)}); val dy=sqrt(ys.sumOf{(it-my)*(it-my)}); val r=if(dx*dy>0) cov/(dx*dy) else 0.0
        return "Descriptive aligned-return correlation=${"%.3f".format(r.coerceIn(-1.0,1.0))} across ${pairs.size} pairs. Correlation is not causal proof and no propagation model is asserted."
    }

    private fun forecastFiveBar(c: List<Candle>): Pair<String, String> {
        val horizon=5
        if(c.size<horizon*4) return "WARM-UP / INSUFFICIENT SAMPLE" to "Historical forward-return distribution needs at least ${horizon*4} closed candles."
        val returns=(0 until c.size-horizon).mapNotNull { i -> val p=c[i].close; val q=c[i+horizon].close; if(p>0 && q.isFinite()) (q/p-1.0)*100.0 else null }.filter{it.isFinite()}.takeLast(250)
        if(returns.size<20) return "INSUFFICIENT SAMPLES" to "Only ${returns.size} historical forward observations available; distribution withheld."
        val sorted=returns.sorted(); fun percentile(p:Double)=sorted[(p*(sorted.size-1)).toInt().coerceIn(0,sorted.lastIndex)]
        val detail="Empirical ${horizon}-candle return sample n=${returns.size}; median ${"%.3f%%".format(percentile(.50))}, 10th percentile ${"%.3f%%".format(percentile(.10))}, 90th percentile ${"%.3f%%".format(percentile(.90))}. Historical range distribution, not a forecast guarantee; horizon uses current selected candle timeframe."
        return "EMPIRICAL RANGE AVAILABLE" to detail
    }
}
