package com.hazman.lunoandroidtrader.domain.trading

import com.hazman.lunoandroidtrader.domain.market.PriceCandle
import com.hazman.lunoandroidtrader.domain.market.SimulatedTrade
import com.hazman.lunoandroidtrader.domain.market.TradeDirection
import com.hazman.lunoandroidtrader.domain.market.TradeStatus
import com.hazman.lunoandroidtrader.domain.model.AccountSnapshot
import com.hazman.lunoandroidtrader.domain.model.RiskConfig
import com.hazman.lunoandroidtrader.domain.risk.RiskDecision
import com.hazman.lunoandroidtrader.domain.risk.RiskManager
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
 */
class PaperTradingEngine(
    private val riskManager: RiskManager
) {

    private val openTrades = mutableListOf<SimulatedTrade>()
    private var totalRealizedPnlMyr: Double = 0.0
    private var nextTradeId: Long = 1L

    /**
     * Get a snapshot of current open trades.
     */
    fun snapshotOpenTrades(): List<SimulatedTrade> = openTrades.map { it.copy() }

    /**
     * Try to open a LONG trade on the given pair using the candle close as entry.
     *
     * Risk model:
     *  - Risk per trade (MYR) = equity * riskPerTradePercent / 100.
     *  - Stop loss = entryPrice * (1 - BASE_SL_PCT).
     *  - Take profit = entryPrice * (1 + 2 * BASE_SL_PCT). (2R target)
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

            // For LONG:
            val pnlMyr = (closePrice - trade.entryPrice) * trade.quantityBase
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
}
