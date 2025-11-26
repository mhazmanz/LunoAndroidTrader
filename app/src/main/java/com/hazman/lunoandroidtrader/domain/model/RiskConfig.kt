package com.hazman.lunoandroidtrader.domain.model

/**
 * Risk configuration that drives how the strategy sizes positions and limits drawdown.
 *
 * All values are expressed in percentages or counts, not in currency,
 * so they can be applied regardless of account size.
 */
data class RiskConfig(
    val riskPerTradePercent: Double,
    val dailyLossLimitPercent: Double,
    val maxTradesPerDay: Int,
    val cooldownMinutesAfterLoss: Int,
    val liveTradingEnabled: Boolean
)
