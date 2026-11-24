package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Walk-forward probabilistic research model.
 * Every test fold is predicted by a model trained only on observations that
 * were available before that fold. This model is shadow evidence only.
 */
data class ProbabilisticForecastReport(
    val available: Boolean,
    val probabilityUp: Double?,
    val balancedAccuracyPct: Double?,
    val brierScore: Double?,
    val baselineBrierScore: Double?,
    val calibrationErrorPct: Double?,
    val outOfSampleReturnPct: Double?,
    val outOfSampleDrawdownPct: Double?,
    val outOfSampleTrades: Int,
    val testSamples: Int,
    val folds: Int,
    val acceptedAsEvidence: Boolean,
    val note: String
)

object ProbabilisticForecastEngine {
    private const val MIN_CANDLES = 420
    private const val MIN_TRAIN_SAMPLES = 180
    private const val FOLD_COUNT = 4
    private const val ROUND_TRIP_COST = 0.002
    private const val LONG_THRESHOLD = 0.55
    private const val SHORT_THRESHOLD = 0.45
    private const val FEATURE_COUNT = 11

    private data class Example(val features: DoubleArray, val label: Int, val nextReturn: Double)
    private data class Prediction(val probability: Double, val label: Int, val nextReturn: Double, val baselineProbability: Double)
    private data class LogisticModel(
        val weights: DoubleArray,
        val bias: Double,
        val means: DoubleArray,
        val scales: DoubleArray
    ) {
        fun predict(raw: DoubleArray): Double {
            val z = bias + weights.indices.sumOf { i ->
                weights[i] * ((raw[i] - means[i]) / scales[i]).coerceIn(-8.0, 8.0)
            }
            return sigmoid(z)
        }
    }

    private fun sigmoid(value: Double): Double {
        val z = value.coerceIn(-30.0, 30.0)
        return 1.0 / (1.0 + exp(-z))
    }

    private fun validCandles(input: List<Candle>, now: Long): List<Candle> =
        input.asSequence().filter {
            it.closeTime > 0L && it.closeTime <= now &&
                it.open.isFinite() && it.high.isFinite() && it.low.isFinite() &&
                it.close.isFinite() && it.volume.isFinite() &&
                it.open > 0.0 && it.close > 0.0 && it.low > 0.0 &&
                it.high >= max(it.open, it.close) && it.low <= min(it.open, it.close) && it.volume >= 0.0
        }.sortedBy { it.time }.distinctBy { it.time }.toList().takeLast(1800)

    private fun features(c: List<Candle>, i: Int): DoubleArray? {
        if (i < 30 || i >= c.size) return null
        val close = c[i].close
        if (!close.isFinite() || close <= 0.0) return null
        fun logReturn(n: Int): Double {
            val old = c[i - n].close
            return if (old > 0.0) ln(close / old) else 0.0
        }
        val returns = (i - 23..i).map { k ->
            val previous = c[k - 1].close
            if (previous > 0.0) ln(c[k].close / previous) else 0.0
        }.filter { it.isFinite() }
        if (returns.size < 20) return null
        val mean = returns.average()
        val vol24 = sqrt(returns.sumOf { (it - mean) * (it - mean) } / returns.size)
        val short = returns.takeLast(12)
        val shortMean = short.average()
        val vol12 = sqrt(short.sumOf { (it - shortMean) * (it - shortMean) } / short.size)
        val sma12 = c.subList(i - 11, i + 1).map { it.close }.average()
        val sma26 = c.subList(i - 25, i + 1).map { it.close }.average()
        val trendGap = if (sma26 > 0.0) sma12 / sma26 - 1.0 else 0.0
        val recentRange = c.subList(i - 11, i + 1).map { (it.high - it.low) / it.close }.average()
        val priorRange = c.subList(i - 29, i - 11).map { (it.high - it.low) / it.close }.average().coerceAtLeast(1e-8)
        val volumeWindow = c.subList(i - 19, i + 1).map { it.volume }.sorted()
        val medianVolume = volumeWindow[volumeWindow.size / 2].coerceAtLeast(1e-12)
        val volumeRatio = c[i].volume / medianVolume
        val changes = (i - 13 until i).map { c[it + 1].close - c[it].close }
        val gains = changes.sumOf { max(it, 0.0) }
        val losses = changes.sumOf { max(-it, 0.0) }
        val rsi = when {
            losses <= 1e-12 && gains > 0.0 -> 100.0
            gains <= 1e-12 -> 0.0
            else -> 100.0 - 100.0 / (1.0 + gains / losses)
        }
        val result = doubleArrayOf(
            logReturn(1).coerceIn(-0.15, 0.15),
            logReturn(3).coerceIn(-0.25, 0.25),
            logReturn(6).coerceIn(-0.35, 0.35),
            logReturn(12).coerceIn(-0.50, 0.50),
            logReturn(24).coerceIn(-0.75, 0.75),
            vol12.coerceIn(0.0, 0.20),
            vol24.coerceIn(0.0, 0.25),
            trendGap.coerceIn(-0.25, 0.25),
            ln((recentRange / priorRange).coerceIn(0.05, 20.0)),
            ((rsi - 50.0) / 50.0).coerceIn(-1.0, 1.0),
            ln(1.0 + volumeRatio.coerceIn(0.0, 20.0))
        )
        return result.takeIf { it.size == FEATURE_COUNT && it.all { v -> v.isFinite() } }
    }

    private fun fit(rows: List<Example>): LogisticModel? {
        if (rows.size < MIN_TRAIN_SAMPLES) return null
        val means = DoubleArray(FEATURE_COUNT) { k -> rows.map { it.features[k] }.average() }
        val scales = DoubleArray(FEATURE_COUNT) { k ->
            sqrt(rows.sumOf { (it.features[k] - means[k]) * (it.features[k] - means[k]) } / rows.size).coerceAtLeast(1e-4)
        }
        val weights = DoubleArray(FEATURE_COUNT)
        var bias = 0.0
        repeat(100) { epoch ->
            val gradient = DoubleArray(FEATURE_COUNT)
            var biasGradient = 0.0
            rows.forEach { row ->
                val x = DoubleArray(FEATURE_COUNT) { k -> ((row.features[k] - means[k]) / scales[k]).coerceIn(-8.0, 8.0) }
                val p = sigmoid(bias + weights.indices.sumOf { weights[it] * x[it] })
                val error = p - row.label.toDouble()
                for (k in weights.indices) gradient[k] += error * x[k]
                biasGradient += error
            }
            val rate = 0.12 / (1.0 + epoch * 0.015)
            for (k in weights.indices) weights[k] = (weights[k] - rate * (gradient[k] / rows.size + 0.001 * weights[k])).coerceIn(-6.0, 6.0)
            bias = (bias - rate * biasGradient / rows.size).coerceIn(-8.0, 8.0)
        }
        return LogisticModel(weights, bias, means, scales)
    }

    fun evaluate(input: List<Candle>, now: Long = System.currentTimeMillis()): ProbabilisticForecastReport {
        val candles = validCandles(input, now)
        if (candles.size < MIN_CANDLES) return unavailable("Warm-up: at least $MIN_CANDLES valid closed candles are required; found ${candles.size}.")
        val rows = (30 until candles.lastIndex).mapNotNull { i ->
            val x = features(candles, i) ?: return@mapNotNull null
            val current = candles[i].close
            val next = candles[i + 1].close
            if (current <= 0.0 || !next.isFinite() || next <= 0.0) null
            else Example(x, if (next > current) 1 else 0, next / current - 1.0)
        }
        if (rows.size < MIN_TRAIN_SAMPLES + 120) return unavailable("Insufficient clean examples for chronological validation: ${rows.size} usable samples.")

        val firstTest = (rows.size * 0.55).toInt().coerceAtLeast(MIN_TRAIN_SAMPLES)
        val predictions = mutableListOf<Prediction>()
        var completedFolds = 0
        for (fold in 0 until FOLD_COUNT) {
            val start = firstTest + (rows.size - firstTest) * fold / FOLD_COUNT
            val end = firstTest + (rows.size - firstTest) * (fold + 1) / FOLD_COUNT
            if (end <= start || end - start < 20) continue
            val training = rows.take(start)
            val model = fit(training) ?: continue
            val baseline = training.count { it.label == 1 }.toDouble() / training.size
            for (i in start until end) {
                val row = rows[i]
                predictions += Prediction(model.predict(row.features).coerceIn(0.0, 1.0), row.label, row.nextReturn, baseline.coerceIn(0.0, 1.0))
            }
            completedFolds++
        }
        if (completedFolds < 3 || predictions.size < 100) return unavailable("Walk-forward validation did not produce enough independent test observations.")

        val positives = predictions.count { it.label == 1 }
        val negatives = predictions.size - positives
        val tp = predictions.count { it.label == 1 && it.probability >= 0.5 }
        val tn = predictions.count { it.label == 0 && it.probability < 0.5 }
        val balanced = if (positives > 0 && negatives > 0) ((tp.toDouble() / positives) + (tn.toDouble() / negatives)) / 2.0 else 0.0
        val brier = predictions.map { (it.probability - it.label).let { e -> e * e } }.average()
        val baselineBrier = predictions.map { (it.baselineProbability - it.label).let { e -> e * e } }.average()
        val ece = expectedCalibrationError(predictions)

        var equity = 1.0
        var peak = 1.0
        var drawdown = 0.0
        var trades = 0
        predictions.forEach { p ->
            val side = when { p.probability >= LONG_THRESHOLD -> 1.0; p.probability <= SHORT_THRESHOLD -> -1.0; else -> 0.0 }
            if (side != 0.0) {
                equity *= 1.0 + (side * p.nextReturn - ROUND_TRIP_COST).coerceAtLeast(-0.99)
                trades++
            }
            peak = max(peak, equity)
            drawdown = max(drawdown, (peak - equity) / peak * 100.0)
        }
        val oosReturn = (equity - 1.0) * 100.0
        val finalModel = fit(rows)
        val latest = features(candles, candles.lastIndex)
        val probability = if (finalModel != null && latest != null) finalModel.predict(latest).coerceIn(0.0, 1.0) else null
        val accepted = probability != null && balanced >= 0.525 && brier < baselineBrier && ece <= 0.12 &&
            oosReturn > 0.0 && drawdown <= 15.0 && trades >= 8 && predictions.size >= 100 && completedFolds >= 3
        val note = buildString {
            append(if (accepted) "SHADOW EVIDENCE ACCEPTED" else "SHADOW EVIDENCE REJECTED")
            append(" • folds ").append(completedFolds)
            append(" • balanced accuracy ").append("%.1f%%".format(balanced * 100.0))
            append(" • Brier ").append("%.4f".format(brier)).append(" vs baseline ").append("%.4f".format(baselineBrier))
            append(" • ECE ").append("%.1f%%".format(ece * 100.0))
            append(" • research return ").append("%.2f%%".format(oosReturn))
            append(" • drawdown ").append("%.2f%%".format(drawdown)).append(" • trades ").append(trades)
            append(". Out-of-sample diagnostics only; current raw score is not a calibrated win probability.")
        }
        return ProbabilisticForecastReport(true, probability, balanced * 100.0, brier, baselineBrier, ece * 100.0,
            oosReturn, drawdown, trades, predictions.size, completedFolds, accepted, note)
    }

    private fun expectedCalibrationError(rows: List<Prediction>): Double {
        if (rows.isEmpty()) return 1.0
        var total = 0.0
        for (bin in 0 until 10) {
            val lower = bin / 10.0
            val upper = (bin + 1) / 10.0
            val group = rows.filter { it.probability >= lower && (it.probability < upper || bin == 9 && it.probability <= upper) }
            if (group.isNotEmpty()) {
                val predicted = group.map { it.probability }.average()
                val observed = group.count { it.label == 1 }.toDouble() / group.size
                total += group.size.toDouble() / rows.size * kotlin.math.abs(predicted - observed)
            }
        }
        return total.coerceIn(0.0, 1.0)
    }

    private fun unavailable(reason: String) = ProbabilisticForecastReport(
        false, null, null, null, null, null, null, null, 0, 0, 0, false, reason
    )
}
