package com.alpon.spotai.data

import kotlinx.coroutines.delay

class MarketHistoryRepository(
    private val binance: BinanceRepository,
    private val dao: MarketCandleDao
) {

    suspend fun refreshLatest(
        symbol: String,
        interval: String
    ): Int {
        val candles = binance.candles(symbol, interval)
        if (candles.isNotEmpty()) {
            dao.insertAll(candles.map { it.toEntity(symbol, interval) })
        }
        return candles.size
    }

    suspend fun downloadFullHistory(
        symbol: String,
        interval: String,
        onProgress: (downloaded: Int, nextStartTime: Long) -> Unit = { _, _ -> }
    ): Int {

        var state = dao.getSyncState(symbol, interval)

        if (state?.isComplete == true) {
            return 0
        }

        if (state == null) {
            val first = binance.firstCandle(symbol, interval)
                ?: return 0

            state = HistorySyncStateEntity(
                symbol = symbol,
                interval = interval,
                nextStartTime = first.time,
                isComplete = false,
                updatedAt = System.currentTimeMillis()
            )
            dao.saveSyncState(state)
        }

        var cursor = state.nextStartTime
        var downloaded = 0

        while (true) {
            val page = binance.candlesAfter(
                symbol = symbol,
                interval = interval,
                startTime = cursor,
                limit = 1000
            )

            if (page.isEmpty()) {
                dao.saveSyncState(
                    HistorySyncStateEntity(
                        symbol = symbol,
                        interval = interval,
                        nextStartTime = cursor,
                        isComplete = true,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                break
            }

            val ordered = page.sortedBy { it.time }
            dao.insertAll(ordered.map { it.toEntity(symbol, interval) })
            downloaded += ordered.size

            val last = ordered.last()
            val nextCursor = last.closeTime + 1L

            if (nextCursor <= cursor) {
                throw IllegalStateException(
                    "Binance history pagination did not advance."
                )
            }

            cursor = nextCursor

            val complete = ordered.size < 1000

            dao.saveSyncState(
                HistorySyncStateEntity(
                    symbol = symbol,
                    interval = interval,
                    nextStartTime = cursor,
                    isComplete = complete,
                    updatedAt = System.currentTimeMillis()
                )
            )

            onProgress(downloaded, cursor)

            if (complete) break

            delay(150L)
        }

        return downloaded
    }
}
