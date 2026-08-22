package com.paranoidx.demo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paranoidx.demo.game.RuleSet
import com.paranoidx.demo.network.GameP2PManager

/**
 * P2P matchmaking screen — create/display/accept invite links.
 * Uses PX SDK SmpProtocol for invite generation and connection.
 */
@Composable
fun P2PMatchmakingScreen(
    ruleSet: RuleSet,
    p2pManager: GameP2PManager,
    onGameStart: (GameP2PManager) -> Unit,
    onBack: () -> Unit
) {
    var inviteText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Generating identity…") }
    var isHost by remember { mutableStateOf(false) }
    var inviteInput by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    // Generate identity + invite on first composition
    LaunchedEffect(Unit) {
        p2pManager.createIdentity()
        status = "Identity ready.\nPaste opponent's invite link below,\nor create one to share:"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("P2P Matchmaking", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(ruleSet.displayName, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(16.dp))

        // Status
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                status,
                modifier = Modifier.padding(12.dp),
                fontSize = 13.sp
            )
        }

        // Create invite button
        Button(
            onClick = {
                val link = p2pManager.createInvite()
                if (link != null) {
                    inviteText = "simplex://${link.pubKey.joinToString("") { "%02x".format(it) }}@" +
                            "${link.serverAddress}/${link.queueId.joinToString("") { "%02x".format(it) }}"
                    isHost = true
                    status = "✅ Invite created! Share this link:"
                } else {
                    status = "❌ Failed to create identity"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create Invite Link")
        }

        // Show invite
        if (inviteText.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    inviteText,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
            Text(
                "Share this link with your opponent",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text("Or paste opponent's invite:", fontSize = 13.sp)

        OutlinedTextField(
            value = inviteInput,
            onValueChange = { inviteInput = it },
            modifier = Modifier.fillMaxWidth().height(80.dp),
            placeholder = { Text("Paste invite link…", fontSize = 12.sp) },
            textStyle = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    status = "Connecting…"
                    val parsed = parseInvite(inviteInput)
                    if (parsed != null) {
                        val ok = p2pManager.acceptInvite(parsed)
                        if (ok) {
                            status = "✅ Connected! Starting game…"
                            onGameStart(p2pManager)
                        } else {
                            status = "❌ Connection failed"
                        }
                    } else {
                        status = "❌ Invalid invite link format"
                    }
                }
            },
            enabled = inviteInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Connect")
        }

        Spacer(Modifier.weight(1f))
        TextButton(onClick = onBack) {
            Text("← Back to menu")
        }
    }
}

/** Simple invite parser — extracts pubKey, server, queueId from simplex:// link */
private fun parseInvite(text: String): com.paranoidx.sdk.security.InviteLink? {
    try {
        val cleaned = text.trim()
        if (!cleaned.startsWith("simplex://")) return null
        val rest = cleaned.removePrefix("simplex://")
        val atIdx = rest.indexOf('@')
        val slashIdx = rest.indexOf('/')
        if (atIdx < 0 || slashIdx < 0 || slashIdx <= atIdx) return null
        val pubKeyHex = rest.substring(0, atIdx)
        val server = rest.substring(atIdx + 1, slashIdx)
        val qidHex = rest.substring(slashIdx + 1).split('?').firstOrNull() ?: ""
        val pubKey = pubKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val queueId = qidHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return com.paranoidx.sdk.security.InviteLink(pubKey, server, queueId, "opponent")
    } catch (_: Exception) { return null }
}
