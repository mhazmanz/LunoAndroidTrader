package com.hazman.lunoandroidtrader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.Home
import androidx.compose.material3.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hazman.lunoandroidtrader.ui.dashboard.DashboardScreen
import com.hazman.lunoandroidtrader.ui.settings.SettingsScreen
import com.hazman.lunoandroidtrader.ui.theme.LunoAndroidTraderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LunoAndroidTraderTheme {
                LunoTraderApp()
            }
        }
    }
}

private enum class BottomTab(val label: String) {
    DASHBOARD("Dashboard"),
    SETTINGS("Settings")
}

@Composable
private fun LunoTraderApp() {
    var selectedTab by remember { mutableStateOf(BottomTab.DASHBOARD) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == BottomTab.DASHBOARD,
                    onClick = { selectedTab = BottomTab.DASHBOARD },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = selectedTab == BottomTab.SETTINGS,
                    onClick = { selectedTab = BottomTab.SETTINGS },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            BottomTab.DASHBOARD -> DashboardScreen(
                modifier = Modifier.padding(innerPadding)
            )
            BottomTab.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
