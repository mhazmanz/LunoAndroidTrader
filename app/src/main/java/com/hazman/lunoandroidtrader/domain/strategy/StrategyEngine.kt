package com.hazman.lunoandroidtrader.domain.strategy

import com.hazman.lunoandroidtrader.domain.market.PriceCandle
import com.hazman.lunoandroidtrader.domain.market.SimulatedTrade
import com.hazman.lunoandroidtrader.domain.model.AccountSnapshot
import com.hazman.lunoandroidtrader.domain.model.RiskConfig
import com.hazman.lunoandroidtrader.domain.trading.PaperTradingEngine
import com.hazman.lunoandroidtrader.domain.trading.PerformanceSnapshot
import kotlin.math.roundToInt

/**
 * Result of a single strategy evaluation + (simulated) execution step.
 *
 * newlyOpenedTrade:
 *  - null when no new trade was opened in this step
 *  - the SimulatedTrade that was just opened (if any)
 *
 * closedTrades:
 *  - trades that were CLOSED in this step (hit SL/TP on this candle)
 *
 * performanceSnapshot:
 *  - global performance metrics based on ALL closed trades so far.
 */
data class StrategyEngineResult(
    val decisionLabel: String,
    val humanSignal: String,
    val openTrades: List<SimulatedTrade>,
    val newlyOpenedTrade: SimulatedTrade?,
    val closedTrades: List<SimulatedTrade> = emptyList(),
    val performanceSnapshot: PerformanceSnapshot? = null
)

/**
 * Thin orchestration layer between:
 *  - SimpleStrategy (signal generation)
 *  - PaperTradingEngine (simulated execution)
 *
 * It:
 *  - feeds candles into the strategy
 *  - opens / closes simulated trades
 *  - assembles a human-friendly message
 *  - exposes performance metrics
 */
class StrategyEngine(
    private val simpleStrategy: SimpleStrategy,
    private val paperTradingEngine: PaperTradingEngine
) {

    /**
     * Run one strategy step on a single candle.
     */
    fun runOnce(
        candle: PriceCandle,
        accountSnapshot: AccountSnapshot,
        riskConfig: RiskConfig
    ): StrategyEngineResult {
        // 1) First, let the paper engine close any trades that hit SL/TP
        //    on this candle. This ensures "closedTrades" is always accurate.
        val closedTrades = paperTradingEngine.onNewPrice(
            pair = candle.pair,
            candle = candle
        )

        // 2) Ask strategy what to do with the latest candle
        val decision = simpleStrategy.evaluate(candle)

        // 3) Optional human-readable message components
        val humanLines = mutableListOf<String>()
        var newlyOpenedTrade: SimulatedTrade? = null

        // If any trades closed, include a short summary line
        if (closedTrades.isNotEmpty()) {
            val netPnl = closedTrades.sumOf { it.pnlMyr ?: 0.0 }
            val sign = if (netPnl >= 0.0) "+" else "-"
            val absPnl = kotlin.math.abs(netPnl)
            val label = "$sign${absPnl.roundTwo()} MYR closed on this candle (${closedTrades.size} trade(s))"
            humanLines.add(label)
        }

        when (decision) {
            StrategyDecision.NoTrade -> {
                humanLines.add("No new trade: conditions not met.")
            }

            is StrategyDecision.OpenLong -> {
                val openResult = paperTradingEngine.tryOpenLongTrade(
                    pair = decision.pair,
                    accountSnapshot = accountSnapshot,
                    riskConfig = riskConfig,
                    entryPrice = decision.entryPrice,
                    stopLossPrice = decision.stopLossPrice,
                    takeProfitPrice = decision.takeProfitPrice
                )

                openResult.fold(
                    onSuccess = { trade ->
                        newlyOpenedTrade = trade
                        humanLines.add(
                            "Opened LONG ${trade.pair} " +
                                    "entry=${trade.entryPrice.roundTwo()} " +
                                    "SL=${trade.stopLossPrice.roundTwo()} " +
                                    "TP=${trade.takeProfitPrice.roundTwo()} " +
                                    "size=${trade.positionSizeBase.roundFour()} base"
                        )
                    },
                    onFailure = { error ->
                        humanLines.add("Strategy signalled LONG ${decision.pair} but open failed: ${error.message}")
                    }
                )
            }
        }

        val performance = paperTradingEngine.getPerformanceSnapshot()

        val label = when (decision) {
            StrategyDecision.NoTrade -> "NoTrade"
            is StrategyDecision.OpenLong -> "OpenLong ${decision.pair}"
        }

        val humanSignal = humanLines.joinToString(separator = "\n").ifEmpty {
            // Fallback to something non-empty
            "Strategy processed candle for ${candle.pair}, no action taken."
        }

        return StrategyEngineResult(
            decisionLabel = label,
            humanSignal = humanSignal,
            openTrades = paperTradingEngine.getOpenTrades(),
            newlyOpenedTrade = newlyOpenedTrade,
            closedTrades = closedTrades,
            performanceSnapshot = performance
        )
    }

    /**
     * Convenience: current snapshot of OPEN trades (no side effects).
     */
    fun snapshotOpenTrades(): List<SimulatedTrade> = paperTradingEngine.getOpenTrades()

    /**
     * Convenience: snapshot of CLOSED trades so far.
     */
    fun snapshotClosedTrades(): List<SimulatedTrade> = paperTradingEngine.getClosedTrades()

    /**
     * Convenience: snapshot of performance metrics.
     */
    fun snapshotPerformance(): PerformanceSnapshot = paperTradingEngine.getPerformanceSnapshot()
}

/**
 * Round a Double to 2 decimal places for human-facing messages.
 */
fun Double.roundTwo(): Double =
    ((this * 100.0).roundToInt() / 100.0)

/**
 * Round a Double to 4 decimal places (for position sizes).
 */
fun Double.roundFour(): Double =
    ((this * 10_000.0).roundToInt() / 10_000.0)
