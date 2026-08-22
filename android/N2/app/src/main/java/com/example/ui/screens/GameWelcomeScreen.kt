package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.GameViewModel

@Composable
fun GameWelcomeScreen(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    val lang = viewModel.selectedLanguage
    val radioState = viewModel.radioManager
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            Spacer(modifier = Modifier.height(2.dp))
            
            // App Logo & Title Block
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFD3A373), MaterialTheme.colorScheme.primary)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .shadow(2.dp, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Dice logo",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Text(
                    text = Language.get("welcome_title", lang),
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.5.sp
                )
                
                Text(
                    text = Language.get("welcome_slogan", lang),
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Progress Indicators (Step row)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 1..4) {
                    val isActive = viewModel.welcomeStep == i
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }

            // Wizard Step card containers
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.2.dp, Color(0xFFD3A373).copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (viewModel.welcomeStep) {
                        1 -> {
                            // LANGUAGE SELECTOR
                            Text(
                                text = "SELECT LANGUAGE",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp
                            )
                            
                            val languages = listOf(
                                Triple("EN", "🇬🇧 English", "Voiceover & comments"),
                                Triple("ES", "🇪🇸 Español", "Comentarios de juego"),
                                Triple("DE", "🇩🇪 Deutsch", "Spielkommentare"),
                                Triple("FR", "🇫🇷 Français", "Commentaires de jeu"),
                                Triple("RU", "🇷🇺 Russian", "Voiceover & comments"),
                                Triple("TR", "🇹🇷 Türkçe", "Yorumlar")
                            )
                            
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val chunks = languages.chunked(2)
                                chunks.forEach { rowLangs ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        rowLangs.forEach { (code, name, subtitle) ->
                                            val isSelected = viewModel.selectedLanguage == code
                                            Card(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        viewModel.updateLanguage(code)
                                                        viewModel.welcomeStep = 2 // Auto-advance to Radio!
                                                    },
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isSelected) {
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                                                    }
                                                ),
                                                border = BorderStroke(
                                                    width = if (isSelected) 1.5.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.1f)
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = name,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = subtitle,
                                                            fontSize = 8.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.CheckCircle,
                                                            contentDescription = "Selected",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        2 -> {
                            // RADIO STATIONS SELECTION
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = Language.get("welcome_select_radio", lang),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "[ ${viewModel.selectedLanguage} ]",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Text(
                                text = Language.get("welcome_radio_subtitle", lang),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            
                            val filteredChannels = radioState.channels.filter { channel ->
                                val langLower = viewModel.selectedLanguage.lowercase()
                                channel.id.startsWith(langLower) || channel.id.startsWith("custom_")
                            }
                            
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Silent Play option
                                val isSilentSelected = !viewModel.welcomePlayRadio
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.welcomePlayRadio = false },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSilentSelected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        } else {
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                                        }
                                    ),
                                    border = BorderStroke(
                                        width = if (isSilentSelected) 2.dp else 1.dp,
                                        color = if (isSilentSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = Language.get("welcome_radio_disable", lang),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSilentSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSilentSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isSilentSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                
                                // Show filtered radio channels
                                if (filteredChannels.isEmpty()) {
                                    radioState.channels.take(3).forEach { channel ->
                                        val isSelected = viewModel.welcomePlayRadio && viewModel.welcomeSelectedChannelId == channel.id
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.welcomePlayRadio = true
                                                    viewModel.welcomeSelectedChannelId = channel.id
                                                },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                } else {
                                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                                                }
                                            ),
                                            border = BorderStroke(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = channel.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    filteredChannels.forEach { channel ->
                                        val isSelected = viewModel.welcomePlayRadio && viewModel.welcomeSelectedChannelId == channel.id
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.welcomePlayRadio = true
                                                    viewModel.welcomeSelectedChannelId = channel.id
                                                },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                } else {
                                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                                                }
                                            ),
                                            border = BorderStroke(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = channel.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.welcomeStep = 1 },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(Language.get("welcome_back", lang), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { viewModel.welcomeStep = 3 },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(Language.get("welcome_next", lang), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                        
                        3 -> {
                            // GAME MODE SELECTION
                            Text(
                                text = Language.get("welcome_select_mode", lang),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // MODE 0: BOT
                                val isBotChosen = viewModel.selectedWelcomeMode == 0
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectedWelcomeMode = 0
                                            viewModel.welcomeStep = 4 // Auto-advance to name input!
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isBotChosen) {
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                        } else {
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                                        }
                                    ),
                                    border = BorderStroke(
                                        width = if (isBotChosen) 2.dp else 1.dp,
                                        color = if (isBotChosen) MaterialTheme.colorScheme.secondary else Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Face,
                                            contentDescription = null,
                                            tint = if (isBotChosen) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text(
                                            text = Language.get("welcome_mode_bot", lang),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isBotChosen) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isBotChosen) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isBotChosen) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                
                                // MODE 1: LOCAL
                                val isLocalChosen = viewModel.selectedWelcomeMode == 1
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectedWelcomeMode = 1
                                            viewModel.welcomeStep = 4 // Auto-advance to names input!
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isLocalChosen) {
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                        } else {
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                                        }
                                    ),
                                    border = BorderStroke(
                                        width = if (isLocalChosen) 2.dp else 1.dp,
                                        color = if (isLocalChosen) MaterialTheme.colorScheme.secondary else Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Face,
                                            contentDescription = null,
                                            tint = if (isLocalChosen) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text(
                                            text = Language.get("welcome_mode_local", lang),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isLocalChosen) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isLocalChosen) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isLocalChosen) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                
                                // MODE 2: ONLINE
                                val isOnlineChosen = viewModel.selectedWelcomeMode == 2
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectedWelcomeMode = 2
                                            viewModel.welcomeStep = 4 // Auto-advance to name input!
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isOnlineChosen) {
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                        } else {
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                                        }
                                    ),
                                    border = BorderStroke(
                                        width = if (isOnlineChosen) 2.dp else 1.dp,
                                        color = if (isOnlineChosen) MaterialTheme.colorScheme.secondary else Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            tint = if (isOnlineChosen) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text(
                                            text = Language.get("welcome_mode_online", lang),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isOnlineChosen) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isOnlineChosen) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isOnlineChosen) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.welcomeStep = 2 },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(Language.get("welcome_back", lang), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { viewModel.welcomeStep = 4 },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(Language.get("welcome_next", lang), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                        
                        4 -> {
                            // PLAYER NAMES INPUT
                            Text(
                                text = Language.get("welcome_ask_name", lang),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (viewModel.selectedWelcomeMode == 1) {
                                    // Local P2P: 2 Names
                                    OutlinedTextField(
                                        value = viewModel.player1Name,
                                        onValueChange = { viewModel.player1Name = it },
                                        label = { Text(Language.get("welcome_p1_label", lang)) },
                                        placeholder = { Text(Language.get("welcome_p1_placeholder", lang)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    )
                                    
                                    OutlinedTextField(
                                        value = viewModel.player2Name,
                                        onValueChange = { viewModel.player2Name = it },
                                        label = { Text(Language.get("welcome_p2_label", lang)) },
                                        placeholder = { Text(Language.get("welcome_p2_placeholder", lang)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    )
                                } else {
                                    // Bot or Online: 1 Name
                                    OutlinedTextField(
                                        value = viewModel.userName,
                                        onValueChange = { viewModel.userName = it },
                                        label = { Text(Language.get("welcome_name_your", lang)) },
                                        placeholder = { Text(Language.get("welcome_placeholder_player", lang)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.welcomeStep = 3 },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(Language.get("welcome_back", lang), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        // Auto fill defaults if fields are left blank
                                        if (viewModel.userName.isBlank()) {
                                            viewModel.userName = Language.get("welcome_placeholder_player", lang)
                                        }
                                        if (viewModel.player1Name.isBlank()) {
                                            viewModel.player1Name = Language.get("welcome_p1_placeholder", lang)
                                        }
                                        if (viewModel.player2Name.isBlank()) {
                                            viewModel.player2Name = Language.get("welcome_p2_placeholder", lang)
                                        }
                                        viewModel.startGameWithSettings()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.weight(1.3f).height(44.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(Language.get("welcome_btn_finish", lang), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
