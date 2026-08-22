package com.example.ui.screens.game

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class GameOverData(
    val winner: String,
    val finalScorePlayer: Int,
    val finalScoreOpponent: Int,
    val isPlayerWinner: Boolean,
    val gameDuration: Long,
    val moveCount: Int,
    val winType: String = "Обычная"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameOverScreen(
    data: GameOverData,
    onPlayAgain: () -> Unit = {},
    onBackToMenu: () -> Unit = {},
    onShareResult: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Игра завершена") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (data.isPlayerWinner) "\uD83C\uDFC6" else "\uD83D\uDE22",
                fontSize = 72.sp
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (data.isPlayerWinner) "Победа!" else "Поражение",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = data.winType,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    StatRow("Победитель", data.winner)
                    StatRow("Счёт", "${data.finalScorePlayer} : ${data.finalScoreOpponent}")
                    StatRow("Ходов", "${data.moveCount}")
                    StatRow("Длительность", "${data.gameDuration / 60} мин ${data.gameDuration % 60} сек")
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth()) {
                Text("Играть ещё")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onShareResult, modifier = Modifier.fillMaxWidth()) {
                Text("Поделиться результатом")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBackToMenu) { Text("В меню") }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
