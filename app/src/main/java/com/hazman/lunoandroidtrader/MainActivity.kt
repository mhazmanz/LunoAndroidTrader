package com.hazman.lunoandroidtrader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hazman.lunoandroidtrader.ui.theme.LunoAndroidTraderTheme

/**
 * MainActivity is the entry point of the app.
 *
 * Right now it shows a simple professional-looking dashboard shell.
 * Later, this will become a multi-screen navigation host with:
 * - Dashboard
 * - Markets & Charts
 * - Positions & History
 * - Settings & Risk
 * - API & Telegram configuration
 * - Logs & Diagnostics
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the main Compose content for the entire app.
        // We wrap everything in the app theme for consistent colors and typography.
        setContent {
            LunoAndroidTraderTheme {
                // Surface provides a background that adapts to the theme.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScaffold()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold() {
    // Scaffold will later host the top app bar, bottom navigation, and screen content.
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Luno Trading Bot",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        }
    ) { innerPadding ->
        // For now we only show a placeholder "Dashboard" layout.
        DashboardPlaceholder(
            modifier = Modifier.padding(innerPadding)
        )
    }
}

/**
 * A simple, centered dashboard placeholder.
 * This will be replaced by a real dashboard with:
 * - Account value
 * - Balances
 * - Today P&L
 * - Risk summary
 * - Trading status (Paper / Live / Paused)
 */
@Composable
private fun DashboardPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Luno Android Trading Bot",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Status: PLANNING PHASE 0\n" +
                            "• Connectivity & UI skeleton only\n" +
                            "• No real trading actions yet\n" +
                            "• Paper & live trading will be added later",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Next steps:\n" +
                            "1. Implement Luno API connectivity\n" +
                            "2. Implement Telegram test notification\n" +
                            "3. Build dashboard with real balances",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
