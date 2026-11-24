package com.alpon.spotai.domain

import com.alpon.spotai.data.Candle

/** One chronological out-of-sample fold. Training and test results are never mixed. */
data class WalkForwardFold(
    val trainingStart: Long,
    val trainingEnd: Long,
    val testStart: Long,
    val testEnd: Long,
    val selectedRewardRisk: Double,
    val trainingTrades: Int,
    val trainingReturnPercent: Double,
    val testTrades: Int,
    val testWins: Int,
    val testWinRate: Double,
    val testReturnPercent: Double,
    val testDrawdownPercent: Double,
    val testProfitFactor: Double?,
    val trainingReliable: Boolean
)

data class WalkForwardWindow(
    val trainingStart: Long,
    val trainingEnd: Long,
    val testStart: Long,
    val testEnd: Long
)

data class WalkForwardResult(
    val folds: List<WalkForwardFold>,
    val outOfSampleTrades: Int,
    val outOfSampleWins: Int,
    val averageOutOfSampleReturnPercent: Double,
    val compoundedOutOfSampleReturnPercent: Double,
    val averageOutOfSampleDrawdownPercent: Double,
    val profitableTestFolds: Int,
    val totalTestFolds: Int,
    val selectedParameters: List<Double>,
    val warning: String
)

/**
 * Chronological walk-forward validation. Each fold tunes reward/risk only on its training
 * window, then freezes that value for the following unseen test window. Tests are run from
 * the first saved candle with trading disabled until the test boundary, so indicators receive
 * their complete causal warm-up without allowing training-period trades into test statistics.
 * Candle pages are streamed; the full history is never retained in memory.
 */
object WalkForwardEngine {
    private val rewardRiskCandidates = listOf(1.5, 2.0, 2.5, 3.0)
    private const val TRAIN_FRACTION = 0.60
    private const val TEST_FRACTION = 0.20
    private const val STEP_FRACTION = 0.20
    private const val MIN_TRAINING_TRADES = 5

    /** Pure chronological split planner, exposed for deterministic boundary tests. */
    fun buildWindows(firstTime: Long, lastTime: Long): List<WalkForwardWindow> {
        if (firstTime >= lastTime) return emptyList()
        val span = lastTime - firstTime
        if (span <= 0L) return emptyList()
        val trainDuration = (span * TRAIN_FRACTION).toLong()
        val testDuration = (span * TEST_FRACTION).toLong()
        val stepDuration = (span * STEP_FRACTION).toLong()
        if (trainDuration <= 0L || testDuration <= 0L || stepDuration <= 0L) return emptyList()
        val windows = mutableListOf<WalkForwardWindow>()
        var offset = 0L
        while (true) {
            val trainStart = firstTime + offset
            val trainEnd = trainStart + trainDuration
            val testStart = trainEnd + 1L
            val testEnd = testStart + testDuration - 1L
            if (testEnd > lastTime || testStart <= trainEnd) break
            windows += WalkForwardWindow(trainStart, trainEnd, testStart, testEnd)
            if (testEnd >= lastTime) break
            offset += stepDuration
            if (offset <= 0L) break
        }
        return windows
    }

    suspend fun run(
        firstTime: Long,
        lastTime: Long,
        interval: String,
        initialCapital: Double = 1000.0,
        riskPercent: Double = 0.5,
        loadPage: suspend (afterTime: Long, untilTime: Long, limit: Int) -> List<Candle>
    ): WalkForwardResult? {
        if (firstTime >= lastTime || initialCapital <= 0.0 || !initialCapital.isFinite() ||
            riskPercent <= 0.0 || riskPercent > 10.0 || !riskPercent.isFinite()) return null

        val windows = buildWindows(firstTime, lastTime)
        if (windows.isEmpty()) return null

        val folds = mutableListOf<WalkForwardFold>()
        for (window in windows) {
            val trainStart = window.trainingStart
            val trainEnd = window.trainingEnd
            val testStart = window.testStart
            val testEnd = window.testEnd

            var bestRewardRisk = rewardRiskCandidates.first()
            var bestScore = Double.NEGATIVE_INFINITY
            var bestTraining: BacktestResult? = null

            for (candidate in rewardRiskCandidates) {
                val training = BacktestingEngine.runFromPages(
                    firstTime = firstTime,
                    interval = interval,
                    initialCapital = initialCapital,
                    riskPercent = riskPercent,
                    rewardRisk = candidate,
                    tradeAfterTime = trainStart
                ) { after, limit -> loadPage(after, trainEnd, limit) } ?: continue

                val reliable = training.totalTrades >= MIN_TRAINING_TRADES
                // Penalize drawdown; reject tiny/no-trade samples from winning by chance.
                val score = if (reliable) {
                    training.totalReturnPercent - training.maxDrawdownPercent * 0.75
                } else {
                    -1_000_000.0 + training.totalTrades
                }
                if (score > bestScore) {
                    bestScore = score
                    bestRewardRisk = candidate
                    bestTraining = training
                }
            }

            val trainingResult = bestTraining ?: return null
            val test = BacktestingEngine.runFromPages(
                firstTime = firstTime,
                interval = interval,
                initialCapital = initialCapital,
                riskPercent = riskPercent,
                rewardRisk = bestRewardRisk,
                tradeAfterTime = testStart
            ) { after, limit -> loadPage(after, testEnd, limit) } ?: return null

            folds += WalkForwardFold(
                trainingStart = trainStart,
                trainingEnd = trainEnd,
                testStart = testStart,
                testEnd = testEnd,
                selectedRewardRisk = bestRewardRisk,
                trainingTrades = trainingResult.totalTrades,
                trainingReturnPercent = trainingResult.totalReturnPercent,
                testTrades = test.totalTrades,
                testWins = test.wins,
                testWinRate = test.winRate,
                testReturnPercent = test.totalReturnPercent,
                testDrawdownPercent = test.maxDrawdownPercent,
                testProfitFactor = test.profitFactor,
                trainingReliable = trainingResult.totalTrades >= MIN_TRAINING_TRADES
            )

        }
        if (folds.isEmpty()) return null

        val testReturns = folds.map { it.testReturnPercent }
        var compounded = 1.0
        testReturns.forEach { compounded *= (1.0 + it / 100.0) }
        val tradeCount = folds.sumOf { it.testTrades }
        val wins = folds.sumOf { it.testWins }
        val unreliable = folds.any { !it.trainingReliable }
        val profitable = folds.count { it.testReturnPercent > 0.0 }
        val warning = when {
            unreliable -> "At least one training window had fewer than 5 trades. Treat its selected parameters as weak evidence."
            profitable < folds.size -> "The strategy did not profit in every unseen test window. Do not assume the training result will repeat."
            else -> "All test windows were profitable in this historical sample; this still does not guarantee future performance."
        }

        return WalkForwardResult(
            folds = folds,
            outOfSampleTrades = tradeCount,
            outOfSampleWins = wins,
            averageOutOfSampleReturnPercent = testReturns.average(),
            compoundedOutOfSampleReturnPercent = (compounded - 1.0) * 100.0,
            averageOutOfSampleDrawdownPercent = folds.map { it.testDrawdownPercent }.average(),
            profitableTestFolds = profitable,
            totalTestFolds = folds.size,
            selectedParameters = folds.map { it.selectedRewardRisk }.distinct(),
            warning = warning
        )
    }
}
