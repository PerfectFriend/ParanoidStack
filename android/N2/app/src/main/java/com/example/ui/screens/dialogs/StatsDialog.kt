package com.example.ui.screens.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.MatchHistory
import com.example.ui.GameViewModel
import com.example.ui.screens.Language
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MatchHistoryRow(match: MatchHistory, lang: String) {
    val sdf = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())
    val dateStr = sdf.format(Date(match.date))
    val youWon = match.winner == "WHITE"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = if (youWon) "${Language.get("white", lang)} ${Language.get("winner", lang)}" else "${Language.get("black", lang)} ${Language.get("winner", lang)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (youWon) Color(0xFF43A047) else Color(0xFFE53935)
            )
            Text(
                text = "$dateStr • Time: ${match.gameDurationSeconds}s",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "${match.scorePlayer} : ${match.scoreOpponent}",
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Диалог статистики игрока: количество игр, побед, история матчей. */
@Composable
fun StatsDialog(
    viewModel: GameViewModel,
    onDismiss: () -> Unit
) {
    val matches by viewModel.matchHistory.collectAsState()
    val totalMatches = matches.size
    val botWins = matches.count { it.winner == "BLACK" }
    val humanWins = matches.count { it.winner == "WHITE" }
    val lang = viewModel.selectedLanguage

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = Language.get("stats_title", lang),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Language.get("stats_played", lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$totalMatches", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Language.get("stats_won", lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$humanWins", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF43A047))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Language.get("stats_lost", lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$botWins", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = Language.get("stats_history", lang),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(top = 4.dp)
                ) {
                    if (matches.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = Language.get("stats_empty", lang),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(matches) { match ->
                                MatchHistoryRow(match, lang)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { viewModel.clearStatsHistory() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(Language.get("stats_clear", lang), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(Language.get("close", lang), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
