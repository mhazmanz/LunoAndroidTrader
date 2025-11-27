package com.hazman.lunoandroidtrader.domain.market

/**
 * Basic OHLCV candle used by the strategy engine and paper trading.
 *
 * All prices are assumed to be in MYR for the current pair (e.g. XBTMYR).
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
 * Direction of a simulated trade. For now we only open LONGs, but the enum is
 * future-proofed for SHORTs if we add them later.
 */
enum class TradeDirection {
    LONG,
    SHORT
}

/**
 * Status of a simulated trade.
 *
 * We mainly track open trades in memory; closed trades are kept separately as
 * [ClosedTrade] entries in the paper engine. Status is therefore mostly for
 * debugging / future extensions.
 */
enum class TradeStatus {
    OPEN,
    CLOSED
}

/**
 * Simulated trade representation for the UI and notification layer.
 *
 * Important fields (already used in the app):
 *  - [pair]
 *  - [entryPrice]
 *  - [stopLossPrice]
 *  - [takeProfitPrice]
 *  - [riskAmountMyr]
 *
 * Additional fields make it easier to extend / debug without breaking the UI.
 */
data class SimulatedTrade(
    val id: Long,
    val pair: String,
    val direction: TradeDirection,
    val entryPrice: Double,
    val stopLossPrice: Double,
    val takeProfitPrice: Double,
    val quantityBase: Double,
    val riskAmountMyr: Double,
    val openedAtMillis: Long,
    val status: TradeStatus = TradeStatus.OPEN
)
