/**
 * Player information and avatar display composable.
 *
 * Renders a side-by-side [PlayerInfoPanel] with two [PlayerCard]s showing
 * player name, score, colour, and turn indicator. The active player's avatar
 * is highlighted with the primary colour.
 */
package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PlayerInfo(
    val name: String,
    val color: String,
    val score: Int,
    val isCurrentTurn: Boolean = false,
    val avatarLetter: String = "?"
)

@Composable
fun PlayerInfoPanel(
    player: PlayerInfo,
    opponent: PlayerInfo,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerCard(player = player, alignment = Alignment.Start)
        Text("VS", fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        PlayerCard(player = opponent, alignment = Alignment.End)
    }
}

@Composable
private fun PlayerCard(
    player: PlayerInfo,
    alignment: Alignment.Horizontal
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignment == Alignment.Start) Arrangement.Start else Arrangement.End
    ) {
        if (alignment == Alignment.Start) {
            Surface(
                modifier = Modifier.size(40.dp).clip(CircleShape),
                color = if (player.isCurrentTurn) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(player.avatarLetter, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(horizontalAlignment = if (alignment == Alignment.Start) Alignment.Start else Alignment.End) {
            Text(player.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("${player.score}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(player.color, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (alignment == Alignment.End) {
            Spacer(Modifier.width(8.dp))
            Surface(
                modifier = Modifier.size(40.dp).clip(CircleShape),
                color = if (player.isCurrentTurn) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(player.avatarLetter, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}