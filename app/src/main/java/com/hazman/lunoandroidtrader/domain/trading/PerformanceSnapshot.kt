package com.hazman.lunoandroidtrader.domain.trading

/**
 * Snapshot of paper-trading performance based on all CLOSED trades.
 *
 * All numbers are in MYR (Malaysian Ringgit) where applicable.
 */
data class PerformanceSnapshot(
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val breakevenTrades: Int,
    val winRatePercent: Double,
    val grossProfitMyr: Double,
    val grossLossMyr: Double,
    val netProfitMyr: Double,
    val maxDrawdownMyr: Double,
    val averageRMultiple: Double?
)
