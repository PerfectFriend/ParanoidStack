package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.GameViewModel

private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
)

@Composable
fun GameSettingsDialog(
    viewModel: GameViewModel,
    onDismiss: () -> Unit,
    onNetworkClick: () -> Unit
) {
    val context = LocalContext.current
    val lang = viewModel.selectedLanguage

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            viewModel.radioManager.addLocalFolder(it)
        }
    }

    val filesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            viewModel.radioManager.addLocalFiles(uris)
        }
    }

    var vpnFileAlertMessage by remember { mutableStateOf("") }
    val vpnConfigLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val res = viewModel.vpnManager.importConfigFromFile(it)
            vpnFileAlertMessage = res
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = Language.get("settings_title", lang),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Box(modifier = Modifier.height(380.dp)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Section 1: Localization & Color Theme Pairings
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Language & Presentation",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Languages grid choices
                                    val languages = listOf(
                                        "EN" to "🇺🇸 EN",
                                        "ES" to "🇪🇸 ES",
                                        "DE" to "🇩🇪 DE",
                                        "FR" to "🇫🇷 FR",
                                        "RU" to "🇷🇺 RU",
                                        "TR" to "🇹🇷 TR"
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Interface Language", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            languages.take(3).forEach { (code, label) ->
                                                val isSelected = viewModel.selectedLanguage == code
                                                Button(
                                                    onClick = { viewModel.updateLanguage(code) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                    modifier = Modifier.weight(1f).height(32.dp)
                                                ) {
                                                    Text(label, fontSize = 10.sp, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            languages.drop(3).forEach { (code, label) ->
                                                val isSelected = viewModel.selectedLanguage == code
                                                Button(
                                                    onClick = { viewModel.updateLanguage(code) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                    modifier = Modifier.weight(1f).height(32.dp)
                                                ) {
                                                    Text(label, fontSize = 10.sp, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Theme choices
                                    val themes = listOf(
                                        "warm" to Color(0xFFD3A373),
                                        "cosmic" to Color(0xFF00FFCC),
                                        "emerald" to Color(0xFF2ECC71),
                                        "sapphire" to Color(0xFF3498DB),
                                        "vintage" to Color(0xFFD2B48C)
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Color Scheme", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            themes.forEach { (themeId, themeColor) ->
                                                val isSelected = viewModel.selectedTheme.lowercase() == themeId
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(CircleShape)
                                                        .background(themeColor)
                                                        .border(
                                                            if (isSelected) 3.dp else 1.dp,
                                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                                            CircleShape
                                                        )
                                                        .clickable { viewModel.updateTheme(themeId) }
                                                        .padding(2.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = if (themeId == "cosmic") Color.Black else Color.White,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Section 2: Local AI & Game Rules Options
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(Language.get("ai_opponent", lang), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(Language.get("ai_opponent_desc", lang), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(
                                            checked = viewModel.isBotOpponentEnabled,
                                            onCheckedChange = { viewModel.isBotOpponentEnabled = it }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(Language.get("auto_roll", lang), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(Language.get("auto_roll_desc", lang), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(
                                            checked = viewModel.isAutoRollEnabled,
                                            onCheckedChange = { viewModel.isAutoRollEnabled = it }
                                        )
                                    }
                                }
                            }
                        }

                        // Section 3: Web Radio & Local Music Player Suite
                        item {
                            var activePlayerTab by remember { mutableStateOf(0) }
                            var customName by remember { mutableStateOf("") }
                            var customUrl by remember { mutableStateOf("") }
                            val radioSettings = viewModel.radioManager

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    // Header and Tabs in one line
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (activePlayerTab == 0) "Web Radio" else if (activePlayerTab == 1) "Local Playlist" else "Telegram Bridge",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        
                                        Row(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                .padding(2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (activePlayerTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                                                    .clickable { activePlayerTab = 0 }
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text("Радио", fontSize = 9.sp, color = if (activePlayerTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (activePlayerTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent)
                                                    .clickable { activePlayerTab = 1 }
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text("Медиа", fontSize = 9.sp, color = if (activePlayerTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (activePlayerTab == 2) MaterialTheme.colorScheme.primary else Color.Transparent)
                                                    .clickable { activePlayerTab = 2 }
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text("TG Канал", fontSize = 9.sp, color = if (activePlayerTab == 2) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Compact Display HUD Panel (Now Playing)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (radioSettings.isPlaying) Icons.Default.PlayArrow else Icons.Default.Close,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = radioSettings.activeTrackTitle,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (radioSettings.isLoading) "Загрузка..." 
                                                       else if (radioSettings.isPlaying) "Воспроизведение" 
                                                       else "Остановлено",
                                                fontSize = 8.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        
                                        // Next/Prev controls only for Local tracks
                                        if (radioSettings.isPlayingLocal) {
                                            IconButton(
                                                onClick = {
                                                    if (radioSettings.isPlayingLocal) radioSettings.prevLocalTrack()
                                                    else radioSettings.prevTelegramTrack()
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                        
                                        IconButton(
                                            onClick = { radioSettings.togglePlay() },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (radioSettings.isPlaying || radioSettings.isLoading) Icons.Default.Close else Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        
                                        if (radioSettings.isPlayingLocal) {
                                            IconButton(
                                                onClick = {
                                                    if (radioSettings.isPlayingLocal) radioSettings.nextLocalTrack()
                                                    else radioSettings.nextTelegramTrack()
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }

                                    // Display error message if present
                                    radioSettings.errorMessage?.let { err ->
                                        Text(err, color = MaterialTheme.colorScheme.error, fontSize = 7.sp, modifier = Modifier.padding(top = 2.dp))
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // TAB Content Drawing
                                    if (activePlayerTab == 0) {
                                        // Web Radio Tab
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(75.dp)
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                .padding(2.dp)
                                        ) {
                                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                items(radioSettings.channels) { channel ->
                                                    val isSelected = !radioSettings.isPlayingLocal && radioSettings.currentChannel?.id == channel.id
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                                                            .clickable { radioSettings.selectChannel(channel) }
                                                            .padding(horizontal = 6.dp, vertical = 3.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                            Icon(
                                                                imageVector = if (isSelected) Icons.Default.Check else Icons.Default.PlayArrow,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(11.dp),
                                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(channel.name, fontSize = 9.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        }
                                                        if (channel.id.startsWith("custom_")) {
                                                            IconButton(onClick = { radioSettings.deleteCustomChannel(channel.id) }, modifier = Modifier.size(16.dp)) {
                                                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(11.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Tiny Stream Addition Panel
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = customName,
                                                onValueChange = { customName = it },
                                                placeholder = { Text("Название", fontSize = 8.sp) },
                                                singleLine = true,
                                                modifier = Modifier.weight(1.3f).height(48.dp),
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 8.sp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                                )
                                            )
                                            OutlinedTextField(
                                                value = customUrl,
                                                onValueChange = { customUrl = it },
                                                placeholder = { Text("Stream URL", fontSize = 8.sp) },
                                                singleLine = true,
                                                modifier = Modifier.weight(2.2f).height(48.dp),
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 8.sp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                                )
                                            )
                                            IconButton(
                                                onClick = {
                                                    if (customName.isNotBlank() && customUrl.isNotBlank()) {
                                                        radioSettings.addCustomChannel(customName, customUrl)
                                                        customName = ""
                                                        customUrl = ""
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                            }
                                        }
                                    } else if (activePlayerTab == 1) {
                                        // Local Music Tab
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Button(
                                                onClick = { folderLauncher.launch(null) },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                modifier = Modifier.weight(1f).height(24.dp),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text("📁 Папка", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Button(
                                                onClick = { filesLauncher.launch(arrayOf("audio/*")) },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                modifier = Modifier.weight(1f).height(24.dp),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text("📄 Файлы", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Local tracks checklist view
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(75.dp)
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                .padding(2.dp)
                                        ) {
                                            if (radioSettings.localTracks.isEmpty()) {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Text("Плейлист пуст.\nДобавьте треки.", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                                }
                                            } else {
                                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                    items(radioSettings.localTracks) { track ->
                                                        val isSelectedPlaying = radioSettings.isPlayingLocal && 
                                                            radioSettings.currentTrackIndex in radioSettings.localTracks.filter { it.isSelected }.indices && 
                                                            radioSettings.localTracks.filter { it.isSelected }[radioSettings.currentTrackIndex].id == track.id
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(if (isSelectedPlaying) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                                                                .clickable { 
                                                                    val filtered = radioSettings.localTracks.filter { it.isSelected }
                                                                    val matchIdx = filtered.indexOfFirst { it.id == track.id }
                                                                    if (matchIdx != -1) {
                                                                        radioSettings.playLocalTrack(matchIdx)
                                                                    } else {
                                                                        radioSettings.toggleTrackSelection(track.id)
                                                                        val newFiltered = radioSettings.localTracks.filter { it.isSelected }
                                                                        val newMatchIdx = newFiltered.indexOfFirst { it.id == track.id }
                                                                        if (newMatchIdx != -1) {
                                                                            radioSettings.playLocalTrack(newMatchIdx)
                                                                        }
                                                                    }
                                                                }
                                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Checkbox(
                                                                checked = track.isSelected,
                                                                onCheckedChange = { radioSettings.toggleTrackSelection(track.id) },
                                                                modifier = Modifier.scale(0.5f).size(16.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(2.dp))
                                                            Text(
                                                                text = track.name, 
                                                                fontSize = 9.sp, 
                                                                fontWeight = if (isSelectedPlaying) FontWeight.Bold else FontWeight.Normal, 
                                                                modifier = Modifier.weight(1f),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            IconButton(
                                                                onClick = { radioSettings.deleteTrack(track.id) },
                                                                modifier = Modifier.size(16.dp)
                                                            ) {
                                                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(10.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Local Control Modes (Shuffle, Repeat, select-all, clear-all)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                IconButton(
                                                    onClick = { radioSettings.isShuffle = !radioSettings.isShuffle },
                                                    modifier = Modifier.size(20.dp).background(if (radioSettings.isShuffle) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
                                                ) {
                                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(10.dp), tint = if (radioSettings.isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                IconButton(
                                                    onClick = { radioSettings.isRepeat = !radioSettings.isRepeat },
                                                    modifier = Modifier.size(20.dp).background(if (radioSettings.isRepeat) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, CircleShape)
                                                ) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(10.dp), tint = if (radioSettings.isRepeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                TextButton(
                                                    onClick = { radioSettings.selectAllTracks(true) },
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(18.dp)
                                                ) {
                                                    Text("Все", fontSize = 8.sp)
                                                }
                                                TextButton(
                                                    onClick = { radioSettings.selectAllTracks(false) },
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(18.dp)
                                                ) {
                                                    Text("Ни одного", fontSize = 8.sp)
                                                }
                                                TextButton(
                                                    onClick = { radioSettings.clearAllTracks() },
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(18.dp)
                                                ) {
                                                    Text("Очистить", fontSize = 8.sp, color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }

                                     else {
                                         // Telegram Tab (activePlayerTab == 2)
                                         Row(
                                             modifier = Modifier.fillMaxWidth(),
                                             horizontalArrangement = Arrangement.SpaceBetween,
                                             verticalAlignment = Alignment.CenterVertically
                                         ) {
                                             Button(
                                                 onClick = { radioSettings.fetchTelegramTracks() },
                                                 colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                                 modifier = Modifier.height(24.dp),
                                                 shape = RoundedCornerShape(4.dp),
                                                 contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                             ) {
                                                 Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(10.dp))
                                                 Spacer(modifier = Modifier.width(3.dp))
                                                 Text("Обновить", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                             }

                                             Button(
                                                 onClick = { radioSettings.playRandomTelegramTrack() },
                                                 colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                                                 modifier = Modifier.height(24.dp),
                                                 shape = RoundedCornerShape(4.dp),
                                                 contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                             ) {
                                                 Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(10.dp))
                                                 Spacer(modifier = Modifier.width(3.dp))
                                                 Text("Случайно", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                             }
                                         }

                                         Spacer(modifier = Modifier.height(4.dp))

                                         // Telegram tracks view
                                         Box(
                                             modifier = Modifier
                                                 .fillMaxWidth()
                                                 .height(75.dp)
                                                 .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                 .padding(2.dp)
                                         ) {
                                             if (radioSettings.isTelegramLoading) {
                                                 Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                     CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                 }
                                             } else if (radioSettings.telegramTracks.isEmpty()) {
                                                 Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                     Text("Канал пуст или не загружен.\nНажмите Обновить.", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                                 }
                                             } else {
                                                 LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                     itemsIndexed(radioSettings.telegramTracks) { idx, track ->
                                                         val isSelectedPlaying = radioSettings.isPlayingTelegram && radioSettings.currentTelegramTrackIndex == idx
                                                         Row(
                                                             modifier = Modifier
                                                                 .fillMaxWidth()
                                                                 .clip(RoundedCornerShape(4.dp))
                                                                 .background(if (isSelectedPlaying) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                                                                 .clickable { radioSettings.playTelegramTrack(idx) }
                                                                 .padding(horizontal = 4.dp, vertical = 2.dp),
                                                             verticalAlignment = Alignment.CenterVertically
                                                         ) {
                                                             Icon(
                                                                 imageVector = if (isSelectedPlaying) Icons.Default.PlayArrow else Icons.Default.Share,
                                                                 contentDescription = null,
                                                                 tint = if (isSelectedPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                                 modifier = Modifier.size(10.dp)
                                                             )
                                                             Spacer(modifier = Modifier.width(4.dp))
                                                             Column(modifier = Modifier.weight(1f)) {
                                                                 Text(
                                                                     text = track.title, 
                                                                     fontSize = 9.sp, 
                                                                     fontWeight = if (isSelectedPlaying) FontWeight.Bold else FontWeight.Normal, 
                                                                     maxLines = 1,
                                                                     overflow = TextOverflow.Ellipsis
                                                                 )
                                                                 Text(
                                                                     text = track.artist, 
                                                                     fontSize = 7.sp, 
                                                                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                     maxLines = 1,
                                                                     overflow = TextOverflow.Ellipsis
                                                                 )
                                                             }
                                                         }
                                                     }
                                                 }
                                             }
                                         }
                                     }
                                }
                            }
                        }

                        // Section 4: Network (P2P SimpleX Chat, VPN & Tor Services)
                        if (viewModel.hasSelectedArmageddonOnce) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onNetworkClick() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (lang == "RU") "Сетевые настройки" else "Network Settings",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = if (lang == "RU") "Настройки Tor-маршрутизации, Onion-серверов и VPN Foxray." else "Configure Tor SOCKS5, Onion servers, and Foxray VPN tunnels.",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 12.sp
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    // Stop connections and exit App
                    viewModel.radioManager.stop()
                    viewModel.vpnManager.stopVpn()
                    viewModel.setTorEnabledState(false)
                    (context as? android.app.Activity)?.finishAffinity()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(0.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
            ) {
                Text(
                    text = if (lang == "RU") "ВЫХОД & Очистка" else "EXIT & CLEAR",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1.3f)
            ) {
                Text(Language.get("done", lang))
            }
        }
            }
        }
    }
}
