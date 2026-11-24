package com.alpon.spotai.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface BinanceApi {
    @GET("api/v3/ticker/24hr") suspend fun tickers(): List<TickerDto>
    @GET("api/v3/exchangeInfo") suspend fun exchangeInfo(): ExchangeInfoDto
    @GET("api/v3/ticker/24hr") suspend fun ticker(@Query("symbol") symbol: String): TickerDto
    @GET("api/v3/klines") suspend fun klines(@Query("symbol") symbol: String, @Query("interval") interval: String, @Query("limit") limit: Int = 300): List<List<Any>>
    companion object { fun create(): BinanceApi = Retrofit.Builder().baseUrl("https://api.binance.com/").addConverterFactory(GsonConverterFactory.create()).build().create(BinanceApi::class.java) }
}
data class TickerDto(val symbol: String, val lastPrice: String, val priceChangePercent: String, val volume: String, val quoteVolume: String)
data class ExchangeInfoDto(val symbols: List<ExchangeSymbolDto>)
data class ExchangeSymbolDto(val symbol: String, val status: String, val quoteAsset: String, val isSpotTradingAllowed: Boolean? = null)
data class Candle(val time: Long, val open: Double, val high: Double, val low: Double, val close: Double, val volume: Double)
data class MarketSnapshot(val ticker: TickerDto, val candles: List<Candle>)
class BinanceRepository(private val api: BinanceApi = BinanceApi.create()) {
    suspend fun ticker(symbol: String) = api.ticker(symbol)
    suspend fun allTickers() = api.tickers()
    suspend fun topSpotUsdtMarkets(limit: Int = 500): List<TickerDto> {
        val eligible = api.exchangeInfo().symbols.asSequence()
            .filter { it.status == "TRADING" && it.quoteAsset == "USDT" && it.isSpotTradingAllowed != false }
            .map { it.symbol }
            .filterNot { symbol -> listOf("UPUSDT", "DOWNUSDT", "BULLUSDT", "BEARUSDT").any { symbol.endsWith(it) } }
            .toSet()
        return api.tickers().asSequence()
            .filter { it.symbol in eligible }
            .sortedByDescending { it.quoteVolume.toDoubleOrNull() ?: 0.0 }
            .take(limit.coerceIn(1, 500))
            .toList()
    }
    suspend fun candles(symbol: String, interval: String): List<Candle> = api.klines(symbol, interval, 300).map { r -> Candle((r[0] as Number).toLong(), (r[1] as String).toDouble(), (r[2] as String).toDouble(), (r[3] as String).toDouble(), (r[4] as String).toDouble(), (r[5] as String).toDouble()) }
    suspend fun snapshot(symbol: String, interval: String) = MarketSnapshot(ticker(symbol), candles(symbol, interval))
}
