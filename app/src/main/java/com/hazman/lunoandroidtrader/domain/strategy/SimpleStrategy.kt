package com.hazman.lunoandroidtrader.domain.strategy

import com.hazman.lunoandroidtrader.domain.market.PriceCandle

/**
 * High-level decision: should we open a trade, and if yes, with what configuration.
 *
 * This is a simplified placeholder for Phase 1–2.
 */
sealed class StrategyDecision {
    data object NoTrade : StrategyDecision()

    data class OpenLong(
        val pair: String,
        val entryPrice: Double,
        val stopLossPrice: Double,
        val takeProfitPrice: Double
    ) : StrategyDecision()
}

/**
 * SimpleStrategy is a placeholder strategy.
 *
 * For now, we will:
 * - Take a single last candle.
 * - Use a trivial rule:
 *      If close > open (green candle) => propose a long with:
 *          entryPrice = close
 *          stopLoss = close * (1 - 0.01)   // 1% below
 *          takeProfit = close * (1 + 0.02) // 2% above
 *      Else => NoTrade
 *
 * Later we will replace this with real indicators / multi-timeframe logic.
 */
class SimpleStrategy(
    private val defaultPair: String = "XBTMYR"
) {

    fun evaluate(latestCandle: PriceCandle?): StrategyDecision {
        if (latestCandle == null) {
            return StrategyDecision.NoTrade
        }

        // Trivial placeholder logic:
        return if (latestCandle.close > latestCandle.open) {
            val entry = latestCandle.close
            val stopLoss = entry * (1.0 - 0.01)   // 1% below
            val takeProfit = entry * (1.0 + 0.02) // 2% above

            StrategyDecision.OpenLong(
                pair = defaultPair,
                entryPrice = entry,
                stopLossPrice = stopLoss,
                takeProfitPrice = takeProfit
            )
        } else {
            StrategyDecision.NoTrade
        }
    }
}
