package com.hazman.lunoandroidtrader.domain.strategy

import com.hazman.lunoandroidtrader.domain.market.PriceCandle
import com.hazman.lunoandroidtrader.domain.market.SimulatedTrade
import com.hazman.lunoandroidtrader.domain.model.AccountSnapshot
import com.hazman.lunoandroidtrader.domain.model.RiskConfig
import com.hazman.lunoandroidtrader.domain.trading.PaperTradingEngine
import kotlin.math.roundToInt

/**
 * Result of a single strategy evaluation + (simulated) execution step.
 *
 * This is a pure domain object – the UI layer consumes it to display:
 * - a short label (decisionLabel),
 * - a human-readable explanation (humanSignal),
 * - the current set of open simulated trades (openTrades).
 */
data class StrategyEngineResult(
    val decisionLabel: String,
    val humanSignal: String,
    val openTrades: List<SimulatedTrade>
)

/**
 * StrategyEngine is the high-level coordinator for strategy execution.
 *
 * Responsibilities:
 * - Accept a PriceCandle + AccountSnapshot + RiskConfig
 * - Ask the configured strategy (SimpleStrategy for now) for a decision
 * - If a trade should be opened, use PaperTradingEngine to size/execute it
 * - Return a StrategyEngineResult with all information needed by the UI
 *
 * This class:
 * - Knows nothing about Android, ViewModels, or Retrofit.
 * - Only works with domain models and pure logic.
 */
class StrategyEngine(
    private val simpleStrategy: SimpleStrategy,
    private val paperTradingEngine: PaperTradingEngine
) {

    /**
     * Run the strategy once for the given inputs.
     *
     * Upper layers must guarantee that:
     * - accountSnapshot and riskConfig are valid (non-null, sane values)
     * - candle comes from some price source (fake or real)
     */
    fun runOnce(
        candle: PriceCandle,
        accountSnapshot: AccountSnapshot,
        riskConfig: RiskConfig
    ): StrategyEngineResult {
        val decision = simpleStrategy.evaluate(candle)

        return when (decision) {
            is StrategyDecision.NoTrade -> {
                StrategyEngineResult(
                    decisionLabel = "NoTrade",
                    humanSignal = "Strategy decided: No trade for this candle.",
                    openTrades = paperTradingEngine.getOpenTrades()
                )
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
                        StrategyEngineResult(
                            decisionLabel = "OpenLong ${trade.pair} @ ${trade.entryPrice.roundTwo()}",
                            humanSignal = buildString {
                                append("Simulated LONG trade opened on ${trade.pair}.\n")
                                append("Entry: ${trade.entryPrice.roundTwo()}, ")
                                append("SL: ${trade.stopLossPrice.roundTwo()}, ")
                                append("TP: ${trade.takeProfitPrice.roundTwo()}\n")
                                append("Risk per trade: RM ${trade.riskAmountMyr.roundTwo()}")
                            },
                            openTrades = paperTradingEngine.getOpenTrades()
                        )
                    },
                    onFailure = { e ->
                        StrategyEngineResult(
                            decisionLabel = "OpenLongFailed",
                            humanSignal = "Failed to open simulated trade: ${e.message ?: "Unknown error"}",
                            openTrades = paperTradingEngine.getOpenTrades()
                        )
                    }
                )
            }
        }
    }

    /**
     * Convenience snapshot for upper layers that just want open trades
     * without running a new candle through the strategy.
     */
    fun snapshotOpenTrades(): List<SimulatedTrade> = paperTradingEngine.getOpenTrades()

    /**
     * Local rounding helper, formatting to 2 decimal places and using
     * a grouped/locale-friendly format (e.g. "12,345.67").
     */
    private fun Double.roundTwo(): String {
        val value = (this * 100.0).roundToInt() / 100.0
        return "%,.2f".format(value)
    }
}
