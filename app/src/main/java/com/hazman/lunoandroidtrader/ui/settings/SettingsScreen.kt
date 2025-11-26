package com.hazman.lunoandroidtrader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
 * - Allows user to input:
 *   - Luno read-only API key & secret
 *   - Telegram bot token & chat ID
 *   - Live trading toggle (for future)
 * - Provides buttons to:
 *   - Test Luno connection (fetch balances)
 *   - Send Telegram test message
 *
 * All secrets are stored securely via AppStorage.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val storage = remember { AppStorage(context) }
    val coroutineScope = rememberCoroutineScope()

    var lunoKey by remember { mutableStateOf("") }
    var lunoSecret by remember { mutableStateOf("") }
    var telegramToken by remember { mutableStateOf("") }
    var telegramChatId by remember { mutableStateOf("") }
    var liveTradingEnabled by remember { mutableStateOf(false) }

    var statusMessage by remember { mutableStateOf("") }

    // Load existing values from storage on first composition
    LaunchedEffect(Unit) {
        lunoKey = storage.getLunoReadOnlyKey() ?: ""
        lunoSecret = storage.getLunoReadOnlySecret() ?: ""
        telegramToken = storage.getTelegramBotToken() ?: ""
        telegramChatId = storage.getTelegramChatId() ?: ""
        liveTradingEnabled = storage.isLiveTradingEnabled()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {

        Text(
            text = "API & Telegram Settings",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Luno read-only key
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

        // Telegram
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

        Spacer(modifier = Modifier.height(16.dp))

        // Save button
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                storage.setLunoReadOnlyKey(lunoKey.ifBlank { null })
                storage.setLunoReadOnlySecret(lunoSecret.ifBlank { null })
                storage.setTelegramBotToken(telegramToken.ifBlank { null })
                storage.setTelegramChatId(telegramChatId.ifBlank { null })
                statusMessage = "Settings saved."
            }
        ) {
            Text(text = "Save Settings")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Test Luno connection
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

        // Test Telegram
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
    }
}
