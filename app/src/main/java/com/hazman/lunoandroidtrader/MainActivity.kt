package com.hazman.lunoandroidtrader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hazman.lunoandroidtrader.ui.dashboard.DashboardScreen
import com.hazman.lunoandroidtrader.ui.settings.SettingsScreen
import com.hazman.lunoandroidtrader.ui.theme.LunoAndroidTraderTheme

/**
 * MainActivity is the entry point of the app.
 *
 * It currently hosts two main tabs:
 * - Dashboard
 * - Settings (API & Telegram)
 *
 * Navigation is done via a simple top "tab row" for now.
 * Later we can replace this with a bottom nav bar or a full NavHost.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LunoAndroidTraderTheme {
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

private enum class MainTab {
    DASHBOARD,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold() {
    var currentTab by remember { mutableStateOf(MainTab.DASHBOARD) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentTab) {
                            MainTab.DASHBOARD -> "Luno Trading Bot – Dashboard"
                            MainTab.SETTINGS -> "Luno Trading Bot – Settings"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Simple text-based tab selector
            TabSelector(
                currentTab = currentTab,
                onTabSelected = { currentTab = it }
            )

            when (currentTab) {
                MainTab.DASHBOARD -> DashboardScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp)
                )

                MainTab.SETTINGS -> SettingsScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun TabSelector(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        TabItem(
            text = "Dashboard",
            isSelected = currentTab == MainTab.DASHBOARD,
            onClick = { onTabSelected(MainTab.DASHBOARD) }
        )
        TabItem(
            text = "Settings",
            isSelected = currentTab == MainTab.SETTINGS,
            onClick = { onTabSelected(MainTab.SETTINGS) }
        )
    }
}

@Composable
private fun TabItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground
    }

    val weight = if (isSelected) FontWeight.Bold else FontWeight.Normal

    Text(
        modifier = Modifier
            .clickable(onClick = onClick),
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontWeight = weight,
            textAlign = TextAlign.Center
        )
    )
}
