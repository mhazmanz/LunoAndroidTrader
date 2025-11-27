package com.hazman.lunoandroidtrader.domain.strategy

import com.hazman.lunoandroidtrader.domain.market.PriceCandle
import kotlin.math.roundToInt

/**
 * Decision returned by [SimpleStrategy].
 *
 * @param label        Human-readable label, used in the UI.
 * @param shouldOpenLong Whether the strategy wants to open a new LONG trade on this candle.
 */
data class StrategyDecision(
    val label: String,
    val shouldOpenLong: Boolean
)

/**
 * Simple trend-following strategy, long-only, based on EMA crossover and a
 * basic volatility filter.
 *
 * Rules (per candle):
 *  - Compute EMA fast (e.g. 9) and EMA slow (e.g. 21).
 *  - Enter LONG when:
 *      - EMA fast crosses above EMA slow ("bullish crossover"), AND
 *      - Close price above both EMAs, AND
 *      - Candle body size is not extremely tiny (avoid noise).
 *
 * Exit is handled via TP/SL in the paper engine; this strategy focuses on
 * entries.
 */
class SimpleStrategy {

    /**
     * Decide whether to open a new LONG trade on this candle, given the
     * historical candles (including the latest one).
     */
    fun decide(
        history: List<PriceCandle>
    ): StrategyDecision {
        if (history.size < 25) {
            return StrategyDecision(
                label = "Insufficient history (need >= 25 candles)",
                shouldOpenLong = false
            )
        }

        val closes = history.map { it.close }
        val emaFastPeriod = 9
        val emaSlowPeriod = 21

        val emaFastSeries = computeEmaSeries(closes, emaFastPeriod)
        val emaSlowSeries = computeEmaSeries(closes, emaSlowPeriod)

        val lastIndex = closes.lastIndex

        val emaFast = emaFastSeries[lastIndex]
        val emaSlow = emaSlowSeries[lastIndex]
        val prevEmaFast = emaFastSeries[lastIndex - 1]
        val prevEmaSlow = emaSlowSeries[lastIndex - 1]

        val lastCandle = history[lastIndex]

        // Bullish crossover condition.
        val wasBelow = prevEmaFast <= prevEmaSlow
        val nowAbove = emaFast > emaSlow
        val bullishCross = wasBelow && nowAbove

        // Price above both EMAs.
        val priceAboveEma = lastCandle.close > emaFast && lastCandle.close > emaSlow

        // Avoid extremely tiny candles (low volatility noise).
        val body = kotlin.math.abs(lastCandle.close - lastCandle.open)
        val avgBody = history.takeLast(20).map { kotlin.math.abs(it.close - it.open) }
            .filter { it > 0.0 }
            .ifEmpty { listOf(1.0) }
            .average()
        val bodyFactor = if (avgBody > 0.0) body / avgBody else 1.0
        val bodyOk = bodyFactor >= 0.5 // at least half of recent average body

        val shouldOpen = bullishCross && priceAboveEma && bodyOk

        val label = buildString {
            append("Fast EMA: ${emaFast.round2()}, Slow EMA: ${emaSlow.round2()}. ")
            append("BullishCross=$bullishCross, PriceAboveEma=$priceAboveEma, BodyOk=$bodyOk. ")
            append(if (shouldOpen) "Signal: OPEN LONG." else "Signal: HOLD.")
        }

        return StrategyDecision(
            label = label,
            shouldOpenLong = shouldOpen
        )
    }

    /**
     * Compute EMA series for an entire list of close prices.
     *
     * We use the standard EMA smoothing constant: alpha = 2 / (period + 1).
     */
    private fun computeEmaSeries(
        values: List<Double>,
        period: Int
    ): List<Double> {
        if (values.isEmpty() || period <= 1) return values

        val alpha = 2.0 / (period + 1.0)
        val ema = MutableList(values.size) { 0.0 }

        // Seed EMA with simple average of first [period] values.
        val seedCount = minOf(period, values.size)
        val seedAvg = values.take(seedCount).average()
        for (i in 0 until seedCount) {
            ema[i] = seedAvg
        }

        for (i in seedCount until values.size) {
            val prev = ema[i - 1]
            ema[i] = alpha * values[i] + (1 - alpha) * prev
        }

        return ema
    }

    private fun Double.round2(): String {
        val v = (this * 100.0).roundToInt() / 100.0
        return "%,.2f".format(v)
    }
}
