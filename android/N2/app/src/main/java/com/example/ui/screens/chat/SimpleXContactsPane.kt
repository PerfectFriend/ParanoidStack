package com.example.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.GameViewModel

@Composable
fun SimpleXContactsPane(
    viewModel: GameViewModel,
    lang: String,
    onOpenChat: (String) -> Unit
) {
    var contactNameInput by remember { mutableStateOf("") }
    var contactHandleInput by remember { mutableStateOf("") }
    var contactIsAnonInput by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = if (lang == "RU") "👥 СПИСОК КОНТАКТОВ (SimpleX Address Book)" else "👥 CONTACT LIST (SimpleX Address Book)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // Add new contact compact form
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (lang == "RU") "🆕 Добавить новый контакт" else "🆕 Add New Contact",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = contactNameInput,
                        onValueChange = { contactNameInput = it },
                        label = { Text(if (lang == "RU") "Имя" else "Name", fontSize = 9.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 11.sp)
                    )

                    OutlinedTextField(
                        value = contactHandleInput,
                        onValueChange = { contactHandleInput = it },
                        label = { Text("SimpleX ID", fontSize = 9.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 11.sp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(
                            checked = contactIsAnonInput,
                            onCheckedChange = { contactIsAnonInput = it }
                        )
                        Text(
                            text = if (lang == "RU") "Анонимный" else "Anonymous",
                            fontSize = 10.sp,
                            color = Color.LightGray
                        )
                    }

                    Button(
                        onClick = {
                            if (contactNameInput.isNotBlank() && contactHandleInput.isNotBlank()) {
                                viewModel.addContact(contactNameInput, contactHandleInput, contactIsAnonInput)
                                contactNameInput = ""
                                contactHandleInput = ""
                                contactIsAnonInput = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (lang == "RU") "Добавить" else "Add", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Render existing contacts in simpleXContacts
        viewModel.simplexContacts.forEach { contact ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF13181C)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (contact.isOnline) Color(0xFF00E676).copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = contact.name.take(2).uppercase(),
                                    color = if (contact.isOnline) Color(0xFF00E676) else Color.LightGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = contact.name,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (contact.isAnonymous) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color.Red.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = if (lang == "RU") "АНОНИМ" else "ANON",
                                                color = Color.Red,
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "ID: ${contact.handle}",
                                    color = Color.Gray,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "⚡ ${contact.rating} XP",
                                color = Color(0xFFFFB300),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (contact.isOnline) Color(0xFF00E676) else Color.Gray)
                                )
                                Text(
                                    text = if (contact.isOnline) (if (lang == "RU") "В сети" else "Online") else (if (lang == "RU") "Не в сети" else "Offline"),
                                    color = if (contact.isOnline) Color(0xFF00E676) else Color.Gray,
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Write Chat Button
                        Button(
                            onClick = {
                                val exists = viewModel.simplexRooms.any { it.id == contact.handle }
                                if (!exists) {
                                    viewModel.simplexRooms.add(
                                        GameViewModel.SimpleXRoom(
                                            id = contact.handle,
                                            title = "👥 Chat: ${contact.name}",
                                            lastMessage = if (lang == "RU") "Соединение установлено" else "Connection established",
                                            simplexUrl = "smp://${contact.handle}"
                                        )
                                    )
                                }
                                onOpenChat(contact.handle)
                            },
                            modifier = Modifier.weight(1f).height(30.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161E26)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(15.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = Color.LightGray,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (lang == "RU") "Написать" else "Chat", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        // 2. Challenge to Game Button
                        Button(
                            onClick = {
                                if (contact.isOnline) {
                                    if (contact.isAnonymous) {
                                        viewModel.isWalkieTalkieMuted = true
                                    }
                                    viewModel.startChallengeGame(contact.name)
                                }
                            },
                            enabled = contact.isOnline,
                            modifier = Modifier.weight(1f).height(30.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFB300),
                                disabledContainerColor = Color.Gray.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(15.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (lang == "RU") "ВЫЗВАТЬ ⚔️" else "CHALLENGE ⚔️", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
