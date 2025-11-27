package com.hazman.lunoandroidtrader.domain.trading

import com.hazman.lunoandroidtrader.domain.market.PriceCandle
import com.hazman.lunoandroidtrader.domain.market.SimulatedTrade
import com.hazman.lunoandroidtrader.domain.market.TradeDirection
import com.hazman.lunoandroidtrader.domain.market.TradeStatus
import com.hazman.lunoandroidtrader.domain.model.AccountSnapshot
import com.hazman.lunoandroidtrader.domain.model.RiskConfig
import com.hazman.lunoandroidtrader.domain.risk.RiskDecision
import com.hazman.lunoandroidtrader.domain.risk.RiskManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Reason for closing a simulated trade.
 */
enum class CloseReason {
    TAKE_PROFIT,
    STOP_LOSS
}

/**
 * Closed trade record with realized P&L.
 */
data class ClosedTrade(
    val trade: SimulatedTrade,
    val closePrice: Double,
    val closedAtMillis: Long,
    val pnlMyr: Double,
    val reason: CloseReason
)

/**
 * Performance snapshot across the entire paper session.
 */
data class PerformanceSnapshot(
    val totalRealizedPnlMyr: Double,
    val totalClosedTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRatePercent: Double
)

/**
 * Result of updating open trades with a new candle.
 */
data class PaperUpdateResult(
    val closedTrades: List<ClosedTrade>,
    val openTrades: List<SimulatedTrade>,
    val totalRealizedPnlMyr: Double
)

/**
 * PaperTradingEngine manages the lifecycle of simulated trades:
 *  - Opens new trades with size derived from the risk manager.
 *  - Closes trades when TP or SL is hit by the candle OHLC.
 *  - Tracks total realized P&L in MYR.
 *
 * This engine is pure-paper: it never touches real Luno accounts.
 *
 * NEW (this step):
 *  - Keeps a full in-memory history of all closed trades in [closedTradesHistory].
 *  - Provides [snapshotClosedTrades] and [snapshotPerformance] for UI layers.
 */
class PaperTradingEngine(
    private val riskManager: RiskManager
) {

    // All currently open simulated trades.
    private val openTrades = mutableListOf<SimulatedTrade>()

    // Full closed trade history for this app session.
    private val closedTradesHistory = mutableListOf<ClosedTrade>()

    // Aggregate realized P&L across the entire paper session.
    private var totalRealizedPnlMyr: Double = 0.0

    // Simple incrementing ID for trades.
    private var nextTradeId: Long = 1L

    /**
     * Get a snapshot of current open trades.
     */
    fun snapshotOpenTrades(): List<SimulatedTrade> = openTrades.map { it.copy() }

    /**
     * Get a snapshot of closed trades history.
     *
     * @param limit Optional max number of recent trades to return.
     *              If null, returns the full history.
     */
    fun snapshotClosedTrades(limit: Int? = null): List<ClosedTrade> {
        if (closedTradesHistory.isEmpty()) return emptyList()
        val copy = closedTradesHistory.toList()
        return if (limit == null || limit <= 0 || limit >= copy.size) {
            copy
        } else {
            copy.takeLast(limit)
        }
    }

    /**
     * Get a performance snapshot across the entire paper session.
     */
    fun snapshotPerformance(): PerformanceSnapshot {
        val closed = closedTradesHistory
        if (closed.isEmpty()) {
            return PerformanceSnapshot(
                totalRealizedPnlMyr = 0.0,
                totalClosedTrades = 0,
                winningTrades = 0,
                losingTrades = 0,
                winRatePercent = 0.0
            )
        }

        var wins = 0
        var losses = 0

        for (ct in closed) {
            if (ct.pnlMyr > 0.0) {
                wins++
            } else if (ct.pnlMyr < 0.0) {
                losses++
            }
        }

        val total = closed.size
        val winRate = if (total > 0) {
            wins.toDouble() / total.toDouble() * 100.0
        } else {
            0.0
        }

        return PerformanceSnapshot(
            totalRealizedPnlMyr = totalRealizedPnlMyr,
            totalClosedTrades = total,
            winningTrades = wins,
            losingTrades = losses,
            winRatePercent = winRate
        )
    }

    /**
     * Fully reset the paper engine state.
     *
     * This is useful if you add a "Reset paper session" button later.
     */
    fun resetSession() {
        openTrades.clear()
        closedTradesHistory.clear()
        totalRealizedPnlMyr = 0.0
        nextTradeId = 1L
    }

    /**
     * For convenience, expose the current total realized P&L.
     */
    fun getTotalRealizedPnlMyr(): Double = totalRealizedPnlMyr

    /**
     * Try to open a LONG trade on the given pair using the candle close as entry.
     *
     * Risk model:
     *  - Risk per trade (MYR) = equity * riskPerTradePercent / 100.
     *  - Stop loss = entryPrice * (1 - BASE_SL_PCT).
     *  - Take profit = entryPrice * (1 + 2 * BASE_SL_PCT). (≈2R target)
     *  - Quantity (base) = riskAmountMyr / (entryPrice - stopLossPrice).
     *
     * If risk checks fail, returns null.
     */
    fun tryOpenLongTrade(
        pair: String = "XBTMYR",
        candle: PriceCandle,
        account: AccountSnapshot,
        riskConfig: RiskConfig
    ): SimulatedTrade? {
        val nowMillis = candle.timestampMillis

        // 1) Risk checks
        val decision: RiskDecision = riskManager.canOpenNewTrade(
            riskConfig = riskConfig,
            account = account,
            nowMillis = nowMillis
        )
        if (!decision.canOpen) {
            return null
        }

        // 2) Compute risk amount
        val maxRiskMyr = riskManager.computeMaxRiskPerTradeMyr(riskConfig, account)
        if (maxRiskMyr <= 0.0) {
            return null
        }

        val entryPrice = candle.close
        if (entryPrice <= 0.0) {
            return null
        }

        // Base % distance to SL: 0.5% of price
        val BASE_SL_PCT = 0.005
        val slPrice = entryPrice * (1.0 - BASE_SL_PCT)
        val tpPrice = entryPrice * (1.0 + BASE_SL_PCT * 2.0)

        val perUnitRiskMyr = entryPrice - slPrice
        if (perUnitRiskMyr <= 0.0) {
            return null
        }

        // Quantity in base asset (e.g. BTC)
        var quantityBase = maxRiskMyr / perUnitRiskMyr

        // Basic sanity clamping.
        if (!quantityBase.isFinite() || quantityBase <= 0.0) {
            return null
        }

        val MIN_QTY = 0.0001
        quantityBase = max(quantityBase, MIN_QTY)

        val trade = SimulatedTrade(
            id = nextTradeId++,
            pair = pair,
            direction = TradeDirection.LONG,
            entryPrice = entryPrice,
            stopLossPrice = slPrice,
            takeProfitPrice = tpPrice,
            quantityBase = quantityBase,
            riskAmountMyr = maxRiskMyr,
            openedAtMillis = nowMillis,
            status = TradeStatus.OPEN
        )

        openTrades.add(trade)
        riskManager.registerOpenedTrade(nowMillis)

        return trade
    }

    /**
     * Update all open trades given a new candle.
     *
     * For each trade, we check:
     *  - If candle.low <= SL: close at SL.
     *  - Else if candle.high >= TP: close at TP.
     *  - If both SL and TP are within the candle range, we assume SL hit first
     *    (conservative assumption).
     */
    fun updateOpenTrades(
        candle: PriceCandle,
        riskConfig: RiskConfig
    ): PaperUpdateResult {
        if (openTrades.isEmpty()) {
            return PaperUpdateResult(
                closedTrades = emptyList(),
                openTrades = emptyList(),
                totalRealizedPnlMyr = totalRealizedPnlMyr
            )
        }

        val closedTrades = mutableListOf<ClosedTrade>()
        val iterator = openTrades.iterator()

        while (iterator.hasNext()) {
            val trade = iterator.next()

            val hitSl = candle.low <= trade.stopLossPrice
            val hitTp = candle.high >= trade.takeProfitPrice

            if (!hitSl && !hitTp) {
                continue
            }

            val reason: CloseReason
            val closePrice: Double

            if (hitSl && hitTp) {
                // Conservative: assume SL first.
                reason = CloseReason.STOP_LOSS
                closePrice = trade.stopLossPrice
            } else if (hitSl) {
                reason = CloseReason.STOP_LOSS
                closePrice = trade.stopLossPrice
            } else {
                reason = CloseReason.TAKE_PROFIT
                closePrice = trade.takeProfitPrice
            }

            val pnlMyr = computePnlMyr(trade = trade, closePrice = closePrice)
            totalRealizedPnlMyr += pnlMyr

            iterator.remove()

            val closed = ClosedTrade(
                trade = trade.copy(status = TradeStatus.CLOSED),
                closePrice = closePrice,
                closedAtMillis = candle.timestampMillis,
                pnlMyr = pnlMyr,
                reason = reason
            )
            closedTrades.add(closed)
            closedTradesHistory.add(closed)

            riskManager.registerClosedTrade(
                pnlMyr = pnlMyr,
                closedAtMillis = candle.timestampMillis
            )
        }

        return PaperUpdateResult(
            closedTrades = closedTrades,
            openTrades = snapshotOpenTrades(),
            totalRealizedPnlMyr = totalRealizedPnlMyr
        )
    }

    /**
     * Compute P&L for a given trade at a specific close price.
     *
     * For now we only open LONGs, so this is straightforward:
     *  - LONG PnL = (close - entry) * quantityBase
     *  - (If SHORTs are added later, we handle them here.)
     */
    private fun computePnlMyr(
        trade: SimulatedTrade,
        closePrice: Double
    ): Double {
        return when (trade.direction) {
            TradeDirection.LONG -> (closePrice - trade.entryPrice) * trade.quantityBase
            TradeDirection.SHORT -> (trade.entryPrice - closePrice) * trade.quantityBase
        }
    }

    // Defensive utility, may be useful later if we clamp or validate anything.
    @Suppress("unused")
    private fun Double.isInvalidNumber(): Boolean {
        return !this.isFinite() || abs(this) > 1e12
    }
}
