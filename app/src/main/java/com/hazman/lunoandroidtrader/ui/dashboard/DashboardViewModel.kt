package com.hazman.lunoandroidtrader.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hazman.lunoandroidtrader.data.account.AccountRepository
import com.hazman.lunoandroidtrader.data.local.AppStorage
import com.hazman.lunoandroidtrader.data.luno.LunoApiClient
import com.hazman.lunoandroidtrader.domain.model.AccountSnapshot
import com.hazman.lunoandroidtrader.domain.model.RiskConfig
import com.hazman.lunoandroidtrader.domain.risk.RiskManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.round

/**
 * DashboardViewModel orchestrates:
 * - Loading account snapshot from AccountRepository
 * - Loading risk config from AppStorage
 * - Using RiskManager to compute risk-related metrics
 */
class DashboardViewModel(
    private val accountRepository: AccountRepository,
    private val storage: AppStorage,
    private val riskManager: RiskManager
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

                    _uiState.value = DashboardUiState(
                        isLoading = false,
                        accountSnapshot = snapshot,
                        riskConfig = riskConfig,
                        maxRiskPerTradeMyr = maxRiskMyr,
                        errorMessage = null
                    )
                },
                onFailure = { e ->
                    _uiState.value = DashboardUiState(
                        isLoading = false,
                        accountSnapshot = null,
                        riskConfig = riskConfig,
                        maxRiskPerTradeMyr = null,
                        errorMessage = e.message ?: e::class.java.simpleName
                    )
                }
            )
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
}

/**
 * UI state for Dashboard screen.
 */
data class DashboardUiState(
    val isLoading: Boolean = false,
    val accountSnapshot: AccountSnapshot? = null,
    val riskConfig: RiskConfig? = null,
    val maxRiskPerTradeMyr: Double? = null,
    val errorMessage: String? = null
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
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(accountRepository, appStorage, riskManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
