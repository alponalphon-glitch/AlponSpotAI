package com.alpon.spotai.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Public RSS headlines only. No API key or account credentials are required. */
data class CryptoNewsItem(
    val title: String,
    val summary: String,
    val url: String,
    val source: String,
    val publishedAt: Long
)

class CryptoNewsRepository {
    private data class Feed(val url: String, val source: String)
    private val feeds = listOf(
        Feed("https://finance.yahoo.com/rss/cryptocurrency", "Yahoo Finance"),
        Feed("https://blog.coinmarketcap.com/feed/", "CoinMarketCap")
    )

    suspend fun latest24Hours(): List<CryptoNewsItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cutoff = now - 24L * 60L * 60L * 1000L
        feeds.flatMap { feed -> runCatching { readFeed(feed) }.getOrDefault(emptyList()) }
            .filter { it.publishedAt in cutoff..now && it.title.isNotBlank() && it.url.startsWith("https://") }
            .distinctBy { it.url }
            .sortedByDescending { it.publishedAt }
    }

    private fun readFeed(feed: Feed): List<CryptoNewsItem> {
        val connection = (URL(feed.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "AlponSpotAI/1.0 (Android; crypto news reader)")
            setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
        }
        try {
            connection.inputStream.use { input ->
                val parser = Xml.newPullParser().apply { setInput(input, "UTF-8") }
                val result = mutableListOf<CryptoNewsItem>()
                var event = parser.eventType
                var inEntry = false
                var tag = ""
                var title = ""; var link = ""; var summary = ""; var date = ""
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            val name = parser.name.lowercase(Locale.ROOT)
                            if (name == "item" || name == "entry") {
                                inEntry = true; title = ""; link = ""; summary = ""; date = ""
                            } else if (inEntry) {
                                tag = name
                                if (name == "link") {
                                    val href = parser.getAttributeValue(null, "href")
                                    if (!href.isNullOrBlank()) link = href
                                }
                            }
                        }
                        XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inEntry) {
                            when (tag) {
                                "title" -> title += parser.text.orEmpty()
                                "link" -> link += parser.text.orEmpty()
                                "description", "summary", "content:encoded" -> summary += parser.text.orEmpty()
                                "pubdate", "published", "updated", "dc:date" -> date += parser.text.orEmpty()
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            val name = parser.name.lowercase(Locale.ROOT)
                            if (name == "item" || name == "entry") {
                                val timestamp = parseDate(date)
                                if (timestamp > 0L) result += CryptoNewsItem(
                                    title = clean(title), summary = clean(summary).take(360),
                                    url = link.trim(), source = feed.source, publishedAt = timestamp
                                )
                                inEntry = false; tag = ""
                            } else if (inEntry) tag = ""
                        }
                    }
                    event = parser.next()
                }
                return result
            }
        } finally { connection.disconnect() }
    }

    private fun parseDate(value: String): Long {
        val raw = value.trim()
        if (raw.isEmpty()) return 0L
        val patterns = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss Z", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        for (pattern in patterns) runCatching {
            val format = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = true }
            format.parse(raw)?.time
        }.getOrNull()?.let { return it }
        return 0L
    }

    private fun clean(value: String): String = value
        .replace(Regex("(?is)<script.*?</script>"), " ")
        .replace(Regex("(?is)<style.*?</style>"), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("\\s+"), " ").trim()
}
