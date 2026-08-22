@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.protocols.*
@Composable
fun ProtocolSettingsScreen(
    registry: ProtocolRegistry?,
    autoDetector: NetworkAutoDetector?,
    onBack: () -> Unit,
    onStartNode: () -> Unit,
    onStopNode: () -> Unit,
    isRunning: Boolean,
    phase: String,
    onConfigure: (String) -> Unit
) {
    val protocols = if (registry != null) registry.registryFlow.collectAsState().value else emptyList()
    val netState = if (autoDetector != null) autoDetector.networkState.collectAsState().value else NetworkState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Transport", "Messaging", "Storage", "Mesh", "Diagnostics")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Protocol Stack", fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("< Back") } },
                actions = {
                    if (isRunning) {
                        Text("RUNNING", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp))
                        TextButton(onClick = onStopNode) { Text("STOP") }
                    } else {
                        TextButton(onClick = onStartNode) { Text("START") }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(8.dp)) {
            // Phase indicator
            if (phase != "IDLE" && phase != "RUNNING") {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
                Text("Phase: $phase", fontSize = 11.sp, color = Color.Gray)
            }

            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, label ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i }) { Text(label, fontSize = 13.sp) }
                }
            }

            when (selectedTab) {
                0 -> TransportTab(protocols.filter { it.info.category == ProtocolCategory.TRANSPORT }, onConfigure)
                1 -> MessagingTab(protocols.filter { it.info.category == ProtocolCategory.MESSAGING }, onConfigure)
                2 -> StorageTab(protocols.filter { it.info.category == ProtocolCategory.STORAGE }, onConfigure)
                3 -> MeshTab(protocols.filter { it.info.category == ProtocolCategory.MESH }, onConfigure)
                4 -> DiagnosticsTab(netState, autoDetector)
            }
        }
    }
}

@Composable
private fun ProtocolCard(instance: ProtocolInstance, onConfigure: (String) -> Unit) {
    val statusColor = when (instance.status) {
        ProtocolStatus.RUNNING -> Color(0xFF4CAF50)
        ProtocolStatus.CONFIGURED -> Color(0xFF2196F3)
        ProtocolStatus.ERROR -> Color(0xFFF44336)
        ProtocolStatus.BLOCKED -> Color(0xFFFF9800)
        else -> Color.Gray
    }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { onConfigure(instance.info.id) }) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(instance.info.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(instance.info.description, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(instance.status.name, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                if (instance.info.version.isNotBlank()) {
                    Text("v${instance.info.version}", fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun TransportTab(protocols: List<ProtocolInstance>, onConfigure: (String) -> Unit) {
    LazyColumn {
        item {
            Text("Transport Protocols", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 4.dp))
            Text("Tor, V2Ray, I2P, WireGuard, Shadowsocks, Yggdrasil, cjdns", fontSize = 11.sp, color = Color.Gray)
        }
        items(protocols) { ProtocolCard(it, onConfigure) }
    }
}

@Composable
private fun MessagingTab(protocols: List<ProtocolInstance>, onConfigure: (String) -> Unit) {
    LazyColumn {
        item {
            Text("Messaging Protocols", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 4.dp))
            Text("SimpleX, Matrix, Tox, Session, Briar, Nostr", fontSize = 11.sp, color = Color.Gray)
        }
        items(protocols) { ProtocolCard(it, onConfigure) }
    }
}

@Composable
private fun StorageTab(protocols: List<ProtocolInstance>, onConfigure: (String) -> Unit) {
    LazyColumn {
        item {
            Text("Storage Protocols", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 4.dp))
            Text("IPFS, BitTorrent (DHT), Hypercore (Dat), Archive Cloud", fontSize = 11.sp, color = Color.Gray)
        }
        items(protocols) { ProtocolCard(it, onConfigure) }
    }
}

@Composable
private fun MeshTab(protocols: List<ProtocolInstance>, onConfigure: (String) -> Unit) {
    LazyColumn {
        item {
            Text("Mesh & Discovery", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 4.dp))
            Text("libp2p, Kademlia DHT", fontSize = 11.sp, color = Color.Gray)
        }
        items(protocols) { ProtocolCard(it, onConfigure) }
    }
}

@Composable
private fun DiagnosticsTab(state: NetworkState, detector: NetworkAutoDetector?) {
    val strategy = detector?.selectedStrategy?.collectAsState()?.value
    val transport = detector?.optimalTransport?.collectAsState()?.value

    LazyColumn {
        item {
            Text("Network Diagnostics", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row { Text("Online: ", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text(if (state.isOnline) "YES" else "NO", color = if (state.isOnline) Color(0xFF4CAF50) else Color.Red, fontSize = 13.sp) }
                    Row { Text("Public IP: ", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text(state.publicIp, fontSize = 13.sp) }
                    Row { Text("DNS: ", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text(if (state.hasDns) "OK" else "FAIL", fontSize = 13.sp) }
                    Row { Text("Firewall: ", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text(state.detectedFirewall, fontSize = 13.sp) }
                    Row { Text("Latency: ", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text("${state.latencyMs}ms", fontSize = 13.sp) }
                    Row { Text("MTU: ", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text("${state.mtu}", fontSize = 13.sp) }
                    Row { Text("Blocked domains: ", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text("${state.blockedDomains.size}", fontSize = 13.sp) }
                }
            }
            Spacer(Modifier.height(4.dp))

            strategy?.let { s ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Bypass Strategy: ${s.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Stack: ${s.protocolStack.joinToString(" → ")}", fontSize = 12.sp)
                        Text(s.description, fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }

            transport?.let { t ->
                Spacer(Modifier.height(4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Optimal Transport: ${t.transportName}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Score: ${t.score}/100, Latency: ${t.latencyMs}ms", fontSize = 12.sp)
                    }
                }
            }

            if (state.blockedDomains.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("⚠ Blocked Domains Detected:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        state.blockedDomains.forEach { Text("  • $it", fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}

@Composable
fun ProtocolConfigDialog(
    instance: ProtocolInstance?,
    onDismiss: () -> Unit,
    onSave: (String, Map<String, String>) -> Unit
) {
    if (instance == null) return
    var configValues by remember(instance.info.id) {
        mutableStateOf(instance.config.toMap().toMutableMap())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure: ${instance.info.name}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(instance.info.description, fontSize = 12.sp, color = Color.Gray)

                instance.info.configFields.forEach { field ->
                    Spacer(Modifier.height(8.dp))
                    Text(field.label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    if (field.hint.isNotBlank()) Text(field.hint, fontSize = 10.sp, color = Color.Gray)

                    val currentValue = configValues.getOrDefault(field.key, field.defaultValue)
                    when (field.type) {
                        FieldType.BOOLEAN -> {
                            var checked by remember { mutableStateOf(currentValue == "true") }
                            Switch(checked = checked, onCheckedChange = { checked = it; configValues[field.key] = it.toString() })
                        }
                        FieldType.SELECT -> {
                            var expanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                                OutlinedTextField(
                                    value = currentValue, onValueChange = {},
                                    readOnly = true, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
                                )
                                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    field.options.forEach { opt ->
                                        DropdownMenuItem(text = { Text(opt) }, onClick = {
                                            configValues[field.key] = opt; expanded = false
                                        })
                                    }
                                }
                            }
                        }
                        FieldType.PASSWORD -> {
                            OutlinedTextField(value = currentValue, onValueChange = { configValues[field.key] = it },
                                singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        FieldType.MULTILINE -> {
                            OutlinedTextField(value = currentValue, onValueChange = { configValues[field.key] = it },
                                minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
                        }
                        else -> {
                            OutlinedTextField(value = currentValue, onValueChange = { configValues[field.key] = it },
                                singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(instance.info.id, configValues); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
