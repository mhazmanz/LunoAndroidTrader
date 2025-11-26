package com.hazman.lunoandroidtrader.domain.market

/**
 * Basic price candle representation.
 * Later we can map real Luno OHLC data into this.
 */
data class PriceCandle(
    val timestampMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

/**
 * Direction of a trade.
 */
enum class TradeDirection {
    LONG,
    SHORT
}

/**
 * Simulated trade model for our paper-trading engine.
 */
data class SimulatedTrade(
    val id: Long,
    val pair: String,
    val direction: TradeDirection,
    val entryPrice: Double,
    val positionSizeBase: Double,   // e.g., XBT amount
    val riskAmountMyr: Double,      // how much MYR is at risk based on stopLossPrice
    val stopLossPrice: Double,
    val takeProfitPrice: Double,
    val openedAtMillis: Long,
    val closedAtMillis: Long? = null,
    val closePrice: Double? = null,
    val pnlMyr: Double? = null,
    val closeReason: CloseReason? = null
)

/**
 * Why the trade was closed.
 */
enum class CloseReason {
    STOP_LOSS,
    TAKE_PROFIT,
    MANUAL_EXIT,
    STRATEGY_EXIT
}
