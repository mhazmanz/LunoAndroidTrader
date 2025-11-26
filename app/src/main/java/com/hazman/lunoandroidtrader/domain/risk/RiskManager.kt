package com.hazman.lunoandroidtrader.domain.risk

import com.hazman.lunoandroidtrader.domain.model.AccountSnapshot
import com.hazman.lunoandroidtrader.domain.model.RiskConfig
import kotlin.math.max

/**
 * RiskManager holds the pure risk logic for:
 * - Position sizing
 * - Daily loss cap
 * - Trade count limits
 * - Cooldown logic (later we will track timestamps & session P&L)
 *
 * This class does NOT know about Android, Retrofit, or UI.
 * It only works with clean domain models.
 */
class RiskManager {

    /**
     * Compute the maximum capital (in MYR) that can be risked on a single trade,
     * given a RiskConfig and current AccountSnapshot.
     *
     * Example:
     * - equity = 50 MYR
     * - riskPerTradePercent = 1.0
     * => max risk per trade = 0.5 MYR
     */
    fun computeMaxRiskPerTradeMyr(
        riskConfig: RiskConfig,
        account: AccountSnapshot
    ): Double {
        val pct = max(0.0, riskConfig.riskPerTradePercent)
        return account.totalEquityMyr * (pct / 100.0)
    }

    /**
     * Given:
     * - RiskConfig
     * - current AccountSnapshot
     * - planned stop distance (% from entry to stop)
     *
     * Return the maximum position size (in MYR) that respects riskPerTradePercent.
     *
     * For example:
     * - equity = 50 MYR
     * - riskPerTradePercent = 1% => 0.5 MYR risk
     * - stopDistancePercent = 2% (if price moves -2% we hit SL)
     * => position size = 0.5 / (2/100) = 25 MYR
     */
    fun computePositionSizeMyr(
        riskConfig: RiskConfig,
        account: AccountSnapshot,
        stopDistancePercent: Double
    ): Double {
        val maxRiskMyr = computeMaxRiskPerTradeMyr(riskConfig, account)

        // Avoid division by zero or silly negative values
        val stopPct = if (stopDistancePercent <= 0.0) {
            // Fallback: assume a 1% stop if not specified
            1.0
        } else {
            stopDistancePercent
        }

        return maxRiskMyr / (stopPct / 100.0)
    }

    /**
     * Decide if we are allowed to open a new trade, based on:
     * - daily loss limit
     * - max trades per day
     * - cooldown after loss
     *
     * For now this is a stub that always returns true.
     * Later we will add:
     * - tracking of daily starting equity vs current equity
     * - counting trades opened today
     * - storing timestamp of last loss / last stop-out
     */
    fun canOpenNewTrade(
        riskConfig: RiskConfig,
        account: AccountSnapshot,
        tradesOpenedToday: Int,
        currentDailyDrawdownPercent: Double,
        isInCooldown: Boolean
    ): Boolean {
        // Check live trading flag (if off, we always allow "paper" trades)
        if (!riskConfig.liveTradingEnabled) {
            // This will be interpreted by higher layers as "paper trading allowed".
            return true
        }

        // Enforce daily loss limit
        if (currentDailyDrawdownPercent >= riskConfig.dailyLossLimitPercent) {
            return false
        }

        // Enforce max trades per day
        if (tradesOpenedToday >= riskConfig.maxTradesPerDay) {
            return false
        }

        // Enforce cooldown
        if (isInCooldown) {
            return false
        }

        return true
    }
}
