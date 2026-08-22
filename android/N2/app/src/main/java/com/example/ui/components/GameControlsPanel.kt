/**
 * Game control buttons and player-info composables.
 *
 * Provides:
 * - [GameControlsPanel] – Roll, Undo, Redo, Double, Resign buttons.
 * - [PlayerInfoPanel] – Score and colour display for both players.
 *
 * Accepts a [GameControlsState] data class to drive button enable/disable logic.
 */
package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class GameControlsState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val canDouble: Boolean = false,
    val canRoll: Boolean = true,
    val canResign: Boolean = true,
    val doublingCubeValue: Int = 1,
    val gameOver: Boolean = false
)

@Composable
fun GameControlsPanel(
    state: GameControlsState,
    onRoll: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onDouble: () -> Unit = {},
    onAcceptDouble: () -> Unit = {},
    onDeclineDouble: () -> Unit = {},
    onResign: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Управление", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (!state.gameOver) {
                    Button(onClick = onRoll, enabled = state.canRoll, modifier = Modifier.weight(1f).padding(end = 4.dp), contentPadding = PaddingValues(4.dp)) {
                        Text("Бросок", fontSize = 11.sp)
                    }
                }
                OutlinedButton(onClick = onUndo, enabled = state.canUndo, modifier = Modifier.weight(1f).padding(horizontal = 2.dp), contentPadding = PaddingValues(4.dp)) {
                    Text("Отм.", fontSize = 11.sp)
                }
                OutlinedButton(onClick = onRedo, enabled = state.canRedo, modifier = Modifier.weight(1f).padding(horizontal = 2.dp), contentPadding = PaddingValues(4.dp)) {
                    Text("Повт.", fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (state.canDouble) {
                    OutlinedButton(onClick = onDouble, modifier = Modifier.weight(1f).padding(end = 4.dp), contentPadding = PaddingValues(4.dp)) {
                        Text("x${state.doublingCubeValue}", fontSize = 11.sp)
                    }
                }
                OutlinedButton(onClick = onResign, enabled = state.canResign, modifier = Modifier.weight(1f).padding(horizontal = 2.dp), contentPadding = PaddingValues(4.dp)) {
                    Text("Сдаться", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun PlayerInfoPanel(
    playerName: String,
    opponentName: String,
    playerColor: String,
    opponentColor: String,
    playerScore: Int,
    opponentScore: Int,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(playerName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("$playerScore", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(playerColor, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("VS", fontWeight = FontWeight.Black, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(opponentName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("$opponentScore", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(opponentColor, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
