package com.example.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.protocols.ConnectivityQuality
import com.example.protocols.NetworkState
import com.example.protocols.TransportRecommendation
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NetworkStateIndicator(networkState: NetworkState, recommendation: TransportRecommendation?) {
    val qualityColor = when (networkState.connectivityQuality) {
        ConnectivityQuality.EXCELLENT -> MaterialTheme.colorScheme.primary
        ConnectivityQuality.GOOD -> MaterialTheme.colorScheme.tertiary
        ConnectivityQuality.FAIR -> MaterialTheme.colorScheme.secondary
        ConnectivityQuality.POOR -> MaterialTheme.colorScheme.error
        ConnectivityQuality.UNKNOWN -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Network", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(qualityColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = networkState.connectivityQuality.name.lowercase().replaceFirstChar { it.uppercase() },
                        fontSize = 11.sp,
                        color = qualityColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NetworkStat("Status", if (networkState.isOnline) "Online" else "Offline")
                NetworkStat("DNS", if (networkState.hasDns) "OK" else "Fail")
                NetworkStat("WebSocket", if (networkState.hasWebSocket) "OK" else "N/A")
                NetworkStat("Latency", "${networkState.latencyMs}ms")
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                NetworkStat("Scans", "${networkState.scanCount}")
                NetworkStat("Firewall", networkState.detectedFirewall.uppercase().take(12))
                NetworkStat("Blocked", "${networkState.blockedDomains.size}")
            }

            if (networkState.lastScanTime > 0L) {
                Spacer(Modifier.height(6.dp))
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                Text(
                    text = "Last scan: ${sdf.format(Date(networkState.lastScanTime))}",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            recommendation?.let {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(Modifier.height(6.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            "Transport: ${it.transportId.uppercase()} (${it.confidence}%)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            it.reason,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Text(label, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
