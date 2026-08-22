/**
 * Score display and game-overview composables.
 *
 * - [GameScorePanel] – full scoreboard with player scores, match count, and action buttons.
 * - [ScoreIndicator] – compact borne-off count display per colour.
 * - [GameScoreSidebar] – sidebar summary showing current score and game-over status.
 */
package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Player
import com.example.ui.GameViewModel

data class GameScoreState(
    val playerName: String = "Player",
    val opponentName: String = "Opponent",
    val playerScore: Int = 0,
    val opponentScore: Int = 0,
    val matchesPlayed: Int = 0,
    val matchesWon: Int = 0,
    val gamePhase: String = "Playing"
)

@Composable
fun GameScorePanel(
    state: GameScoreState,
    onNewGame: () -> Unit = {},
    onUndo: () -> Unit = {},
    onResign: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Счёт", style = MaterialTheme.typography.titleMedium)
                Text(state.gamePhase, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.playerName, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text("${state.playerScore}", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
                Text(":", fontSize = 24.sp, modifier = Modifier.padding(horizontal = 16.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.opponentName, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text("${state.opponentScore}", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Матчей: ${state.matchesPlayed} • Побед: ${state.matchesWon}", fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                OutlinedButton(onClick = onUndo) { Text("Отменить") }
                OutlinedButton(onClick = onResign) { Text("Сдаться") }
                Button(onClick = onNewGame) { Text("Новая игра") }
            }
        }
    }
}

@Composable
fun ScoreIndicator(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape).border(0.5.dp, Color.Gray, CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = "$label: ", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = "$count/15", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun GameScoreSidebar(viewModel: GameViewModel) {
    val player = viewModel.activePlayer
    val visualColor = if (player == Player.WHITE) viewModel.humanPlayerColor else viewModel.botPlayerColor
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = if (visualColor == Player.WHITE) Color(0xFFEFEDE8) else Color(0xFF252528)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).border(width = 1.dp, color = if (visualColor == Player.WHITE) Color.DarkGray.copy(alpha = 0.3f) else Color(0xFFB08D57), shape = RoundedCornerShape(8.dp))) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = if (viewModel.gameStatus == com.example.model.GameStatus.GAME_OVER) "ИГРА ОКОНЧЕНА" else "СЧЁТ:", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = if (visualColor == Player.WHITE) Color.Black else Color(0xFFEBE0D0))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceAround) {
            val whitePlayer = if (viewModel.humanPlayerColor == Player.WHITE) Player.WHITE else Player.BLACK
            val blackPlayer = if (viewModel.humanPlayerColor == Player.BLACK) Player.WHITE else Player.BLACK
            ScoreIndicator(label = "Белые", count = viewModel.borneOffCount[whitePlayer] ?: 0, color = Color(0xFFDCDAD4))
            ScoreIndicator(label = "Чёрные", count = viewModel.borneOffCount[blackPlayer] ?: 0, color = Color(0xFF333335))
        }
    }
}
