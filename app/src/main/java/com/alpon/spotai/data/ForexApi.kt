package com.alpon.spotai.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

interface TwelveDataApi {
    @GET("time_series")
    suspend fun timeSeries(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("outputsize") outputSize: Int = 1000,
        @Query("order") order: String = "ASC",
        @Query("timezone") timezone: String = "UTC",
        @Query("apikey") apiKey: String
    ): ForexSeriesResponse

    @GET("forex_pairs")
    suspend fun forexPairs(@Query("apikey") apiKey: String, @Query("outputsize") outputSize: Int = 500): ForexPairsResponse

    companion object {
        fun create(): TwelveDataApi = Retrofit.Builder()
            .baseUrl("https://api.twelvedata.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TwelveDataApi::class.java)
    }
}

data class ForexSeriesResponse(
    val meta: ForexMeta? = null,
    val values: List<ForexValue>? = null,
    val status: String? = null,
    val message: String? = null,
    val code: Int? = null
)

data class ForexMeta(val symbol: String? = null, val interval: String? = null, val currency_base: String? = null, val currency_quote: String? = null)
data class ForexValue(val datetime: String? = null, val open: String? = null, val high: String? = null, val low: String? = null, val close: String? = null)
data class ForexPairsResponse(val data: List<ForexPairDto>? = null, val status: String? = null, val message: String? = null, val code: Int? = null)
data class ForexPairDto(val symbol: String, val currency_group: String? = null, val currency_base: String? = null, val currency_quote: String? = null)

class TwelveDataForexRepository(private val api: TwelveDataApi = TwelveDataApi.create()) {
    suspend fun pairs(apiKey: String): List<ForexPairDto> {
        require(apiKey.isNotBlank()) { "Enter a Twelve Data API key to load Forex instruments." }
        val response = api.forexPairs(apiKey.trim())
        val result = response.data.orEmpty()
        if (result.isEmpty()) throw IllegalStateException(response.message ?: "The Forex provider returned no currency pairs.")
        return result.distinctBy { it.symbol.uppercase() }.sortedWith(
            compareBy<ForexPairDto> { groupRank(it.currency_group) }.thenBy { it.symbol }
        )
    }

    suspend fun candles(pair: String, interval: String, apiKey: String, outputSize: Int = 1000): List<Candle> {
        require(apiKey.isNotBlank()) { "Twelve Data API key is missing. Forex data cannot be requested." }
        val response = api.timeSeries(pair, interval, outputSize.coerceIn(1, 5000), "ASC", "UTC", apiKey.trim())
        val rows = response.values.orEmpty()
        if (rows.isEmpty()) throw IllegalStateException(response.message ?: "No Forex candles returned for $pair ($interval). Check pair support and API limits.")
        val duration = intervalMillis(interval)
        val parsed = rows.map { row ->
            val time = parseUtc(row.datetime ?: throw IllegalStateException("Forex candle has no timestamp."))
            fun value(raw: String?, name: String): Double = raw?.toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: throw IllegalStateException("Invalid Forex $name value for $pair.")
            val open = value(row.open, "open")
            val high = value(row.high, "high")
            val low = value(row.low, "low")
            val close = value(row.close, "close")
            require(open > 0.0 && high >= maxOf(open, close) && low <= minOf(open, close) && low > 0.0 && close > 0.0) {
                "Invalid OHLC candle received for $pair."
            }
            val closeTime = if (interval == "1month") {
                val date = java.time.Instant.ofEpochMilli(time).atZone(ZoneOffset.UTC).toLocalDate()
                date.withDayOfMonth(1).plusMonths(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() - 1L
            } else time + duration - 1L
            Candle(time, open, high, low, close, 0.0, closeTime, 0.0, 0L, 0.0, 0.0)
        }.sortedBy { it.time }
        require(parsed.zipWithNext().none { (a, b) -> a.time >= b.time }) { "Forex data contains duplicate or unordered timestamps." }
        return parsed
    }

    private fun parseUtc(raw: String): Long {
        runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        val dateTime = runCatching { LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) }
            .recoverCatching { LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
            .recoverCatching { LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay() }
            .getOrElse { throw IllegalStateException("Unsupported Forex timestamp: $raw") }
        return dateTime.toInstant(ZoneOffset.UTC).toEpochMilli()
    }

    private fun intervalMillis(interval: String): Long = when (interval) {
        "1min" -> 60_000L; "5min" -> 300_000L; "15min" -> 900_000L; "30min" -> 1_800_000L
        "45min" -> 2_700_000L; "1h" -> 3_600_000L; "2h" -> 7_200_000L; "4h" -> 14_400_000L
        "8h" -> 28_800_000L; "1day" -> 86_400_000L; "1week" -> 604_800_000L; "1month" -> 2_592_000_000L
        else -> throw IllegalArgumentException("Unsupported Forex interval: $interval")
    }

    private fun groupRank(group: String?): Int = when (group?.lowercase()) {
        "major" -> 0; "minor" -> 1; "exotic" -> 2; "exotic-cross" -> 3; else -> 4
    }
}
