package com.hazman.lunoandroidtrader.domain.trading

import com.hazman.lunoandroidtrader.domain.market.PriceCandle
import com.hazman.lunoandroidtrader.domain.market.SimulatedTrade
import com.hazman.lunoandroidtrader.domain.market.TradeCloseReason
import com.hazman.lunoandroidtrader.domain.market.TradeDirection
import com.hazman.lunoandroidtrader.domain.model.AccountSnapshot
import com.hazman.lunoandroidtrader.domain.model.RiskConfig
import com.hazman.lunoandroidtrader.domain.risk.RiskManager
import kotlin.math.abs
import kotlin.math.max

/**
 * Simple in-memory paper trading engine.
 *
 * Responsibilities:
 *  - Maintain a list of OPEN and CLOSED simulated trades.
 *  - Enforce basic risk per trade using [RiskManager].
 *  - Apply stop-loss / take-profit logic on each new candle.
 *  - Compute performance statistics over CLOSED trades.
 *
 * Thread-safety: this class is NOT thread-safe; it is meant to be used
 * from a single coroutine / single thread (as we do in the ViewModel).
 */
class PaperTradingEngine(
    private val riskManager: RiskManager
) {

    private val openTrades = mutableListOf<SimulatedTrade>()
    private val closedTrades = mutableListOf<SimulatedTrade>()
    private var nextTradeId: Long = 1L

    /**
     * Completely wipe the in-memory paper trading state.
     * Useful when user changes pair, timeframe, or wants a clean slate.
     */
    fun reset() {
        openTrades.clear()
        closedTrades.clear()
        nextTradeId = 1L
    }

    /**
     * Shallow copy of current OPEN trades.
     */
    fun getOpenTrades(): List<SimulatedTrade> = openTrades.toList()

    /**
     * Shallow copy of all CLOSED trades (historical ledger).
     */
    fun getClosedTrades(): List<SimulatedTrade> = closedTrades.toList()

    /**
     * Attempt to open a LONG trade given a strategy decision.
     *
     * Risk logic:
     *  - Uses RiskManager to compute maximum MYR risk per trade.
     *  - Converts that risk into a base-asset position size based on
     *    distance between entry and stop-loss.
     */
    fun tryOpenLongTrade(
        pair: String,
        accountSnapshot: AccountSnapshot,
        riskConfig: RiskConfig,
        entryPrice: Double,
        stopLossPrice: Double,
        takeProfitPrice: Double
    ): Result<SimulatedTrade> {
        if (entryPrice <= 0.0 || stopLossPrice <= 0.0 || takeProfitPrice <= 0.0) {
            return Result.failure(IllegalArgumentException("Invalid price(s) for simulated trade."))
        }

        if (stopLossPrice >= entryPrice) {
            return Result.failure(IllegalArgumentException("For a LONG trade, stop-loss must be BELOW entry price."))
        }

        // How much MYR are we allowed to risk on this trade?
        val maxRiskMyr = riskManager.computeMaxRiskPerTradeMyr(
            riskConfig = riskConfig,
            accountSnapshot = accountSnapshot
        )

        if (maxRiskMyr <= 0.0) {
            return Result.failure(IllegalStateException("Risk per trade is zero or negative; cannot open trade."))
        }

        val positionSizeBase = computePositionSizeBaseForRisk(
            maxRiskMyr = maxRiskMyr,
            entryPrice = entryPrice,
            stopLossPrice = stopLossPrice
        )

        if (positionSizeBase <= 0.0) {
            return Result.failure(IllegalStateException("Computed position size is zero; check risk settings."))
        }

        val positionSizeQuote = positionSizeBase * entryPrice
        val nowMillis = System.currentTimeMillis()

        val trade = SimulatedTrade(
            tradeId = nextTradeId++,
            pair = pair,
            direction = TradeDirection.LONG,
            openedAtMillis = nowMillis,
            entryPrice = entryPrice,
            stopLossPrice = stopLossPrice,
            takeProfitPrice = takeProfitPrice,
            positionSizeBase = positionSizeBase,
            positionSizeQuote = positionSizeQuote,
            // Defaults for fields that will be filled on close:
            closedAtMillis = null,
            closePrice = null,
            pnlMyr = null,
            closeReason = null
        )

        openTrades.add(trade)
        return Result.success(trade)
    }

    /**
     * Process a NEW candle and close any trades that hit SL/TP.
     *
     * Returns the list of trades that were closed because of THIS candle
     * (there can be zero, one, or many).
     */
    fun onNewPrice(
        pair: String,
        candle: PriceCandle
    ): List<SimulatedTrade> {
        if (openTrades.isEmpty()) {
            return emptyList()
        }

        val closedInThisStep = mutableListOf<SimulatedTrade>()
        val iterator = openTrades.iterator()
        val nowMillis = candle.timestampMillis ?: System.currentTimeMillis()

        while (iterator.hasNext()) {
            val trade = iterator.next()

            // Only manage trades for this pair
            if (trade.pair != pair) {
                continue
            }

            when (trade.direction) {
                TradeDirection.LONG -> {
                    val stopLossHit = candle.low <= trade.stopLossPrice
                    val takeProfitHit = candle.high >= trade.takeProfitPrice

                    val closePrice: Double?
                    val reason: TradeCloseReason?

                    when {
                        stopLossHit -> {
                            closePrice = trade.stopLossPrice
                            reason = TradeCloseReason.STOP_LOSS
                        }
                        takeProfitHit -> {
                            closePrice = trade.takeProfitPrice
                            reason = TradeCloseReason.TAKE_PROFIT
                        }
                        else -> {
                            // Still open; nothing to do
                            continue
                        }
                    }

                    val pnlMyr = computePnlMyr(
                        direction = trade.direction,
                        positionSizeBase = trade.positionSizeBase,
                        entryPrice = trade.entryPrice,
                        exitPrice = closePrice
                    )

                    val closedTrade = trade.copy(
                        closedAtMillis = nowMillis,
                        closePrice = closePrice,
                        pnlMyr = pnlMyr,
                        closeReason = reason
                    )

                    iterator.remove()
                    closedTrades.add(closedTrade)
                    closedInThisStep.add(closedTrade)
                }

                TradeDirection.SHORT -> {
                    // We do not yet open SHORT trades in the current strategy,
                    // but we keep the branch explicit for future extension.
                    continue
                }
            }
        }

        return closedInThisStep
    }

    /**
     * Compute a performance snapshot based ONLY on CLOSED trades.
     *
     * This is safe to call at any time; if there are no closed trades yet,
     * it will just return zeros.
     */
    fun getPerformanceSnapshot(): PerformanceSnapshot {
        if (closedTrades.isEmpty()) {
            return PerformanceSnapshot(
                totalTrades = 0,
                winningTrades = 0,
                losingTrades = 0,
                breakevenTrades = 0,
                winRatePercent = 0.0,
                grossProfitMyr = 0.0,
                grossLossMyr = 0.0,
                netProfitMyr = 0.0,
                maxDrawdownMyr = 0.0,
                averageRMultiple = null
            )
        }

        var totalTrades = 0
        var winningTrades = 0
        var losingTrades = 0
        var breakevenTrades = 0

        var grossProfit = 0.0
        var grossLoss = 0.0
        var cumulativeEquity = 0.0
        var peakEquity = 0.0
        var maxDrawdown = 0.0

        var rSum = 0.0
        var rCount = 0

        // Sort by close time for consistent equity curve
        val ordered = closedTrades.sortedBy { it.closedAtMillis ?: Long.MAX_VALUE }

        for (trade in ordered) {
            val pnl = trade.pnlMyr ?: 0.0
            totalTrades += 1

            when {
                pnl > 0.0 -> {
                    winningTrades += 1
                    grossProfit += pnl
                }

                pnl < 0.0 -> {
                    losingTrades += 1
                    grossLoss += abs(pnl)
                }

                else -> {
                    breakevenTrades += 1
                }
            }

            // Equity curve & drawdown in MYR
            cumulativeEquity += pnl
            peakEquity = max(peakEquity, cumulativeEquity)
            val drawdown = peakEquity - cumulativeEquity
            maxDrawdown = max(maxDrawdown, drawdown)

            // R-multiple = PnL / initialRisk
            val riskPerUnit = abs(trade.entryPrice - trade.stopLossPrice)
            val riskMyr = trade.positionSizeBase * riskPerUnit
            if (riskMyr > 0.0) {
                val rMultiple = pnl / riskMyr
                rSum += rMultiple
                rCount += 1
            }
        }

        val netProfit = grossProfit - grossLoss
        val winRatePercent = if (totalTrades > 0) {
            (winningTrades.toDouble() / totalTrades.toDouble()) * 100.0
        } else {
            0.0
        }

        val avgR = if (rCount > 0) rSum / rCount.toDouble() else null

        return PerformanceSnapshot(
            totalTrades = totalTrades,
            winningTrades = winningTrades,
            losingTrades = losingTrades,
            breakevenTrades = breakevenTrades,
            winRatePercent = winRatePercent,
            grossProfitMyr = grossProfit,
            grossLossMyr = grossLoss,
            netProfitMyr = netProfit,
            maxDrawdownMyr = maxDrawdown,
            averageRMultiple = avgR
        )
    }

    /**
     * Convert max MYR risk into base-asset position size.
     */
    private fun computePositionSizeBaseForRisk(
        maxRiskMyr: Double,
        entryPrice: Double,
        stopLossPrice: Double
    ): Double {
        val riskPerUnit = abs(entryPrice - stopLossPrice)
        if (riskPerUnit <= 0.0) return 0.0
        val size = maxRiskMyr / riskPerUnit
        return if (size.isFinite() && size > 0.0) size else 0.0
    }

    /**
     * Compute realised PnL when closing a position.
     */
    private fun computePnlMyr(
        direction: TradeDirection,
        positionSizeBase: Double,
        entryPrice: Double,
        exitPrice: Double
    ): Double {
        val priceDiff = when (direction) {
            TradeDirection.LONG -> exitPrice - entryPrice
            TradeDirection.SHORT -> entryPrice - exitPrice
        }
        return positionSizeBase * priceDiff
    }
}
