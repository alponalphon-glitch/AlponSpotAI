package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle

/** FX-specific consensus: no synthetic 3D/12H bars and no fabricated FX volume. */
object ForexMultiTimeframeAnalysis {
    fun analyze(frames: Map<String, List<Candle>>): MultiTimeframeSignal? {
        val specs = listOf("1M" to 24, "1W" to 40, "1D" to 600, "4H" to 40, "2H" to 40, "1H" to 40, "15M" to 40)
        val now = System.currentTimeMillis()
        val readings = mutableListOf<TimeframeReading>()
        for ((name, minimum) in specs) {
            val closed = frames[name].orEmpty().filter { it.closeTime in 1..now }.sortedBy { it.time }
            if (closed.size < minimum || closed.zipWithNext().any { it.first.time >= it.second.time }) return null
            val recent = closed.takeLast(120)
            if (recent.any { c -> !c.open.isFinite() || !c.high.isFinite() || !c.low.isFinite() || !c.close.isFinite() || c.low <= 0.0 || c.high < maxOf(c.open, c.close) || c.low > minOf(c.open, c.close) }) return null
            val closes = recent.map { it.close }
            val fast = ema(closes, 9)
            val slow = ema(closes, 21)
            val change = if (closes.size > 10) (closes.last() / closes[closes.size - 11] - 1.0) * 100.0 else 0.0
            val rsiValue = rsi(closes, 14)
            val score = (50 + (if (fast > slow) 12 else -12) + (if (change > 0.0) 10 else if (change < 0.0) -10 else 0) + (if(rsiValue>55.0) 5 else if(rsiValue<45.0) -5 else 0)).coerceIn(0, 100)
            val direction = when { score >= 60 -> "BULLISH"; score <= 40 -> "BEARISH"; else -> "NEUTRAL" }
            readings += TimeframeReading(name, direction, score, change, fast, slow, rsiValue, closed.size, closed.last().closeTime)
        }
        val weights = mapOf("1M" to .22, "1W" to .20, "1D" to .19, "4H" to .14, "2H" to .10, "1H" to .09, "15M" to .06)
        val score = readings.sumOf { it.score * (weights[it.label] ?: 0.0) } / weights.values.sum()
        val higher = readings.filter { it.label in setOf("1M", "1W", "1D") }
        val lower = readings.filter { it.label in setOf("4H", "2H", "1H", "15M") }
        val hb = higher.count { it.direction == "BULLISH" }; val hs = higher.count { it.direction == "BEARISH" }
        val lb = lower.count { it.direction == "BULLISH" }; val ls = lower.count { it.direction == "BEARISH" }
        val action = when {
            score >= 63.0 && hb >= 2 && lb >= 3 && hs == 0 -> "BUY"
            score <= 37.0 && hs >= 2 && ls >= 3 && hb == 0 -> "SELL"
            else -> "WAIT"
        }
        val execution = frames["15M"].orEmpty().filter { it.closeTime <= now }.takeLast(15)
        val price = execution.lastOrNull()?.close ?: return null
        val atr = execution.map { it.high - it.low }.average().takeIf { it.isFinite() && it > 0.0 } ?: return null
        val distance = maxOf(atr * 2.0, price * .0015)
        val stop = when (action) { "BUY" -> price - distance; "SELL" -> price + distance; else -> 0.0 }
        val risk = if (stop > 0.0) kotlin.math.abs(price - stop) else 0.0
        val tp1 = when (action) { "BUY" -> price + risk; "SELL" -> price - risk; else -> 0.0 }
        val tp2 = when (action) { "BUY" -> price + risk * 2; "SELL" -> price - risk * 2; else -> 0.0 }
        val tp3 = when (action) { "BUY" -> price + risk * 3; "SELL" -> price - risk * 3; else -> 0.0 }
        val explanation = when (action) {
            "BUY" -> "FX higher-timeframe bias and lower-timeframe momentum align. Confirm spread, session and event risk before any execution."
            "SELL" -> "FX bearish consensus. This is a Forex directional signal, not a Binance Spot short instruction."
            else -> "Forex timeframes are not sufficiently aligned or the required daily history is incomplete. WAIT."
        }
        return MultiTimeframeSignal(action, score.toInt().coerceIn(0, 100), if (hb >= 2 && lb >= 3) "BULLISH ALIGNMENT" else if (hs >= 2 && ls >= 3) "BEARISH ALIGNMENT" else "MIXED / TRANSITION", "FX context: hours to weeks", if (action == "WAIT") 0.0 else price, stop, tp1, tp2, tp3, "$hb bullish / $hs bearish higher frames • $lb bullish / $ls bearish lower frames", explanation, readings)
    }

    private fun rsi(values:List<Double>,period:Int):Double{
        if(values.size<=period)return 50.0
        var gains=0.0;var losses=0.0
        for(i in values.size-period until values.size){val change=values[i]-values[i-1];if(change>0)gains+=change else losses-=change}
        if(losses<=0.0)return 100.0
        val rs=(gains/period)/(losses/period)
        return 100.0-(100.0/(1.0+rs))
    }

    private fun ema(values: List<Double>, period: Int): Double {
        if (values.isEmpty()) return Double.NaN
        val alpha = 2.0 / (period + 1.0)
        var value = values.first()
        for (item in values.drop(1)) value = item * alpha + value * (1.0 - alpha)
        return value
    }
}
