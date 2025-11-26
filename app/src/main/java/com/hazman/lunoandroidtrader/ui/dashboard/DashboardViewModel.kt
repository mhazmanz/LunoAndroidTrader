package com.hazman.lunoandroidtrader.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hazman.lunoandroidtrader.data.account.AccountRepository
import com.hazman.lunoandroidtrader.data.local.AppStorage
import com.hazman.lunoandroidtrader.data.luno.LunoApiClient
import com.hazman.lunoandroidtrader.domain.market.PriceCandle
import com.hazman.lunoandroidtrader.domain.model.AccountSnapshot
import com.hazman.lunoandroidtrader.domain.model.RiskConfig
import com.hazman.lunoandroidtrader.domain.risk.RiskManager
import com.hazman.lunoandroidtrader.domain.strategy.SimpleStrategy
import com.hazman.lunoandroidtrader.domain.strategy.StrategyDecision
import com.hazman.lunoandroidtrader.domain.trading.PaperTradingEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.round

/**
 * DashboardViewModel orchestrates:
 * - Loading account snapshot from AccountRepository
 * - Loading risk config from AppStorage
 * - Using RiskManager to compute risk-related metrics
 * - Running a simple paper-trading strategy + engine
 */
class DashboardViewModel(
    private val accountRepository: AccountRepository,
    private val storage: AppStorage,
    private val riskManager: RiskManager,
    private val tradingEngine: PaperTradingEngine,
    private val strategy: SimpleStrategy
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /**
     * Refresh account + risk configuration from data sources.
     */
    fun refresh() {
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null
        )

        viewModelScope.launch {
            val riskConfig = loadRiskConfigFromStorage()

            val accountResult = accountRepository.loadAccountSnapshot()
            accountResult.fold(
                onSuccess = { snapshot ->
                    val maxRiskMyr = riskManager.computeMaxRiskPerTradeMyr(
                        riskConfig = riskConfig,
                        account = snapshot
                    )

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        accountSnapshot = snapshot,
                        riskConfig = riskConfig,
                        maxRiskPerTradeMyr = maxRiskMyr,
                        errorMessage = null,
                        // update open trades snapshot
                        openSimulatedTrades = tradingEngine.getOpenTrades(),
                        lastStrategyDecision = _uiState.value.lastStrategyDecision,
                        lastSimulatedSignal = _uiState.value.lastSimulatedSignal
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        accountSnapshot = null,
                        riskConfig = riskConfig,
                        maxRiskPerTradeMyr = null,
                        errorMessage = e.message ?: e::class.java.simpleName,
                        openSimulatedTrades = emptyList()
                    )
                }
            )
        }
    }

    /**
     * For now we simulate a candle around a fake price,
     * run the strategy, and optionally open a simulated trade.
     *
     * Later we will replace this by real Luno ticker/price data.
     */
    fun runPaperStrategyOnce(fakeCurrentPrice: Double) {
        val currentState = _uiState.value
        val account = currentState.accountSnapshot
        val riskConfig = currentState.riskConfig

        if (account == null || riskConfig == null) {
            _uiState.value = currentState.copy(
                lastSimulatedSignal = "Cannot run strategy – account or risk config is not loaded."
            )
            return
        }

        // Fake candle: small random-ish range around current price.
        val base = fakeCurrentPrice
        val open = base * 0.999
        val close = base * 1.001
        val high = maxOf(open, close) * 1.001
        val low = minOf(open, close) * 0.999

        val candle = PriceCandle(
            timestampMillis = System.currentTimeMillis(),
            open = open,
            high = high,
            low = low,
            close = close,
            volume = 1.0
        )

        val decision = strategy.evaluate(candle)

        when (decision) {
            is StrategyDecision.NoTrade -> {
                _uiState.value = currentState.copy(
                    lastStrategyDecision = "NoTrade",
                    lastSimulatedSignal = "Strategy decided: No trade for this candle."
                )
            }

            is StrategyDecision.OpenLong -> {
                val openResult = tradingEngine.tryOpenLongTrade(
                    pair = decision.pair,
                    accountSnapshot = account,
                    riskConfig = riskConfig,
                    entryPrice = decision.entryPrice,
                    stopLossPrice = decision.stopLossPrice,
                    takeProfitPrice = decision.takeProfitPrice
                )

                openResult.fold(
                    onSuccess = { trade ->
                        _uiState.value = _uiState.value.copy(
                            lastStrategyDecision = "OpenLong ${trade.pair} @ ${trade.entryPrice.roundTwo()}",
                            lastSimulatedSignal = buildString {
                                append("Simulated LONG trade opened on ${trade.pair}.\n")
                                append("Entry: ${trade.entryPrice.roundTwo()}, ")
                                append("SL: ${trade.stopLossPrice.roundTwo()}, ")
                                append("TP: ${trade.takeProfitPrice.roundTwo()}\n")
                                append("Risk per trade: RM ${trade.riskAmountMyr.roundTwo()}")
                            },
                            openSimulatedTrades = tradingEngine.getOpenTrades()
                        )
                    },
                    onFailure = { e ->
                        _uiState.value = _uiState.value.copy(
                            lastStrategyDecision = "OpenLongFailed",
                            lastSimulatedSignal = "Failed to open simulated trade: ${e.message}",
                            openSimulatedTrades = tradingEngine.getOpenTrades()
                        )
                    }
                )
            }
        }
    }

    private fun loadRiskConfigFromStorage(): RiskConfig {
        val riskPct = storage.getRiskPerTradePercent()
        val dailyLossPct = storage.getDailyLossLimitPercent()
        val maxTrades = storage.getMaxTradesPerDay()
        val cooldownMin = storage.getCooldownMinutesAfterLoss()
        val liveTrading = storage.isLiveTradingEnabled()

        return RiskConfig(
            riskPerTradePercent = riskPct,
            dailyLossLimitPercent = dailyLossPct,
            maxTradesPerDay = maxTrades,
            cooldownMinutesAfterLoss = cooldownMin,
            liveTradingEnabled = liveTrading
        )
    }

    private fun Double.roundTwo(): String {
        val value = (this * 100.0).roundToInt() / 100.0
        return "%,.2f".format(value)
    }
}

/**
 * UI state for Dashboard screen.
 */
data class DashboardUiState(
    val isLoading: Boolean = false,
    val accountSnapshot: AccountSnapshot? = null,
    val riskConfig: RiskConfig? = null,
    val maxRiskPerTradeMyr: Double? = null,
    val errorMessage: String? = null,

    // Paper-trading / strategy related
    val lastStrategyDecision: String? = null,
    val lastSimulatedSignal: String? = null,
    val openSimulatedTrades: List<com.hazman.lunoandroidtrader.domain.market.SimulatedTrade> = emptyList()
)

/**
 * Factory to create DashboardViewModel with the proper dependencies.
 */
class DashboardViewModelFactory(
    private val appStorage: AppStorage
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            val lunoApiClient = LunoApiClient(appStorage)
            val accountRepository = AccountRepository(lunoApiClient)
            val riskManager = RiskManager()
            val tradingEngine = PaperTradingEngine(riskManager)
            val strategy = SimpleStrategy()
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(
                accountRepository = accountRepository,
                storage = appStorage,
                riskManager = riskManager,
                tradingEngine = tradingEngine,
                strategy = strategy
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
