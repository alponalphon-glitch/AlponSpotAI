package com.alpon.spotai.data

import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Independent public context checks. Missing providers are reported, never fabricated. */
data class ExternalHeadline(
    val title: String,
    val source: String,
    val url: String,
    val publishedAt: Long,
    val symbolMatch: Boolean,
    val sentiment: Int
)

data class ExternalMarketContext(
    val symbol: String,
    val fetchedAt: Long,
    val successfulSources: Int,
    val coinSpecificSources: Int,
    val headlines: List<ExternalHeadline>,
    val bullishNews: Int,
    val bearishNews: Int,
    val coinGeckoMarketCapUsd: Double?,
    val coinGeckoVolume24hUsd: Double?,
    val coinGeckoChange24hPct: Double?,
    val coinGeckoRank: Int?,
    val globalMarketCapChange24hPct: Double?,
    val btcDominancePct: Double?,
    val coinMarketCapFeedAvailable: Boolean,
    val errors: List<String>
) {
    val newsConflict: Boolean get() = bullishNews >= 2 && bearishNews >= 2
    val marketDataReady: Boolean get() = coinGeckoMarketCapUsd != null && coinGeckoVolume24hUsd != null &&
        globalMarketCapChange24hPct != null && btcDominancePct != null
    val verificationReady: Boolean get() = successfulSources >= 3 && coinSpecificSources >= 2 &&
        headlines.size >= 3 && coinMarketCapFeedAvailable && marketDataReady && !newsConflict &&
        System.currentTimeMillis() - fetchedAt <= 30L * 60L * 1000L
}

class ExternalMarketContextRepository {
    private data class Feed(val url: String, val source: String, val isCmc: Boolean = false)
    private val feeds = listOf(
        Feed("https://www.coindesk.com/arc/outboundfeeds/rss/", "CoinDesk"),
        Feed("https://cointelegraph.com/rss", "Cointelegraph"),
        Feed("https://decrypt.co/feed", "Decrypt"),
        Feed("https://finance.yahoo.com/rss/cryptocurrency", "Yahoo Finance"),
        Feed("https://blog.coinmarketcap.com/feed/", "CoinMarketCap", true)
    )

    suspend fun load(rawSymbol: String): ExternalMarketContext = withContext(Dispatchers.IO) {
        val symbol = rawSymbol.uppercase(Locale.ROOT).removeSuffix("USDT").removeSuffix("USDC").removeSuffix("BUSD").removeSuffix("USD").removeSuffix("PERP")
        val errors = mutableListOf<String>()
        val now = System.currentTimeMillis()
        val feedResults = feeds.map { feed ->
            runCatching { feed to readFeed(feed) }.onFailure { errors += "${feed.source}: ${it.message ?: "unavailable"}" }.getOrNull()
        }.filterNotNull()
        val headlines = feedResults.flatMap { it.second }
            .filter { now - it.publishedAt in 0L..36L*60L*60L*1000L }
            .map { item ->
                val match = matchesSymbol(item.title + " " + item.summary, symbol)
                item.copy(symbolMatch = match, sentiment = sentiment(item.title + " " + item.summary))
            }
            .filter { it.symbolMatch || it.source == "CoinMarketCap" }
            .distinctBy { it.url }
            .sortedByDescending { it.publishedAt }
            .take(40)
        val coinSpecific = headlines.filter { it.symbolMatch }.map { it.source }.distinct().size
        val bullish = headlines.count { it.symbolMatch && it.sentiment > 0 }
        val bearish = headlines.count { it.symbolMatch && it.sentiment < 0 }
        val cmcAvailable = feedResults.any { it.first.isCmc }
        var cap: Double? = null; var volume: Double? = null; var change: Double? = null; var rank: Int? = null
        var globalChange: Double? = null; var btcDom: Double? = null
        try {
            val query = URLEncoder.encode(symbol, "UTF-8")
            val search = JSONObject(httpGet("https://api.coingecko.com/api/v3/search?query=$query"))
            val coins = search.optJSONArray("coins") ?: JSONArray()
            val match = (0 until coins.length()).map { coins.optJSONObject(it) }
                .firstOrNull { it?.optString("symbol", "")?.equals(symbol, true) == true }
            val id = match?.optString("id")?.takeIf { it.isNotBlank() }
            if (id == null) errors += "CoinGecko exact-symbol lookup did not return a matching asset."
            else {
                val marketRows = JSONArray(httpGet("https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&ids=${URLEncoder.encode(id,"UTF-8")}&price_change_percentage=24h"))
                val row = marketRows.optJSONObject(0)
                if (row != null) {
                    cap = row.optDouble("market_cap").takeIf { it.isFinite() && it > 0 }
                    volume = row.optDouble("total_volume").takeIf { it.isFinite() && it > 0 }
                    change = row.optDouble("price_change_percentage_24h").takeIf { it.isFinite() }
                    rank = row.optInt("market_cap_rank").takeIf { it > 0 }
                }
            }
        } catch (e: Exception) { errors += "CoinGecko coin metrics: ${e.message ?: "unavailable"}" }
        try {
            val global = JSONObject(httpGet("https://api.coingecko.com/api/v3/global")).optJSONObject("data")
            val capPct = global?.optJSONObject("market_cap_change_percentage_24h_usd")
            globalChange = capPct?.optDouble("usd")?.takeIf { it.isFinite() }
            btcDom = global?.optJSONObject("market_cap_percentage")?.optDouble("btc")?.takeIf { it.isFinite() && it > 0 }
        } catch (e: Exception) { errors += "CoinGecko global market metrics: ${e.message ?: "unavailable"}" }
        ExternalMarketContext(symbol, now, feedResults.size, coinSpecific, headlines, bullish, bearish,
            cap, volume, change, rank, globalChange, btcDom, cmcAvailable, errors)
    }

    private data class RawHeadline(val title:String,val summary:String,val url:String,val source:String,val publishedAt:Long)
    private fun readFeed(feed: Feed): List<RawHeadline> {
        val c = (URL(feed.url).openConnection() as HttpURLConnection).apply {
            connectTimeout=7000; readTimeout=9000
            setRequestProperty("User-Agent","ALPON-Spot-AI/1.4.2 (Android market context)")
            setRequestProperty("Accept","application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
        }
        try {
            c.inputStream.use { input ->
                val parser=Xml.newPullParser().apply { setInput(input,"UTF-8") }
                val out= mutableListOf<RawHeadline>(); var event=parser.eventType
                var inside=false; var tag=""; var title=""; var link=""; var desc=""; var date=""
                while(event!=XmlPullParser.END_DOCUMENT) {
                    when(event) {
                        XmlPullParser.START_TAG -> {
                            val n=parser.name.lowercase(Locale.ROOT)
                            if(n=="item" || n=="entry") { inside=true;title="";link="";desc="";date="" }
                            else if(inside) { tag=n; if(n=="link") link=parser.getAttributeValue(null,"href") ?: link }
                        }
                        XmlPullParser.TEXT, XmlPullParser.CDSECT -> if(inside) when(tag) {
                            "title" -> title+=parser.text.orEmpty()
                            "link" -> link+=parser.text.orEmpty()
                            "description","summary","content:encoded" -> desc+=parser.text.orEmpty()
                            "pubdate","published","updated","dc:date" -> date+=parser.text.orEmpty()
                        }
                        XmlPullParser.END_TAG -> {
                            val n=parser.name.lowercase(Locale.ROOT)
                            if(n=="item" || n=="entry") {
                                val ts=parseDate(date)
                                if(ts>0 && title.isNotBlank() && link.startsWith("http")) out+=RawHeadline(clean(title),clean(desc).take(500),link.trim(),feed.source,ts)
                                inside=false;tag=""
                            } else if(inside) tag=""
                        }
                    }; event=parser.next()
                }; out
            }
        } finally { c.disconnect() }
    }
    private fun httpGet(url:String):String {
        val c=(URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout=8000;readTimeout=10000;setRequestProperty("User-Agent","ALPON-Spot-AI/1.4.2")
        }
        try { if(c.responseCode !in 200..299) error("HTTP ${c.responseCode}"); return c.inputStream.bufferedReader().use { it.readText() } }
        finally { c.disconnect() }
    }
    private fun parseDate(s:String):Long {
        val v=s.trim(); if(v.isEmpty()) return 0L
        val patterns=listOf("EEE, dd MMM yyyy HH:mm:ss Z","EEE, d MMM yyyy HH:mm:ss Z","yyyy-MM-dd'T'HH:mm:ss'Z'","yyyy-MM-dd'T'HH:mm:ssXXX","yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        for(p in patterns) try { return java.text.SimpleDateFormat(p,Locale.US).parse(v)?.time ?: 0L } catch(_:Exception) {}
        return 0L
    }
    private fun clean(s:String)=s.replace(Regex("(?is)<script.*?</script>")," ").replace(Regex("(?is)<style.*?</style>")," ")
        .replace(Regex("<[^>]+>")," ").replace("&amp;","&").replace("&quot;","").replace(Regex("\\s+")," ").trim()
    private fun matchesSymbol(text:String,symbol:String):Boolean {
        if(symbol.length<3) return false
        val aliases=mapOf("BTC" to "Bitcoin", "ETH" to "Ethereum", "BNB" to "Binance Coin", "XRP" to "Ripple", "SOL" to "Solana", "DOGE" to "Dogecoin", "ADA" to "Cardano", "AVAX" to "Avalanche", "DOT" to "Polkadot", "LINK" to "Chainlink", "LTC" to "Litecoin", "TRX" to "Tron", "TON" to "Toncoin")
        val tickerMatch=Regex("(?i)(?<![A-Z0-9])\\$?${Regex.escape(symbol)}(?![A-Z0-9])").containsMatchIn(text)
        return tickerMatch || aliases[symbol]?.let { Regex("(?i)\\b${Regex.escape(it)}\\b").containsMatchIn(text) } == true
    }
    private fun sentiment(text:String):Int {
        val t=text.lowercase(Locale.ROOT)
        val positive=listOf("surge","rally","bullish","gains","approval","adoption","inflow","breakout","partnership","record high","upgrade","etf inflow","accumulation")
        val negative=listOf("crash","hack","exploit","lawsuit","ban","bearish","sell-off","outflow","liquidation","fraud","delist","sec probe","bankruptcy","breach","warning")
        val p=positive.count { t.contains(it) }; val n=negative.count { t.contains(it) }
        return when { p>n -> 1; n>p -> -1; else -> 0 }
    }
}
