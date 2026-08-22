package com.example.ui.screens.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.protocols.NetworkState
import com.example.protocols.TransportRecommendation
import com.example.ui.screens.ProfileSwitcherScreen

data class DashboardItem(
    val title: String,
    val subtitle: String,
    val icon: String,
    val badge: Int = 0,
    val onClick: () -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    userName: String = "Пользователь",
    items: List<DashboardItem> = emptyList(),
    networkState: NetworkState? = null,
    transportRecommendation: TransportRecommendation? = null,
    bandwidthMonitor: com.example.data.BandwidthMonitor? = null,
    onOpenChat: (String) -> Unit = {},
    onOpenGame: () -> Unit = {},
    onSettings: () -> Unit = {}
) {
    var showProfileSwitcher by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("N2 Messenger") },
                actions = {
                    TextButton(onClick = { showProfileSwitcher = true }) { Text("Profiles") }
                    TextButton(onClick = onSettings) { Text("\u2699") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Привет, $userName",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${items.size} активных чатов",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            networkState?.let {
                NetworkStateIndicator(it, transportRecommendation)
                Spacer(Modifier.height(8.dp))
            }

            bandwidthMonitor?.let {
                com.example.ui.screens.dashboard.BandwidthCard(it)
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = onOpenGame,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Play Backgammon", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            items.forEach { item ->
                Card(
                    onClick = item.onClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.Medium)
                            Text(item.subtitle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp)
                        }
                        if (item.badge > 0) {
                            Badge { Text("${item.badge}") }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("\u203A", fontSize = 20.sp)
                    }
                }
            }
        }
    }

    if (showProfileSwitcher) {
        ProfileSwitcherScreen(
            onDismiss = { showProfileSwitcher = false },
            onProfileSwitched = { showProfileSwitcher = false }
        )
    }
}
