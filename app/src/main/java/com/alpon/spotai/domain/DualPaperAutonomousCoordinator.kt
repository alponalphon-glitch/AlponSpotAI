package com.alpon.spotai.domain

import android.content.SharedPreferences
import com.alpon.spotai.data.Candle
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

/** Paper-only autonomous controller. It accepts validated market candles and never submits exchange orders. */
class DualPaperAutonomousCoordinator(private val prefs: SharedPreferences) {
    data class MarketInput(val symbol: String, val candles: List<Candle>, val lastPrice: Double)
    data class CycleReport(val spotActions: List<String>, val futuresActions: List<String>, val scanned: Int, val skipped: Int)
    private data class Stats(var day: String = "", var trades: Int = 0, var lossesInRow: Int = 0, var dayPnl: Double = 0.0, var halted: Boolean = false)

    private var spot = loadLedger("spot", DualPaperPortfolioEngine.newSpot())
    private var futures = loadLedger("futures", DualPaperPortfolioEngine.newFutures())
    private var spotStats = loadStats("spot")
    private var futuresStats = loadStats("futures")

    companion object {
        private const val MAX_TRADES_DAY = 5
        private const val MAX_POSITION = 0.25
        private const val RISK_PER_TRADE = 0.005
        private const val DAILY_STOP = 0.02
        private const val MAX_LOSS_STREAK = 3
        private const val COOLDOWN_MS = 5 * 60 * 1000L
        private const val ATR_MULT = 1.6
        private const val RR = 2.0
    }

    @Synchronized fun snapshot(): Pair<DualPaperPortfolioEngine.Ledger, DualPaperPortfolioEngine.Ledger> = spot to futures

    @Synchronized fun cycle(spotMarkets: List<MarketInput>, futuresMarkets: List<MarketInput>, now: Long = System.currentTimeMillis()): CycleReport {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now))
        resetDaily(spotStats, date); resetDaily(futuresStats, date)
        val spotActions = mutableListOf<String>(); val futuresActions = mutableListOf(); var skipped = 0
        spot = manageOpen(spot, spotMarkets, spotActions, now, spotStats)
        futures = manageOpen(futures, futuresMarkets, futuresActions, now, futuresStats)
        if (!spotStats.halted && spotStats.trades < MAX_TRADES_DAY && spot.positions.size < 3) {
            for (input in spotMarkets.sortedByDescending { quoteVolume(it.candles) }) {
                if (spot.positions.size >= 3 || spotStats.trades >= MAX_TRADES_DAY) break
                val signal = signal(input)
                if (signal == null) {
                    skipped++
                    continue
                }
                if (signal.direction != DualPaperPortfolioEngine.Side.LONG || spot.positions.any { it.symbol == input.symbol }) continue
                val notional = sizedNotional(spot, input.lastPrice, signal.atr, spotStats)
                if (notional <= 0.0) continue
                val sl = input.lastPrice - signal.atr * ATR_MULT
                val tp = input.lastPrice + signal.atr * ATR_MULT * RR
                val opened = DualPaperPortfolioEngine.open(spot, input.symbol, signal.direction, input.lastPrice, notional, now, sl, tp, 1)
                if (opened.accepted) { spot = opened.ledger; spotStats.trades++; spotActions += "BUY ${input.symbol} · size ${"%.2f".format(notional)} · SL ${"%.6f".format(sl)} · TP ${"%.6f".format(tp)}" }
            }
        }
        if (!futuresStats.halted && futuresStats.trades < MAX_TRADES_DAY && futures.positions.size < 3) {
            for (input in futuresMarkets.sortedByDescending { quoteVolume(it.candles) }) {
                if (futures.positions.size >= 3 || futuresStats.trades >= MAX_TRADES_DAY) break
                val signal = signal(input)
                if (signal == null) {
                    skipped++
                    continue
                }
                if (futures.positions.any { it.symbol == input.symbol }) continue
                val notional = sizedNotional(futures, input.lastPrice, signal.atr, futuresStats)
                if (notional <= 0.0) continue
                val side = signal.direction
                val sl = if (side == DualPaperPortfolioEngine.Side.LONG) input.lastPrice - signal.atr * ATR_MULT else input.lastPrice + signal.atr * ATR_MULT
                val tp = if (side == DualPaperPortfolioEngine.Side.LONG) input.lastPrice + signal.atr * ATR_MULT * RR else input.lastPrice - signal.atr * ATR_MULT * RR
                val opened = DualPaperPortfolioEngine.open(futures, input.symbol, side, input.lastPrice, notional, now, sl, tp, 1)
                if (opened.accepted) { futures = opened.ledger; futuresStats.trades++; futuresActions += "${side.name} ${input.symbol} · size ${"%.2f".format(notional)} · SL ${"%.6f".format(sl)} · TP ${"%.6f".format(tp)}" }
            }
        }
        saveAll()
        return CycleReport(spotActions, futuresActions, spotMarkets.size + futuresMarkets.size, skipped)
    }


    private data class Signal(val direction: DualPaperPortfolioEngine.Side, val atr: Double)
    private fun signal(input: MarketInput): Signal? {
        val candles = input.candles.filter { it.closeTime <= System.currentTimeMillis() && it.close.isFinite() && it.close > 0 }.sortedBy { it.time }
        if (candles.size < 60 || !input.lastPrice.isFinite() || input.lastPrice <= 0) return null
        val closes = candles.map { it.close }
        val fast = ema(closes, 20); val slow = ema(closes, 50)
        val rsi = rsi(closes, 14); val atr = atr(candles, 14)
        val previousFast = ema(closes.dropLast(1), 20); val previousSlow = ema(closes.dropLast(1), 50)
        if (!fast.isFinite() || !slow.isFinite() || !rsi.isFinite() || !atr.isFinite() || atr <= 0) return null
        val bullish = previousFast <= previousSlow && fast > slow && rsi in 52.0..68.0 && input.lastPrice >= fast
        val bearish = previousFast >= previousSlow && fast < slow && rsi in 32.0..48.0 && input.lastPrice <= fast
        return when { bullish -> Signal(DualPaperPortfolioEngine.Side.LONG, atr); bearish -> Signal(DualPaperPortfolioEngine.Side.SHORT, atr); else -> null }
    }

    private fun manageOpen(ledger: DualPaperPortfolioEngine.Ledger, markets: List<MarketInput>, actions: MutableList<String>, now: Long, stats: Stats): DualPaperPortfolioEngine.Ledger {
        var updated = ledger
        val bySymbol = markets.associateBy { it.symbol }
        for (position in ledger.positions.toList()) {
            val market = bySymbol[position.symbol] ?: continue
            val price = market.lastPrice.takeIf { it.isFinite() && it > 0 } ?: continue
            val stopHit = position.stopLoss?.let { if (position.side == DualPaperPortfolioEngine.Side.LONG) price <= it else price >= it } ?: false
            val targetHit = position.takeProfit?.let { if (position.side == DualPaperPortfolioEngine.Side.LONG) price >= it else price <= it } ?: false
            val reversed = signal(market)?.direction?.let { it != position.side } ?: false
            if (stopHit || targetHit || reversed) {
                val result = DualPaperPortfolioEngine.close(updated, position.symbol, price, now)
                if (result.accepted) {
                    updated = result.ledger; stats.dayPnl += result.ledger.closedPnl - ledger.closedPnl
                    val delta = result.ledger.closedPnl - ledger.closedPnl
                    stats.lossesInRow = if (delta < 0) stats.lossesInRow + 1 else 0
                    actions += "EXIT ${position.symbol} · ${if (stopHit) "STOP" else if (targetHit) "TARGET" else "SIGNAL FLIP"} · PnL ${"%.2f".format(delta)}"
                    if (stats.dayPnl <= -updated.startingCash * DAILY_STOP || stats.lossesInRow >= MAX_LOSS_STREAK) stats.halted = true
                }
            }
        }
        return updated
    }

    private fun sizedNotional(ledger: DualPaperPortfolioEngine.Ledger, price: Double, atr: Double, stats: Stats): Double {
        if (!price.isFinite() || price <= 0 || !atr.isFinite() || atr <= 0 || stats.halted) return 0.0
        val equity = max(0.0, ledger.cash + ledger.positions.sumOf { p -> p.quantity * p.entryPrice / p.leverage })
        val riskBudget = equity * RISK_PER_TRADE
        val stopDistance = atr * ATR_MULT
        val riskSized = riskBudget / stopDistance * price
        return minOf(riskSized, equity * MAX_POSITION, ledger.cash * 0.95).takeIf { it.isFinite() && it >= 10.0 } ?: 0.0
    }
    private fun resetDaily(stats: Stats, date: String) { if (stats.day != date) { stats.day=date; stats.trades=0; stats.lossesInRow=0; stats.dayPnl=0.0; stats.halted=false } }
    private fun quoteVolume(c: List<Candle>) = c.lastOrNull()?.quoteVolume?.takeIf { it.isFinite() && it > 0 } ?: 0.0
    private fun ema(values: List<Double>, period: Int): Double { if (values.size < period) return Double.NaN; val k=2.0/(period+1); var e=values.take(period).average(); for (v in values.drop(period)) e=v*k+e*(1-k); return e }
    private fun rsi(values: List<Double>, period: Int): Double { if(values.size<=period) return Double.NaN; val changes=values.zipWithNext { a,b -> b-a }; var gain=changes.take(period).filter { it>0 }.sum()/period; var loss=changes.take(period).filter { it<0 }.sumOf { -it }/period; for(d in changes.drop(period)){gain=(gain*(period-1)+max(0.0,d))/period;loss=(loss*(period-1)+max(0.0,-d))/period}; return if(loss==0.0)100.0 else 100.0-100.0/(1.0+gain/loss) }
    private fun atr(c: List<Candle>, period: Int): Double { if(c.size<=period) return Double.NaN; val tr=c.drop(1).mapIndexed { i,x -> val p=c[i].close; max(x.high-x.low,max(abs(x.high-p),abs(x.low-p))) }; return tr.takeLast(period).average() }

    private fun loadLedger(key: String, fallback: DualPaperPortfolioEngine.Ledger): DualPaperPortfolioEngine.Ledger = runCatching {
        val o=JSONObject(prefs.getString("dual_paper_$key", "") ?: "")
        val arr=o.optJSONArray("positions") ?: JSONArray(); val ps=mutableListOf<DualPaperPortfolioEngine.Position>()
        for(i in 0 until arr.length()){val p=arr.getJSONObject(i);ps+=DualPaperPortfolioEngine.Position(p.getString("symbol"),DualPaperPortfolioEngine.Side.valueOf(p.getString("side")),p.getDouble("quantity"),p.getDouble("entry"),p.optDouble("entryFee",0.0),p.optLong("openedAt",0L),if(p.has("sl"))p.optDouble("sl") else null,if(p.has("tp"))p.optDouble("tp") else null,p.optInt("leverage",1))}
        fallback.copy(startingCash=o.optDouble("startingCash",5000.0),cash=o.optDouble("cash",5000.0),positions=ps,closedPnl=o.optDouble("closedPnl",0.0),feesPaid=o.optDouble("feesPaid",0.0),halted=o.optBoolean("halted",false),haltReason=o.optString("haltReason").takeIf{it.isNotBlank()})
    }.getOrElse { fallback }
    private fun loadStats(key:String)=runCatching{val o=JSONObject(prefs.getString("dual_stats_$key", "") ?: "");Stats(o.optString("day"),o.optInt("trades"),o.optInt("lossesInRow"),o.optDouble("dayPnl"),o.optBoolean("halted"))}.getOrElse{Stats()}
    private fun saveLedger(key:String,l:DualPaperPortfolioEngine.Ledger){val o=JSONObject().put("startingCash",l.startingCash).put("cash",l.cash).put("closedPnl",l.closedPnl).put("feesPaid",l.feesPaid).put("halted",l.halted).put("haltReason",l.haltReason);val a=JSONArray();l.positions.forEach{p->a.put(JSONObject().put("symbol",p.symbol).put("side",p.side.name).put("quantity",p.quantity).put("entry",p.entryPrice).put("entryFee",p.entryFee).put("openedAt",p.openedAt).put("sl",p.stopLoss).put("tp",p.takeProfit).put("leverage",p.leverage))};o.put("positions",a);prefs.edit().putString("dual_paper_$key",o.toString()).apply()}
    private fun saveStats(key:String,s:Stats)=prefs.edit().putString("dual_stats_$key",JSONObject().put("day",s.day).put("trades",s.trades).put("lossesInRow",s.lossesInRow).put("dayPnl",s.dayPnl).put("halted",s.halted).toString()).apply()
    private fun saveAll(){saveLedger("spot",spot);saveLedger("futures",futures);saveStats("spot",spotStats);saveStats("futures",futuresStats)}
}
