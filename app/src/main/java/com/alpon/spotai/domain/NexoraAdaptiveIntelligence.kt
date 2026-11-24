package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** Results from three isolated research systems. None can place or authorize a live order. */
data class DeepLearningReport(
    val available: Boolean, val probabilityUp: Double?, val holdoutAccuracy: Double?,
    val balancedAccuracy: Double?, val trainSamples: Int, val testSamples: Int,
    val acceptedAsEvidence: Boolean, val note: String
)
data class ReinforcementLearningReport(
    val available: Boolean, val outOfSampleReturnPct: Double?, val outOfSampleDrawdownPct: Double?,
    val outOfSampleTrades: Int, val winRatePct: Double?, val acceptedAsEvidence: Boolean,
    val note: String,
    val probabilisticForecast: ProbabilisticForecastReport? = null
)
data class StrategyEvolutionReport(
    val available: Boolean, val fastPeriod: Int?, val slowPeriod: Int?,
    val baselineOutOfSampleReturnPct: Double?, val evolvedOutOfSampleReturnPct: Double?,
    val outOfSampleDrawdownPct: Double?, val outOfSampleTrades: Int,
    val promotedToShadow: Boolean, val note: String
)
data class NexoraAdaptiveReport(
    val deepLearning: DeepLearningReport,
    val reinforcementLearning: ReinforcementLearningReport,
    val strategyEvolution: StrategyEvolutionReport,
    val note: String,
    val probabilisticForecast: ProbabilisticForecastReport? = null
)

/**
 * Small, deterministic and auditable research implementations:
 * - one-hidden-layer neural network trained on chronological candle features;
 * - tabular Q-learning trained only on the training segment;
 * - bounded evolutionary EMA search, selected on training and judged once on holdout.
 * These are research prototypes, not production-grade forecasts or autonomous execution.
 */
object NexoraAdaptiveIntelligence {
    private const val FEE_RATE = 0.001
    private const val MIN_CANDLES = 420
    private data class CacheKey(val count: Int, val firstTime: Long, val lastTime: Long, val lastCloseBits: Long, val contentHash: Int)
    private val cacheLock = Any()
    private var cachedKey: CacheKey? = null
    private var cachedReport: NexoraAdaptiveReport? = null

    private data class Example(val x: DoubleArray, val label: Int)
    private fun valid(c: List<Candle>): List<Candle> = c.asSequence()
        .filter { it.closeTime > 0L && it.closeTime <= System.currentTimeMillis() && it.close > 0.0 &&
            it.open.isFinite() && it.high.isFinite() && it.low.isFinite() && it.close.isFinite() &&
            it.volume.isFinite() && it.volume >= 0.0 && it.high >= max(it.open, it.close) && it.low <= min(it.open, it.close) }
        .sortedBy { it.time }.distinctBy { it.time }.toList().takeLast(1200)

    private fun features(c: List<Candle>, i: Int): DoubleArray? {
        if (i < 24 || i >= c.size) return null
        val closes = c.map { it.close }
        val r1 = closes[i] / closes[i - 1] - 1.0
        val r3 = closes[i] / closes[i - 3] - 1.0
        val r8 = closes[i] / closes[i - 8] - 1.0
        val returns = (i - 12 until i).map { closes[it] / closes[it - 1] - 1.0 }
        val mean = returns.average()
        val vol = sqrt(returns.sumOf { (it - mean) * (it - mean) } / returns.size)
        val vols = (i - 20 until i).map { c[it].volume }
        val med = vols.sorted()[vols.size / 2].coerceAtLeast(1e-12)
        val volumeRatio = (c[i].volume / med).coerceIn(0.0, 10.0)
        return doubleArrayOf(
            (r1 * 100.0).coerceIn(-10.0, 10.0),
            (r3 * 100.0).coerceIn(-15.0, 15.0),
            (r8 * 100.0).coerceIn(-20.0, 20.0),
            (vol * 100.0).coerceIn(0.0, 10.0),
            ln(1.0 + volumeRatio).coerceIn(0.0, 3.0)
        )
    }

    private class TinyMlp(seed: Int) {
        private val random = Random(seed)
        private val w1 = Array(8) { DoubleArray(5) { (random.nextDouble() - .5) * .25 } }
        private val b1 = DoubleArray(8)
        private val w2 = DoubleArray(8) { (random.nextDouble() - .5) * .25 }
        private var b2 = 0.0
        private fun sigmoid(v: Double) = 1.0 / (1.0 + exp(-v.coerceIn(-30.0, 30.0)))
        fun predict(x: DoubleArray): Double {
            val h = DoubleArray(8) { j -> kotlin.math.tanh(b1[j] + (0 until 5).sumOf { k -> w1[j][k] * x[k] }) }
            return sigmoid(b2 + (0 until 8).sumOf { j -> w2[j] * h[j] })
        }
        fun train(rows: List<Example>, means: DoubleArray, scales: DoubleArray) {
            repeat(45) { epoch ->
                val rate = .018 / (1.0 + epoch * .012)
                rows.forEach { row ->
                    val x = DoubleArray(5) { (row.x[it] - means[it]) / scales[it] }
                    val h = DoubleArray(8) { j -> kotlin.math.tanh(b1[j] + (0 until 5).sumOf { k -> w1[j][k] * x[k] }) }
                    val p = sigmoid(b2 + (0 until 8).sumOf { j -> w2[j] * h[j] })
                    val err = (p - row.label.toDouble()).coerceIn(-1.0, 1.0)
                    val oldW2 = w2.copyOf()
                    for (j in 0 until 8) {
                        w2[j] -= rate * (err * h[j] + .0005 * w2[j])
                        val dh = err * oldW2[j] * (1.0 - h[j] * h[j])
                        for (k in 0 until 5) w1[j][k] -= rate * (dh * x[k] + .0005 * w1[j][k])
                        b1[j] -= rate * dh
                    }
                    b2 -= rate * err
                }
            }
        }
        fun predictScaled(x: DoubleArray, means: DoubleArray, scales: DoubleArray) = predict(DoubleArray(5) { (x[it] - means[it]) / scales[it] })
    }

    private fun deepLearning(c: List<Candle>): DeepLearningReport {
        if (c.size < MIN_CANDLES) return DeepLearningReport(false, null, null, null, 0, 0, false, "Warm-up: at least $MIN_CANDLES valid closed candles are required.")
        val rows = (24 until c.lastIndex).mapNotNull { i ->
            val x = features(c, i) ?: return@mapNotNull null
            val next = c[i + 1].close / c[i].close - 1.0
            if (!next.isFinite()) null else Example(x, if (next > 0.0) 1 else 0)
        }
        val split = (rows.size * .70).toInt()
        val train = rows.take(split); val test = rows.drop(split)
        if (train.size < 200 || test.size < 60) return DeepLearningReport(false, null, null, null, train.size, test.size, false, "Chronological training/holdout sample is too small.")
        val means = DoubleArray(5) { k -> train.map { it.x[k] }.average() }
        val scales = DoubleArray(5) { k -> sqrt(train.sumOf { (it.x[k] - means[k]) * (it.x[k] - means[k]) } / train.size).coerceAtLeast(.05) }
        val model = TinyMlp(731)
        model.train(train, means, scales)
        val predictions = test.map { model.predictScaled(it.x, means, scales) }
        val accuracy = predictions.indices.count { (predictions[it] >= .5) == (test[it].label == 1) }.toDouble() / test.size
        val positives = test.indices.filter { test[it].label == 1 }
        val negatives = test.indices.filter { test[it].label == 0 }
        val tpr = if (positives.isEmpty()) 0.0 else positives.count { predictions[it] >= .5 }.toDouble() / positives.size
        val tnr = if (negatives.isEmpty()) 0.0 else negatives.count { predictions[it] < .5 }.toDouble() / negatives.size
        val balanced = (tpr + tnr) / 2.0
        val latest = features(c, c.lastIndex)
        val probability = latest?.let { model.predictScaled(it, means, scales) }
        val accepted = balanced >= .525 && test.size >= 60 && probability != null
        return DeepLearningReport(true, probability, accuracy * 100.0, balanced * 100.0, train.size, test.size, accepted,
            if (accepted) "Holdout balanced accuracy ${"%.1f".format(balanced * 100)}%; neural output is eligible as low-weight shadow evidence only." else "Neural model failed the 52.5% balanced-accuracy gate; output is not used as directional evidence.")
    }

    private data class SimResult(val ret: Double, val drawdown: Double, val trades: Int, val wins: Int) {
        val winRate: Double get() = if (trades == 0) 0.0 else wins * 100.0 / trades
    }
    private fun simulateActions(c: List<Candle>, start: Int, end: Int, actionAt: (Int) -> Int): SimResult {
        var equity = 1.0; var peak = 1.0; var dd = 0.0
        var prior = 0; var trades = 0; var wins = 0; var tradeEquity = 1.0
        for (i in start until end) {
            val action = actionAt(i).coerceIn(0, 1)
            val r = c[i + 1].close / c[i].close - 1.0
            if (action != prior) {
                equity *= (1.0 - FEE_RATE)
                if (action == 1) tradeEquity = 1.0 - FEE_RATE
                if (prior == 1 && action == 0) {
                    tradeEquity *= (1.0 - FEE_RATE)
                    trades++
                    if (tradeEquity > 1.0) wins++
                    tradeEquity = 1.0
                }
                prior = action
            }
            if (action == 1) {
                val factor = (1.0 + r).coerceAtLeast(.01)
                equity *= factor
                tradeEquity *= factor
            }
            peak = max(peak, equity)
            dd = max(dd, (peak - equity) / peak * 100.0)
        }
        if (prior == 1) {
            tradeEquity *= (1.0 - FEE_RATE)
            trades++
            if (tradeEquity > 1.0) wins++
        }
        return SimResult((equity - 1.0) * 100.0, dd, trades, wins)
    }

    private fun state(c: List<Candle>, i: Int): Int {
        val momentum = c[i].close / c[i - 8].close - 1.0
        val returns = (i - 12 until i).map { c[it].close / c[it - 1].close - 1.0 }
        val m = returns.average(); val vol = sqrt(returns.sumOf { (it - m) * (it - m) } / returns.size)
        val trend = when { momentum > .004 -> 2; momentum < -.004 -> 0; else -> 1 }
        val volatility = when { vol > .012 -> 2; vol < .003 -> 0; else -> 1 }
        return trend * 3 + volatility
    }

    private fun reinforcementLearning(c: List<Candle>): ReinforcementLearningReport {
        if (c.size < MIN_CANDLES) return ReinforcementLearningReport(false, null, null, 0, null, false, "Warm-up: at least $MIN_CANDLES valid closed candles are required.")
        val split = (c.size * .70).toInt().coerceIn(100, c.lastIndex - 60)
        val q = Array(9) { DoubleArray(2) }
        val rng = Random(1907)
        var previousAction = 0
        for (i in 24 until split - 1) {
            val s = state(c, i)
            val epsilon = .25 * (1.0 - (i - 24).toDouble() / (split - 24)).coerceIn(.05, 1.0)
            val action = if (rng.nextDouble() < epsilon) rng.nextInt(2) else if (q[s][1] > q[s][0]) 1 else 0
            val raw = c[i + 1].close / c[i].close - 1.0
            val reward = (if (action == 1) raw else 0.0) - (if (action != previousAction) FEE_RATE else 0.0)
            val nextState = state(c, i + 1)
            val target = reward + .92 * q[nextState].maxOrNull()!!
            q[s][action] += .12 * (target - q[s][action])
            previousAction = action
        }
        val test = simulateActions(c, split, c.lastIndex) { i -> if (q[state(c, i)][1] > q[state(c, i)][0]) 1 else 0 }
        val accepted = test.trades >= 8 && test.ret > 0.0 && test.drawdown <= 12.0
        return ReinforcementLearningReport(true, test.ret, test.drawdown, test.trades, test.winRate, accepted,
            if (accepted) "Q-learning policy passed preliminary holdout gates; paper/shadow only, not a live execution rule." else "Q-learning did not pass holdout return/trade-count/drawdown gates; keep policy inactive.")
    }

    private fun emaSeries(c: List<Candle>, period: Int): DoubleArray {
        val values = DoubleArray(c.size) { Double.NaN }
        if (c.size < period) return values
        val k = 2.0 / (period + 1.0)
        var value = c.take(period).map { it.close }.average()
        values[period - 1] = value
        for (i in period until c.size) {
            value = c[i].close * k + value * (1.0 - k)
            values[i] = value
        }
        return values
    }
    private fun emaActions(c: List<Candle>, fast: Int, slow: Int, start: Int, end: Int): SimResult {
        val fastValues = emaSeries(c, fast)
        val slowValues = emaSeries(c, slow)
        return simulateActions(c, start, end) { i -> if (fastValues[i] > slowValues[i]) 1 else 0 }
    }

    private fun strategyEvolution(c: List<Candle>): StrategyEvolutionReport {
        if (c.size < MIN_CANDLES) return StrategyEvolutionReport(false, null, null, null, null, null, 0, false, "Warm-up: at least $MIN_CANDLES valid closed candles are required.")
        val split = (c.size * .70).toInt().coerceIn(120, c.lastIndex - 60)
        data class Gene(val fast: Int, val slow: Int)
        fun normalize(f: Int, s: Int): Gene { val fast = f.coerceIn(4, 24); return Gene(fast, s.coerceIn(max(25, fast + 2), 100)) }
        fun trainScore(g: Gene): Double {
            val result = emaActions(c, g.fast, g.slow, 24, split - 1)
            return result.ret - result.drawdown * .65 - max(0, 6 - result.trades) * 1.5
        }
        val rng = Random(42017)
        var population = (0 until 14).map { normalize(rng.nextInt(4, 25), rng.nextInt(27, 101)) }.distinct().toMutableList()
        while (population.size < 14) population += normalize(rng.nextInt(4, 25), rng.nextInt(27, 101))
        repeat(7) {
            val ranked = population.distinct().sortedByDescending { trainScore(it) }.take(5)
            val next = ranked.take(3).toMutableList()
            while (next.size < 14) {
                val a = ranked[rng.nextInt(ranked.size)]; val b = ranked[rng.nextInt(ranked.size)]
                val f = if (rng.nextBoolean()) a.fast else b.fast
                val s = if (rng.nextBoolean()) a.slow else b.slow
                val child = normalize(f + if (rng.nextDouble() < .45) rng.nextInt(-4, 5) else 0,
                    s + if (rng.nextDouble() < .45) rng.nextInt(-12, 13) else 0)
                next += child
            }
            population = next
        }
        val winner = population.distinct().maxByOrNull { trainScore(it) } ?: return StrategyEvolutionReport(false, null, null, null, null, null, 0, false, "No valid candidate was generated.")
        val evolved = emaActions(c, winner.fast, winner.slow, split, c.lastIndex - 1)
        val baseline = emaActions(c, 12, 26, split, c.lastIndex - 1)
        val promoted = evolved.trades >= 10 && evolved.ret > 0.0 && evolved.ret > baseline.ret && evolved.drawdown <= 10.0
        return StrategyEvolutionReport(true, winner.fast, winner.slow, baseline.ret, evolved.ret, evolved.drawdown, evolved.trades, promoted,
            if (promoted) "Candidate beat fixed EMA 12/26 on the untouched holdout and passed preliminary risk gates; promoted to shadow testing only." else "Candidate was not promoted: it failed to beat the fixed baseline or missed holdout trade/return/drawdown gates.")
    }

    fun evaluate(candles: List<Candle>): NexoraAdaptiveReport {
        val c = valid(candles)
        val key = CacheKey(c.size, c.firstOrNull()?.time ?: 0L, c.lastOrNull()?.time ?: 0L,
            c.lastOrNull()?.close?.toBits() ?: 0L, c.hashCode())
        synchronized(cacheLock) {
            if (key == cachedKey) cachedReport?.let { return it }
            val dl = deepLearning(c)
            val rl = reinforcementLearning(c)
            val evolution = strategyEvolution(c)
            val forecast = ProbabilisticForecastEngine.evaluate(c)
            val report = NexoraAdaptiveReport(
                deepLearning = dl,
                reinforcementLearning = rl,
                strategyEvolution = evolution,
                note = "Chronological 70/30 research split. Models are trained locally from historical closed candles; no external training corpus, GPU model, persisted weights or live auto-deployment is used. Every accepted result remains shadow-only until independently revalidated.",
                probabilisticForecast = forecast
            )
            cachedKey = key
            cachedReport = report
            return report
        }
    }
}
