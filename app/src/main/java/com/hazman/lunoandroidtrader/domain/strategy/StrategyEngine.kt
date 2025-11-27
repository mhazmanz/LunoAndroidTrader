package com.hazman.lunoandroidtrader.domain.strategy

import com.hazman.lunoandroidtrader.domain.market.PriceCandle
import com.hazman.lunoandroidtrader.domain.market.SimulatedTrade
import com.hazman.lunoandroidtrader.domain.model.AccountSnapshot
import com.hazman.lunoandroidtrader.domain.model.RiskConfig
import com.hazman.lunoandroidtrader.domain.trading.ClosedTrade
import com.hazman.lunoandroidtrader.domain.trading.PaperTradingEngine
import com.hazman.lunoandroidtrader.domain.trading.PaperUpdateResult
import com.hazman.lunoandroidtrader.domain.trading.PerformanceSnapshot
import kotlin.math.roundToInt

/**
 * Result of running the strategy once on a single candle.
 *
 * This is what DashboardViewModel consumes.
 */
data class StrategyRunResult(
    val decisionLabel: String,
    val humanSignal: String,
    val openTrades: List<SimulatedTrade>,
    val newlyOpenedTrade: SimulatedTrade? = null,
    val closedTrades: List<ClosedTrade> = emptyList(),
    val totalRealizedPnlMyr: Double = 0.0
)

/**
 * StrategyEngine wires together:
 *  - Candle history
 *  - [SimpleStrategy] entry logic
 *  - [PaperTradingEngine] trade lifecycle and P&L
 *
 * It is called once per "tick" (in this app, once per fake or live candle).
 *
 * NEW (this step):
 *  - Exposes closed-trade history and performance snapshot, so UI can render
 *    a dedicated "Trade History / Performance" screen or section.
 */
class StrategyEngine(
    private val simpleStrategy: SimpleStrategy,
    private val paperTradingEngine: PaperTradingEngine
) {

    private val candleHistory = mutableListOf<PriceCandle>()

    /**
     * Main entry point: run the strategy on a single candle.
     *
     * @param candle          New candle to process.
     * @param accountSnapshot Current account snapshot (for sizing & risk).
     * @param riskConfig      Current risk configuration.
     */
    fun runOnce(
        candle: PriceCandle,
        accountSnapshot: AccountSnapshot,
        riskConfig: RiskConfig
    ): StrategyRunResult {
        candleHistory.add(candle)

        // 1) Update existing trades (check TP/SL).
        val updateResult: PaperUpdateResult =
            paperTradingEngine.updateOpenTrades(candle, riskConfig)

        // 2) Ask strategy whether to open a new trade.
        val decision = simpleStrategy.decide(candleHistory)
        var newlyOpenedTrade: SimulatedTrade? = null
        var decisionLabel = decision.label

        if (decision.shouldOpenLong) {
            val trade = paperTradingEngine.tryOpenLongTrade(
                pair = "XBTMYR",
                candle = candle,
                account = accountSnapshot,
                riskConfig = riskConfig
            )
            newlyOpenedTrade = trade
            if (trade == null) {
                decisionLabel += " | New LONG blocked by risk limits or invalid sizing."
            } else {
                decisionLabel += " | NEW LONG opened."
            }
        } else {
            decisionLabel += " | No new entries."
        }

        // 3) Build human-readable summary for the UI.
        val human = buildHumanSignal(
            candle = candle,
            decisionLabel = decisionLabel,
            updateResult = updateResult,
            newlyOpenedTrade = newlyOpenedTrade
        )

        return StrategyRunResult(
            decisionLabel = decisionLabel,
            humanSignal = human,
            openTrades = updateResult.openTrades,
            newlyOpenedTrade = newlyOpenedTrade,
            closedTrades = updateResult.closedTrades,
            totalRealizedPnlMyr = updateResult.totalRealizedPnlMyr
        )
    }

    /**
     * Snapshot of current open trades, used by Dashboard.
     */
    fun snapshotOpenTrades(): List<SimulatedTrade> = paperTradingEngine.snapshotOpenTrades()

    /**
     * Snapshot of closed trades across the entire session.
     *
     * @param limit Optional max number of trades (most recent) to return.
     */
    fun snapshotClosedTrades(limit: Int? = null): List<ClosedTrade> =
        paperTradingEngine.snapshotClosedTrades(limit)

    /**
     * Snapshot of performance stats across the entire session.
     *
     * Useful for:
     *  - A dedicated "Performance" card
     *  - A Trade History screen header
     *  - Deciding when to reset the paper session
     */
    fun snapshotPerformance(): PerformanceSnapshot =
        paperTradingEngine.snapshotPerformance()

    /**
     * Convenience: expose total realized P&L directly.
     */
    fun totalRealizedPnlMyr(): Double = paperTradingEngine.getTotalRealizedPnlMyr()

    /**
     * Optional: reset the paper session and candle history.
     *
     * Later we can wire this to a "Reset Paper Trading" button in Settings or
     * on the Dashboard.
     */
    fun resetSession() {
        candleHistory.clear()
        paperTradingEngine.resetSession()
    }

    /**
     * Build a readable explanation string for the last run, suitable for the UI.
     */
    private fun buildHumanSignal(
        candle: PriceCandle,
        decisionLabel: String,
        updateResult: PaperUpdateResult,
        newlyOpenedTrade: SimulatedTrade?
    ): String {
        val sb = StringBuilder()

        sb.append("Candle @ ${candle.timestampMillis} | ")
        sb.append("O=${candle.open.round2()}, H=${candle.high.round2()}, ")
        sb.append("L=${candle.low.round2()}, C=${candle.close.round2()}.\n")

        sb.append(decisionLabel).append("\n")

        if (updateResult.closedTrades.isNotEmpty()) {
            sb.append("Closed trades this run: ${updateResult.closedTrades.size}.\n")
            updateResult.closedTrades.forEach { closed ->
                sb.append(
                    " - ${closed.trade.pair} ${closed.reason} @ " +
                            "${closed.closePrice.round2()} | " +
                            "PnL: ${closed.pnlMyr.round2()} MYR.\n"
                )
            }
        }

        if (newlyOpenedTrade != null) {
            sb.append(
                "Opened new LONG ${newlyOpenedTrade.pair} @ " +
                        "${newlyOpenedTrade.entryPrice.round2()} | " +
                        "SL=${newlyOpenedTrade.stopLossPrice.round2()}, " +
                        "TP=${newlyOpenedTrade.takeProfitPrice.round2()}, " +
                        "Risk≈${newlyOpenedTrade.riskAmountMyr.round2()} MYR.\n"
            )
        }

        val performance = paperTradingEngine.snapshotPerformance()
        sb.append(
            "Open simulated trades: ${updateResult.openTrades.size}. " +
                    "Total realized P&L (paper): ${performance.totalRealizedPnlMyr.round2()} MYR. " +
                    "Closed trades: ${performance.totalClosedTrades}, " +
                    "Win rate: ${performance.winRatePercent.round2()}%."
        )

        return sb.toString()
    }

    private fun Double.round2(): String {
        val v = (this * 100.0).roundToInt() / 100.0
        return "%,.2f".format(v)
    }
}
