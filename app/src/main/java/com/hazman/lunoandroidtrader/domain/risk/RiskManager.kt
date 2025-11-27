package com.hazman.lunoandroidtrader.domain.risk

import com.hazman.lunoandroidtrader.domain.model.AccountSnapshot
import com.hazman.lunoandroidtrader.domain.model.RiskConfig
import kotlin.math.abs
import kotlin.math.max

/**
 * Result of checking whether a new trade can be opened.
 */
data class RiskDecision(
    val canOpen: Boolean,
    val reason: String? = null
)

/**
 * In-memory risk manager.
 *
 * Responsibilities:
 *  - Compute max risk per trade in MYR given [RiskConfig] + [AccountSnapshot].
 *  - Track very simple "daily" stats (trades opened, realized losses) and
 *    enforce:
 *      - Max trades per day
 *      - Daily loss limit
 *
 * Notes:
 *  - "Day" is approximated as UTC days from epoch (simple, deterministic).
 *  - State is held in-memory for the lifetime of the process. On app restart,
 *    counters reset. This is acceptable for an initial version.
 */
class RiskManager {

    // We store a simple "day key" derived from epoch millis (UTC).
    private var currentDayKey: Long? = null
    private var tradesOpenedToday: Int = 0
    private var realizedLossMyrToday: Double = 0.0

    /**
     * Ensure counters are synced with the current UTC "day".
     * If the day key changed, reset daily stats.
     */
    private fun ensureDay(nowMillis: Long) {
        val dayKey = nowMillis / (24L * 60L * 60L * 1000L)
        if (currentDayKey == null || currentDayKey != dayKey) {
            currentDayKey = dayKey
            tradesOpenedToday = 0
            realizedLossMyrToday = 0.0
        }
    }

    /**
     * Compute max risk per trade in MYR.
     *
     * riskPerTradePercent is interpreted as a percentage of account equity.
     * Example:
     *  - equity = 10,000 MYR
     *  - riskPerTradePercent = 1.5
     *  => 150 MYR risk per trade
     */
    fun computeMaxRiskPerTradeMyr(
        riskConfig: RiskConfig,
        account: AccountSnapshot
    ): Double {
        val equity = max(account.totalEquityMyr, 0.0)
        val pct = riskConfig.riskPerTradePercent.coerceAtLeast(0.0)
        return equity * pct / 100.0
    }

    /**
     * Evaluate whether opening a new trade is allowed under the risk config.
     *
     * Checks:
     *  - Max trades per day
     *  - Daily loss limit (based on equity and realizedLossMyrToday)
     */
    fun canOpenNewTrade(
        riskConfig: RiskConfig,
        account: AccountSnapshot,
        nowMillis: Long
    ): RiskDecision {
        ensureDay(nowMillis)

        // Max trades per day
        if (riskConfig.maxTradesPerDay > 0 &&
            tradesOpenedToday >= riskConfig.maxTradesPerDay
        ) {
            return RiskDecision(
                canOpen = false,
                reason = "Max trades per day reached (${riskConfig.maxTradesPerDay})."
            )
        }

        // Daily loss limit
        val equity = max(account.totalEquityMyr, 1.0)
        val dailyLossLimitPct = riskConfig.dailyLossLimitPercent.coerceAtLeast(0.0)
        if (dailyLossLimitPct > 0.0 && realizedLossMyrToday < 0.0) {
            val lossPct = abs(realizedLossMyrToday) / equity * 100.0
            if (lossPct >= dailyLossLimitPct) {
                return RiskDecision(
                    canOpen = false,
                    reason = "Daily loss limit reached (${lossPct.format2()}% ≥ ${dailyLossLimitPct}%)."
                )
            }
        }

        return RiskDecision(canOpen = true)
    }

    /**
     * Must be called whenever a trade is successfully opened.
     */
    fun registerOpenedTrade(nowMillis: Long) {
        ensureDay(nowMillis)
        tradesOpenedToday += 1
    }

    /**
     * Must be called whenever a trade is closed.
     *
     * We only track realized **losses** against the daily loss limit; profits
     * simply don't change [realizedLossMyrToday].
     */
    fun registerClosedTrade(pnlMyr: Double, closedAtMillis: Long) {
        ensureDay(closedAtMillis)
        if (pnlMyr < 0.0) {
            realizedLossMyrToday += pnlMyr
        }
    }

    private fun Double.format2(): String = "%,.2f".format(this)
}
