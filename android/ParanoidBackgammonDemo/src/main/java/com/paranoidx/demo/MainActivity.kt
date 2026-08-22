package com.paranoidx.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paranoidx.demo.game.RuleSet
import com.paranoidx.demo.network.GameP2PManager
import com.paranoidx.demo.ui.GameScreen
import com.paranoidx.demo.ui.P2PMatchmakingScreen

data class Screen(val id: String, val ruleSet: RuleSet? = null, val p2p: Boolean = false)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DemoApp()
                }
            }
        }
    }
}

@Composable
fun DemoApp() {
    var screenStack by remember { mutableStateOf(listOf(Screen("menu"))) }
    val p2pManager = remember { GameP2PManager() }
    val current = screenStack.last()

    when (current.id) {
        "menu" -> RuleSetSelectorScreen(
            onSelect = { rs -> screenStack = screenStack + Screen("mode", rs) }
        )
        "mode" -> ModeSelectorScreen(
            ruleSet = current.ruleSet!!,
            onAI = { screenStack = screenStack + Screen("game", current.ruleSet, p2p = false) },
            onP2P = { screenStack = screenStack + Screen("p2p", current.ruleSet, p2p = true) },
            onBack = { screenStack = screenStack.dropLast(1) }
        )
        "p2p" -> P2PMatchmakingScreen(
            ruleSet = current.ruleSet!!,
            p2pManager = p2pManager,
            onGameStart = {
                screenStack = screenStack + Screen("game", current.ruleSet, p2p = true)
            },
            onBack = { screenStack = screenStack.dropLast(1) }
        )
        "game" -> GameScreen(
            ruleSet = current.ruleSet!!,
            p2pManager = if (current.p2p) p2pManager else null,
            onBack = {
                screenStack = screenStack.dropLast(1)
                p2pManager.disconnect()
            }
        )
    }
}

@Composable
fun RuleSetSelectorScreen(onSelect: (RuleSet) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("PX Backgammon", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("ParanoidX Transport SDK Demo", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Text("Select Rule Set:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        RuleSet.entries.forEach { rs ->
            Button(
                onClick = { onSelect(rs) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(rs.displayName)
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            "Powered by PX SDK — SMP, XFTP, Double Ratchet, E2EE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ModeSelectorScreen(
    ruleSet: RuleSet,
    onAI: () -> Unit,
    onP2P: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Mode", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(ruleSet.displayName, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(32.dp))

        Button(onClick = onAI, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("🤖 Play vs AI", fontSize = MaterialTheme.typography.bodyLarge.fontSize)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onP2P, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("🌐 Play vs Player (P2P)", fontSize = MaterialTheme.typography.bodyLarge.fontSize)
        }

        Spacer(Modifier.weight(1f))
        TextButton(onClick = onBack) { Text("← Back") }
    }
}
