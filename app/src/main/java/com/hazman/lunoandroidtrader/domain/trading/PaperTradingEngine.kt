package com.hazman.lunoandroidtrader.domain.trading

import com.hazman.lunoandroidtrader.domain.market.CloseReason
import com.hazman.lunoandroidtrader.domain.market.PriceCandle
import com.hazman.lunoandroidtrader.domain.market.SimulatedTrade
import com.hazman.lunoandroidtrader.domain.market.TradeDirection
import com.hazman.lunoandroidtrader.domain.model.AccountSnapshot
import com.hazman.lunoandroidtrader.domain.model.RiskConfig
import com.hazman.lunoandroidtrader.domain.risk.RiskManager
import kotlin.math.abs

/**
 * PaperTradingEngine:
 * - Runs completely in memory; not persisted yet.
 * - Uses RiskManager to size positions based on equity & risk%.
 * - Accepts fake/real prices as PriceCandle and updates open positions.
 */
class PaperTradingEngine(
    private val riskManager: RiskManager
) {

    private val openTrades = mutableListOf<SimulatedTrade>()
    private val closedTrades = mutableListOf<SimulatedTrade>()

    private var nextTradeId: Long = 1L

    /**
     * Get snapshot of current open trades.
     */
    fun getOpenTrades(): List<SimulatedTrade> = openTrades.toList()

    /**
     * Get snapshot of closed trades.
     */
    fun getClosedTrades(): List<SimulatedTrade> = closedTrades.toList()

    /**
     * Main entry to request a new trade.
     *
     * For now, we only support LONG direction.
     * Later we can add SHORT support if needed and if exchange allows it.
     */
    fun tryOpenLongTrade(
        pair: String,
        accountSnapshot: AccountSnapshot,
        riskConfig: RiskConfig,
        entryPrice: Double,
        stopLossPrice: Double,
        takeProfitPrice: Double
    ): Result<SimulatedTrade> {
        // Compute how much RM we are allowed to risk.
        val maxRisk = riskManager.computeMaxRiskPerTradeMyr(riskConfig, accountSnapshot)
        if (maxRisk <= 0.0) {
            return Result.failure(IllegalStateException("Max risk per trade is zero or negative. Check risk settings or account balance."))
        }

        // Calculate position size so that:
        // riskAmountMyr ≈ maxRisk, based on entryPrice and stopLossPrice.
        val positionSizeBase = computePositionSizeBaseForRisk(
            maxRiskMyr = maxRisk,
            entryPrice = entryPrice,
            stopLossPrice = stopLossPrice
        )

        if (positionSizeBase <= 0.0) {
            return Result.failure(IllegalStateException("Computed position size is zero. Check entry/stop prices."))
        }

        val trade = SimulatedTrade(
            id = nextTradeId++,
            pair = pair,
            direction = TradeDirection.LONG,
            entryPrice = entryPrice,
            positionSizeBase = positionSizeBase,
            riskAmountMyr = maxRisk,
            stopLossPrice = stopLossPrice,
            takeProfitPrice = takeProfitPrice,
            openedAtMillis = System.currentTimeMillis()
        )

        openTrades.add(trade)
        return Result.success(trade)
    }

    /**
     * Update all open trades with a new price candle.
     *
     * - If candle's low <= stopLossPrice => close at stopLossPrice (STOP_LOSS)
     * - Else if candle's high >= takeProfitPrice => close at takeProfitPrice (TAKE_PROFIT)
     *
     * For simplicity, we assume one price per trade; later we can use more refined logic.
     */
    fun onNewPrice(pair: String, candle: PriceCandle): List<SimulatedTrade> {
        val affectedTrades = mutableListOf<SimulatedTrade>()

        val iterator = openTrades.iterator()
        while (iterator.hasNext()) {
            val trade = iterator.next()
            if (trade.pair != pair) {
                continue
            }

            var shouldClose = false
            var closeReason: CloseReason? = null
            var closePrice = candle.close

            // Long position:
            // - stop-loss if price falls to/below stopLossPrice
            // - take-profit if price rises to/above takeProfitPrice
            if (trade.direction == TradeDirection.LONG) {
                if (candle.low <= trade.stopLossPrice) {
                    shouldClose = true
                    closeReason = CloseReason.STOP_LOSS
                    closePrice = trade.stopLossPrice
                } else if (candle.high >= trade.takeProfitPrice) {
                    shouldClose = true
                    closeReason = CloseReason.TAKE_PROFIT
                    closePrice = trade.takeProfitPrice
                }
            }

            if (shouldClose && closeReason != null) {
                val pnl = computePnlMyr(
                    direction = trade.direction,
                    positionSizeBase = trade.positionSizeBase,
                    entryPrice = trade.entryPrice,
                    exitPrice = closePrice
                )

                val closed = trade.copy(
                    closedAtMillis = candle.timestampMillis,
                    closePrice = closePrice,
                    pnlMyr = pnl,
                    closeReason = closeReason
                )

                iterator.remove()
                closedTrades.add(closed)
                affectedTrades.add(closed)
            }
        }

        return affectedTrades.toList()
    }

    /**
     * Very simplistic calculation:
     *
     * For LONG:
     * riskAmountMyr = positionSizeBase * abs(entryPrice - stopLossPrice)
     *
     * => positionSizeBase = riskAmountMyr / abs(entryPrice - stopLossPrice)
     */
    private fun computePositionSizeBaseForRisk(
        maxRiskMyr: Double,
        entryPrice: Double,
        stopLossPrice: Double
    ): Double {
        val priceDiff = abs(entryPrice - stopLossPrice)
        if (priceDiff <= 0.0) return 0.0
        return maxRiskMyr / priceDiff
    }

    /**
     * PnL in MYR for a closed trade.
     */
    private fun computePnlMyr(
        direction: TradeDirection,
        positionSizeBase: Double,
        entryPrice: Double,
        exitPrice: Double
    ): Double {
        val diff = exitPrice - entryPrice
        val pnlPerUnit = when (direction) {
            TradeDirection.LONG -> diff
            TradeDirection.SHORT -> -diff
        }
        return positionSizeBase * pnlPerUnit
    }
}
