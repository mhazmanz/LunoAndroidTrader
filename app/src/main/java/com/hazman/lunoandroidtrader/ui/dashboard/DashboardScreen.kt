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
import com.hazman.lunoandroidtrader.data.notifications.AppNotificationDispatcher
import com.hazman.lunoandroidtrader.data.telegram.TelegramClient
import kotlin.math.roundToInt

/**
 * DashboardScreen shows:
 * - Account overview (approx equity MYR, free MYR, balances)
 * - Risk configuration and computed max risk per trade
 * - Strategy status + last simulated signal
 * - Buttons to:
 *      - Refresh data
 *      - Run paper strategy once with a fake price
 *      - Run paper strategy once using live Luno ticker (XBTMYR)
 *
 * If Telegram is not configured, trade-open signals are delivered as
 * local Android notifications instead.
 */
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appStorage = remember { AppStorage(context) }
    val notificationDispatcher = remember {
        val telegramClient = TelegramClient(appStorage)
        AppNotificationDispatcher(
            context = context.applicationContext,
            storage = appStorage,
            telegramClient = telegramClient
        )
    }

    val viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModelFactory(
            appStorage = appStorage,
            notificationDispatcher = notificationDispatcher
        )
    )

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Luno Android Trader",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Action buttons
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Refresh")
                    }

                    Button(
                        onClick = {
                            // Simple fixed fake price for testing.
                            viewModel.runPaperStrategyOnce(fakeCurrentPrice = 250_000.0)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Run Fake Price")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            // Live ticker for XBTMYR. Later we can make this configurable.
                            viewModel.runPaperStrategyOnceWithLivePrice(pair = "XBTMYR")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Run Live XBTMYR")
                    }
                }
            }
        }

        if (uiState.isLoading) {
            Text(
                text = "Loading account & risk data…",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        uiState.errorMessage?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Error",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        AccountCard(uiState = uiState)
        RiskCard(uiState = uiState)
        StrategyCard(uiState = uiState)
    }
}

@Composable
private fun AccountCard(uiState: DashboardUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Account Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            val account = uiState.accountSnapshot
            if (account == null) {
                Text(
                    text = "No account data loaded yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
                return@Column
            }

            Text(
                text = "Approx. total equity (MYR): " +
                        account.totalEquityMyr.roundToTwoDecimals(),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Free MYR balance: " +
                        account.freeBalanceMyr.roundToTwoDecimals(),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Balances:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )

            if (account.balances.isEmpty()) {
                Text(
                    text = "No balances available.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                account.balances.forEach { bal ->
                    Text(
                        text = "${bal.asset}: ${bal.amount.roundToEightOrTwo()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun RiskCard(uiState: DashboardUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Risk Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            val risk = uiState.riskConfig
            if (risk == null) {
                Text(
                    text = "No risk config loaded yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
                return@Column
            }

            Text(
                text = "Risk per trade: ${risk.riskPerTradePercent.roundToTwoDecimals()}%",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Daily loss limit: ${risk.dailyLossLimitPercent.roundToTwoDecimals()}%",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Max trades per day: ${risk.maxTradesPerDay}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Cooldown after loss: ${risk.cooldownMinutesAfterLoss} minutes",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Live trading enabled: ${risk.liveTradingEnabled}",
                style = MaterialTheme.typography.bodyMedium
            )

            uiState.maxRiskPerTradeMyr?.let { maxRisk ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Max risk per trade (MYR): ${maxRisk.roundToTwoDecimals()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun StrategyCard(uiState: DashboardUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Strategy & Paper Trading",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Last decision: ${uiState.lastStrategyDecision ?: "-"}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = uiState.lastSimulatedSignal ?: "No simulated signal yet.",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Open simulated trades: ${uiState.openSimulatedTrades.size}",
                style = MaterialTheme.typography.bodyMedium
            )
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
