package com.example.ui.screens.game

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class GameStats(
    val totalGames: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val winRate: Double = 0.0,
    val avgGameDuration: Long = 0,
    val bestStreak: Int = 0,
    val currentStreak: Int = 0,
    val totalMoves: Int = 0,
    val totalDoubles: Int = 0,
    val eloRating: Int = 1000
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameStatsScreen(
    stats: GameStats = GameStats(),
    onResetStats: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Статистика игр") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                },
                actions = {
                    TextButton(onClick = onResetStats) { Text("Сброс") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Рейтинг", style = MaterialTheme.typography.titleMedium)
                    Text("${stats.eloRating}", fontSize = 48.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Всего игр: ${stats.totalGames}", fontWeight = FontWeight.Medium)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Побед: ${stats.wins}  |  Поражений: ${stats.losses}")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Процент побед: ${"%.1f".format(stats.winRate)}%")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Лучшая серия: ${stats.bestStreak}")
                    Text("Текущая серия: ${stats.currentStreak}")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Средняя длительность: ${stats.avgGameDuration / 60} мин")
                    Text("Всего ходов: ${stats.totalMoves}")
                    Text("Удвоений: ${stats.totalDoubles}")
                }
            }
        }
    }
}
