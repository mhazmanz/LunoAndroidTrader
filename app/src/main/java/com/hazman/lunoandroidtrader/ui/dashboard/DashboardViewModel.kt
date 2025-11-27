package com.hazman.lunoandroidtrader.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hazman.lunoandroidtrader.data.account.AccountRepository
import com.hazman.lunoandroidtrader.data.local.AppStorage
import com.hazman.lunoandroidtrader.data.luno.LunoApiClient
import com.hazman.lunoandroidtrader.data.luno.LunoPublicService
import com.hazman.lunoandroidtrader.domain.market.PriceCandle
import com.hazman.lunoandroidtrader.domain.market.SimulatedTrade
import com.hazman.lunoandroidtrader.domain.model.AccountSnapshot
import com.hazman.lunoandroidtrader.domain.model.RiskConfig
import com.hazman.lunoandroidtrader.domain.notifications.NotificationDispatcher
import com.hazman.lunoandroidtrader.domain.risk.RiskManager
import com.hazman.lunoandroidtrader.domain.strategy.SimpleStrategy
import com.hazman.lunoandroidtrader.domain.strategy.StrategyEngine
import com.hazman.lunoandroidtrader.domain.trading.PaperTradingEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * DashboardViewModel orchestrates:
 * - Loading the account snapshot from AccountRepository
 * - Loading RiskConfig from AppStorage
 * - Asking RiskManager for max risk per trade
 * - Delegating per-candle decisions to StrategyEngine
 * - Dispatching notifications via NotificationDispatcher when trades open
 * - Running optional auto paper-trading loop using live Luno prices
 */
class DashboardViewModel(
    private val accountRepository: AccountRepository,
    private val storage: AppStorage,
    private val riskManager: RiskManager,
    private val strategyEngine: StrategyEngine,
    private val lunoPublicService: LunoPublicService,
    private val notificationDispatcher: NotificationDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // Background job for auto paper-trading using live prices.
    private var autoPaperJob: Job? = null

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
                        openSimulatedTrades = strategyEngine.snapshotOpenTrades(),
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
     * Phase 1 legacy function:
     * Simulate a candle around a provided "fake" current price.
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

        // Fake candle: small range around the given price.
        val base = fakeCurrentPrice
        val open = base * 0.999
        val close = base * 1.001
        val high = maxOf(open, close) * 1.0005
        val low = minOf(open, close) * 0.9995

        val candle = PriceCandle(
            timestampMillis = System.currentTimeMillis(),
            open = open,
            high = high,
            low = low,
            close = close,
            volume = 1.0
        )

        val result = try {
            strategyEngine.runOnce(
                candle = candle,
                accountSnapshot = account,
                riskConfig = riskConfig
            )
        } catch (e: Exception) {
            _uiState.value = currentState.copy(
                lastStrategyDecision = "Error",
                lastSimulatedSignal = "Error while running strategy: ${e.message ?: "Unknown error"}",
                openSimulatedTrades = strategyEngine.snapshotOpenTrades()
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            lastStrategyDecision = result.decisionLabel,
            lastSimulatedSignal = "Fake price run @ RM ${fakeCurrentPrice.roundTwo()}.\n" + result.humanSignal,
            openSimulatedTrades = result.openTrades
        )

        // If a new simulated trade was opened, send a notification.
        result.newlyOpenedTrade?.let {
            val message = buildString {
                append("Simulated LONG opened (Fake price run).\n")
                append("Pair: ${it.pair}\n")
                append("Entry: ${it.entryPrice.roundTwo()}, ")
                append("SL: ${it.stopLossPrice.roundTwo()}, ")
                append("TP: ${it.takeProfitPrice.roundTwo()}\n")
                append("Risk per trade: RM ${it.riskAmountMyr.roundTwo()}")
            }
            viewModelScope.launch {
                notificationDispatcher.notifySignal(message)
            }
        }
    }

    /**
     * Run paper strategy once using a live ticker from Luno for the given pair (default XBTMYR).
     * This is a single-shot public entry point from the UI button.
     */
    fun runPaperStrategyOnceWithLivePrice(pair: String = "XBTMYR") {
        viewModelScope.launch {
            runPaperStrategyOnceWithLivePriceInternal(
                pair = pair,
                fromAutoLoop = false
            )
        }
    }

    /**
     * Internal suspend implementation for live-price paper-strategy run.
     * Used by both manual button and auto-trading loop.
     */
    private suspend fun runPaperStrategyOnceWithLivePriceInternal(
        pair: String,
        fromAutoLoop: Boolean
    ) {
        val currentState = _uiState.value
        val account = currentState.accountSnapshot
        val riskConfig = currentState.riskConfig

        if (account == null || riskConfig == null) {
            _uiState.value = currentState.copy(
                lastSimulatedSignal = "Cannot run strategy – account or risk config is not loaded."
            )
            return
        }

        if (!fromAutoLoop) {
            _uiState.value = _uiState.value.copy(
                lastStrategyDecision = "FetchingTicker",
                lastSimulatedSignal = "Fetching live ticker for $pair…"
            )
        }

        val tickerResponse = try {
            lunoPublicService.getTicker(pair)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                lastStrategyDecision = "TickerError",
                lastSimulatedSignal = "Error fetching ticker for $pair: ${e.message ?: "Unknown error"}"
            )
            return
        }

        if (!tickerResponse.isSuccessful) {
            _uiState.value = _uiState.value.copy(
                lastStrategyDecision = "TickerError",
                lastSimulatedSignal = "Ticker request failed for $pair. HTTP ${tickerResponse.code()}."
            )
            return
        }

        val ticker = tickerResponse.body()
        if (ticker == null) {
            _uiState.value = _uiState.value.copy(
                lastStrategyDecision = "TickerError",
                lastSimulatedSignal = "No ticker body returned for $pair."
            )
            return
        }

        val basePrice = ticker.lastTrade?.toDoubleOrNull()
            ?: ticker.bid?.toDoubleOrNull()
            ?: ticker.ask?.toDoubleOrNull()

        if (basePrice == null) {
            _uiState.value = _uiState.value.copy(
                lastStrategyDecision = "TickerError",
                lastSimulatedSignal = "Ticker for $pair did not contain a usable numeric price."
            )
            return
        }

        // Build a synthetic candle around the live price.
        val open = basePrice * 0.999
        val close = basePrice * 1.001
        val high = maxOf(open, close) * 1.0005
        val low = minOf(open, close) * 0.9995

        val candle = PriceCandle(
            timestampMillis = ticker.timestamp ?: System.currentTimeMillis(),
            open = open,
            high = high,
            low = low,
            close = close,
            volume = ticker.rolling24hVolume?.toDoubleOrNull() ?: 1.0
        )

        val result = try {
            strategyEngine.runOnce(
                candle = candle,
                accountSnapshot = account,
                riskConfig = riskConfig
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                lastStrategyDecision = "Error",
                lastSimulatedSignal = "Error while running strategy on live price: ${e.message ?: "Unknown error"}",
                openSimulatedTrades = strategyEngine.snapshotOpenTrades()
            )
            return
        }

        val infoPrefix = if (fromAutoLoop) {
            "Auto paper run – live $pair price ≈ RM ${basePrice.roundTwo()}.\n"
        } else {
            "Live $pair price ≈ RM ${basePrice.roundTwo()}.\n"
        }

        _uiState.value = _uiState.value.copy(
            lastStrategyDecision = result.decisionLabel,
            lastSimulatedSignal = buildString {
                append(infoPrefix)
                append(result.humanSignal)
            },
            openSimulatedTrades = result.openTrades,
            lastAutoPaperRunTimestampMillis = if (fromAutoLoop) System.currentTimeMillis() else _uiState.value.lastAutoPaperRunTimestampMillis
        )

        // If a new simulated trade was opened, send a notification.
        result.newlyOpenedTrade?.let {
            val message = buildString {
                append(
                    if (fromAutoLoop)
                        "Auto paper trading – simulated LONG opened (Live price run).\n"
                    else
                        "Simulated LONG opened (Live price run).\n"
                )
                append("Pair: ${it.pair}\n")
                append("Entry: ${it.entryPrice.roundTwo()}, ")
                append("SL: ${it.stopLossPrice.roundTwo()}, ")
                append("TP: ${it.takeProfitPrice.roundTwo()}\n")
                append("Risk per trade: RM ${it.riskAmountMyr.roundTwo()}")
            }
            // Notification route: Telegram if configured, else local notification.
            notificationDispatcher.notifySignal(message)
        }
    }

    /**
     * Start automatic paper-trading using live Luno prices.
     *
     * @param pair             Market pair, e.g. "XBTMYR".
     * @param intervalMinutes  Interval between runs. Minimum enforced to 1 minute.
     */
    fun startAutoPaperTrading(
        pair: String = "XBTMYR",
        intervalMinutes: Int = 5
    ) {
        val safeInterval = intervalMinutes.coerceAtLeast(1)

        // If already running, do nothing.
        if (autoPaperJob?.isActive == true) {
            _uiState.value = _uiState.value.copy(
                autoPaperStatusMessage = "Auto paper trading already running every $safeInterval minutes for $pair."
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isAutoPaperTradingEnabled = true,
            autoPaperIntervalMinutes = safeInterval,
            autoPaperStatusMessage = "Starting auto paper trading every $safeInterval minutes for $pair…"
        )

        autoPaperJob = viewModelScope.launch {
            while (isActive) {
                // Run the strategy once with live price.
                runPaperStrategyOnceWithLivePriceInternal(
                    pair = pair,
                    fromAutoLoop = true
                )

                // Wait for the configured interval before the next loop.
                val delayMillis = safeInterval * 60_000L
                delay(delayMillis)
            }
        }
    }

    /**
     * Stop automatic paper-trading if it is running.
     */
    fun stopAutoPaperTrading() {
        autoPaperJob?.cancel()
        autoPaperJob = null

        _uiState.value = _uiState.value.copy(
            isAutoPaperTradingEnabled = false,
            autoPaperStatusMessage = "Auto paper trading stopped."
        )
    }

    /**
     * Load RiskConfig cleanly from AppStorage, centralizing all defaulting logic.
     */
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

    /**
     * Local rounding helper, formatting to 2 decimal places and using
     * a grouped/locale-friendly format (e.g. "12,345.67").
     */
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
    val openSimulatedTrades: List<SimulatedTrade> = emptyList(),

    // Auto paper-trading
    val isAutoPaperTradingEnabled: Boolean = false,
    val autoPaperIntervalMinutes: Int = 5,
    val autoPaperStatusMessage: String? = null,
    val lastAutoPaperRunTimestampMillis: Long? = null
)

/**
 * Factory to create DashboardViewModel with the proper dependencies.
 */
class DashboardViewModelFactory(
    private val appStorage: AppStorage,
    private val notificationDispatcher: NotificationDispatcher
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            val lunoApiClient = LunoApiClient(appStorage)
            val accountRepository = AccountRepository(lunoApiClient)
            val riskManager = RiskManager()
            val paperTradingEngine = PaperTradingEngine(riskManager)
            val simpleStrategy = SimpleStrategy()
            val strategyEngine = StrategyEngine(
                simpleStrategy = simpleStrategy,
                paperTradingEngine = paperTradingEngine
            )
            val lunoPublicService = LunoPublicService.getInstance()

            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(
                accountRepository = accountRepository,
                storage = appStorage,
                riskManager = riskManager,
                strategyEngine = strategyEngine,
                lunoPublicService = lunoPublicService,
                notificationDispatcher = notificationDispatcher
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
