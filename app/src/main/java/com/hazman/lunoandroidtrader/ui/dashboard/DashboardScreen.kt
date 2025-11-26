package com.hazman.lunoandroidtrader.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hazman.lunoandroidtrader.data.local.AppStorage
import kotlin.math.roundToInt

/**
 * DashboardScreen shows:
 * - Account overview (approx equity MYR, free MYR balance, balances by asset)
 * - Risk overview (risk config values)
 * - Derived risk metrics (max risk per trade in MYR)
 * - Strategy / paper-trading status
 *
 * Still paper-only – no real orders.
 */
@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val storage = remember { AppStorage(context) }

    val viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModelFactory(storage)
    )

    val uiState by viewModel.uiState.collectAsState()

    // Auto-refresh once when screen is shown
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {

        Text(
            text = "Account Overview",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            Text(
                text = "Loading account and risk data...",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        uiState.errorMessage?.let { error ->
            Text(
                text = "Error: $error",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        uiState.accountSnapshot?.let { snapshot ->
            AccountCard(snapshot, uiState.maxRiskPerTradeMyr)
            Spacer(modifier = Modifier.height(16.dp))
        }

        uiState.riskConfig?.let { riskConfig ->
            RiskConfigCard(
                riskPerTradePercent = riskConfig.riskPerTradePercent,
                dailyLossLimitPercent = riskConfig.dailyLossLimitPercent,
                maxTradesPerDay = riskConfig.maxTradesPerDay,
                cooldownMinutes = riskConfig.cooldownMinutesAfterLoss,
                liveTradingEnabled = riskConfig.liveTradingEnabled
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        StrategyCard(
            lastDecision = uiState.lastStrategyDecision,
            lastSignal = uiState.lastSimulatedSignal,
            openTradesCount = uiState.openSimulatedTrades.size,
            onRunOnce = {
                // For now, assume a fake BTC/MYR current price.
                viewModel.runPaperStrategyOnce(fakeCurrentPrice = 250_000.0)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { viewModel.refresh() }
        ) {
            Text(text = "Refresh Account & Risk")
        }
    }
}

@Composable
private fun AccountCard(
    snapshot: com.hazman.lunoandroidtrader.domain.model.AccountSnapshot,
    maxRiskPerTradeMyr: Double?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Balance Snapshot",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Approx Total Equity: RM ${snapshot.totalEquityMyr.roundToTwoDecimals()}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Free MYR Balance: RM ${snapshot.freeBalanceMyr.roundToTwoDecimals()}",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Balances by Asset:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            snapshot.balances.forEach { b ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = b.asset)
                    Text(text = b.amount.roundToEightOrTwo())
                }
            }

            maxRiskPerTradeMyr?.let { riskMyr ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Max risk per trade: RM ${riskMyr.roundToTwoDecimals()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun RiskConfigCard(
    riskPerTradePercent: Double,
    dailyLossLimitPercent: Double,
    maxTradesPerDay: Int,
    cooldownMinutes: Int,
    liveTradingEnabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Risk Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Risk per trade: ${riskPerTradePercent.roundToTwoDecimals()} %",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Daily loss limit: ${dailyLossLimitPercent.roundToTwoDecimals()} %",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Max trades per day: $maxTradesPerDay",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Cooldown after loss: $cooldownMinutes minutes",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Live trading mode: ${if (liveTradingEnabled) "ON (future)" else "OFF / paper only"}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun StrategyCard(
    lastDecision: String?,
    lastSignal: String?,
    openTradesCount: Int,
    onRunOnce: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Strategy & Paper Trading",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Mode: PAPER ONLY (no real orders yet)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Open simulated trades: $openTradesCount",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Last decision: ${lastDecision ?: "—"}",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = lastSignal ?: "No strategy run yet.",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRunOnce
            ) {
                Text(text = "Run Paper Strategy Once (Fake Price)")
            }
        }
    }
}

private fun Double.roundToTwoDecimals(): String {
    val value = (this * 100.0).roundToInt() / 100.0
    return "%,.2f".format(value)
}

private fun Double.roundToEightOrTwo(): String {
    // If it's a "crypto-looking" small number, show up to 8 decimals.
    return if (this in 0.0..1.0) {
        "%,.8f".format(this)
    } else {
        "%,.2f".format(this)
    }
}
