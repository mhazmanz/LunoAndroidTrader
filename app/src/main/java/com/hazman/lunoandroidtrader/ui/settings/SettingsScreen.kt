package com.hazman.lunoandroidtrader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hazman.lunoandroidtrader.data.local.AppStorage
import com.hazman.lunoandroidtrader.data.luno.LunoApiClient
import com.hazman.lunoandroidtrader.data.telegram.TelegramClient
import kotlinx.coroutines.launch

/**
 * SettingsScreen:
 *
 * - API & Telegram:
 *   - Luno read-only API key & secret
 *   - Telegram bot token & chat ID
 *   - Live trading toggle (for future)
 *
 * - Risk settings:
 *   - Risk per trade (% of equity)
 *   - Daily loss limit (% of starting-day equity)
 *   - Max trades per day
 *   - Cooldown after loss (minutes)
 *
 * - Actions:
 *   - Save Settings
 *   - Test Luno Connection
 *   - Send Telegram Test
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val storage = remember { AppStorage(context) }
    val coroutineScope = rememberCoroutineScope()

    // Scroll state so content is scrollable on smaller screens
    val scrollState = rememberScrollState()

    // API & Telegram state
    var lunoKey by remember { mutableStateOf("") }
    var lunoSecret by remember { mutableStateOf("") }
    var telegramToken by remember { mutableStateOf("") }
    var telegramChatId by remember { mutableStateOf("") }
    var liveTradingEnabled by remember { mutableStateOf(false) }

    // Risk settings state (kept as text for editing)
    var riskPerTradePercentText by remember { mutableStateOf("1.0") }
    var dailyLossLimitPercentText by remember { mutableStateOf("5.0") }
    var maxTradesPerDayText by remember { mutableStateOf("10") }
    var cooldownMinutesText by remember { mutableStateOf("30") }

    var statusMessage by remember { mutableStateOf("") }

    // Load existing values from storage on first composition
    LaunchedEffect(Unit) {
        lunoKey = storage.getLunoReadOnlyKey() ?: ""
        lunoSecret = storage.getLunoReadOnlySecret() ?: ""
        telegramToken = storage.getTelegramBotToken() ?: ""
        telegramChatId = storage.getTelegramChatId() ?: ""
        liveTradingEnabled = storage.isLiveTradingEnabled()

        riskPerTradePercentText = storage.getRiskPerTradePercent().toString()
        dailyLossLimitPercentText = storage.getDailyLossLimitPercent().toString()
        maxTradesPerDayText = storage.getMaxTradesPerDay().toString()
        cooldownMinutesText = storage.getCooldownMinutesAfterLoss().toString()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState) // <--- ENABLE VERTICAL SCROLL
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {

        // -------------
        // API SETTINGS
        // -------------

        Text(
            text = "API & Telegram Settings",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = lunoKey,
            onValueChange = { lunoKey = it },
            label = { Text("Luno Read-Only API Key") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = lunoSecret,
            onValueChange = { lunoSecret = it },
            label = { Text("Luno Read-Only API Secret") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = telegramToken,
            onValueChange = { telegramToken = it },
            label = { Text("Telegram Bot Token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = telegramChatId,
            onValueChange = { telegramChatId = it },
            label = { Text("Telegram Chat ID") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Live trading toggle (future)
        Column {
            Text(
                text = "Live Trading Mode (future)",
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = liveTradingEnabled,
                onCheckedChange = { checked ->
                    liveTradingEnabled = checked
                    storage.setLiveTradingEnabled(checked)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // -------------
        // RISK SETTINGS
        // -------------

        Text(
            text = "Risk Settings",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "These values control how aggressively the bot will trade. " +
                    "They do not guarantee profits, but they help define limits.",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = riskPerTradePercentText,
            onValueChange = { riskPerTradePercentText = it },
            label = { Text("Risk Per Trade (%)") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = dailyLossLimitPercentText,
            onValueChange = { dailyLossLimitPercentText = it },
            label = { Text("Daily Loss Limit (%)") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = maxTradesPerDayText,
            onValueChange = { maxTradesPerDayText = it },
            label = { Text("Max Trades Per Day") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // --- COOLDOWN FIELD (this is the 4th risk setting) ---
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = cooldownMinutesText,
            onValueChange = { cooldownMinutesText = it },
            label = { Text("Cooldown After Loss (minutes)") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // -------------
        // SAVE SETTINGS
        // -------------

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                // Save API settings
                storage.setLunoReadOnlyKey(lunoKey.ifBlank { null })
                storage.setLunoReadOnlySecret(lunoSecret.ifBlank { null })
                storage.setTelegramBotToken(telegramToken.ifBlank { null })
                storage.setTelegramChatId(telegramChatId.ifBlank { null })

                // Parse and save risk settings safely
                val riskPct = riskPerTradePercentText.toDoubleOrNull() ?: storage.getRiskPerTradePercent()
                val dailyLossPct = dailyLossLimitPercentText.toDoubleOrNull() ?: storage.getDailyLossLimitPercent()
                val maxTrades = maxTradesPerDayText.toIntOrNull() ?: storage.getMaxTradesPerDay()
                val cooldownMin = cooldownMinutesText.toIntOrNull() ?: storage.getCooldownMinutesAfterLoss()

                storage.setRiskPerTradePercent(riskPct)
                storage.setDailyLossLimitPercent(dailyLossPct)
                storage.setMaxTradesPerDay(maxTrades)
                storage.setCooldownMinutesAfterLoss(cooldownMin)

                // Normalize text fields back to stored values
                riskPerTradePercentText = riskPct.toString()
                dailyLossLimitPercentText = dailyLossPct.toString()
                maxTradesPerDayText = maxTrades.toString()
                cooldownMinutesText = cooldownMin.toString()

                statusMessage = "Settings saved."
            }
        ) {
            Text(text = "Save Settings")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // -------------
        // TEST BUTTONS
        // -------------

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                statusMessage = "Testing Luno connection..."
                coroutineScope.launch {
                    val client = LunoApiClient(storage)
                    val result = client.testGetBalances()
                    statusMessage = result.fold(
                        onSuccess = { balances ->
                            if (balances.isEmpty()) {
                                "Luno test OK – no balances returned (empty account or no assets)."
                            } else {
                                val summary = balances.joinToString { "${it.asset}:${it.balance}" }
                                "Luno test OK – balances: $summary"
                            }
                        },
                        onFailure = { e ->
                            "Luno test FAILED: ${e.message ?: e::class.java.simpleName}"
                        }
                    )
                }
            }
        ) {
            Text(text = "Test Luno Connection")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                statusMessage = "Sending Telegram test..."
                coroutineScope.launch {
                    val client = TelegramClient(storage)
                    val result = client.sendTestMessage()
                    statusMessage = result.fold(
                        onSuccess = {
                            "Telegram test OK – check your Telegram chat."
                        },
                        onFailure = { e ->
                            "Telegram test FAILED: ${e.message ?: e::class.java.simpleName}"
                        }
                    )
                }
            }
        ) {
            Text(text = "Send Telegram Test")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (statusMessage.isNotBlank()) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
