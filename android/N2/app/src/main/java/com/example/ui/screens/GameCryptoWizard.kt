package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Bip39Helper
import com.example.ui.GameViewModel
import com.example.ui.components.MatrixKey
import com.example.ui.components.MatrixStyleKeyboard

@Composable
fun GameCryptoWizard(viewModel: GameViewModel, onDismiss: () -> Unit) {
    val lang = viewModel.selectedLanguage
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(0) } // 0 = Option Picker, 1 = Config Wizard, 2 = Restore Wizard
    
    // Step 1 states (Wizard inputs)
    var setupHandle by remember { mutableStateOf(viewModel.simplexUserHandle) }
    var setupSmpAddress by remember { mutableStateOf(viewModel.smpOnionAddress) }
    var setupXftpAddress by remember { mutableStateOf(viewModel.xftpOnionAddress) }
    
    var showSeedPhraseBackupStep by remember { mutableStateOf(false) }
    var hasConfirmedMnemonicBackup by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    // Step 2 states (Restore inputs)
    var restoreSeedPhrase by remember { mutableStateOf("") }
    var restoreContainerBlock by remember { mutableStateOf("") }
    var restoreStatusError by remember { mutableStateOf("") }

    var activeSetupField by remember { mutableStateOf("handle") }
    var activeRestoreField by remember { mutableStateOf("seed") }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun dismissKeyboard() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0F12))
            .clickable(enabled = true) {} // block clickthrough
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon header
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFAB47BC).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFFAB47BC),
                    modifier = Modifier.size(32.dp)
                )
            }

            when (currentStep) {
                0 -> {
                    // SELECTION STEP
                    Text(
                        text = if (lang == "RU") "⚠️ СЕТЬ НЕ НАСТРОЕНА" else "⚠️ NETWORK UNCONFIGURED",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = if (lang == "RU") {
                            "Секретный криптоконтейнер SimpleX размонтирован или уничтожен. Безопасные каналы связи не настроены. Пожалуйста, восстановите параметры по seed-фразе или настройте новые параметры вручную."
                        } else {
                            "The secure SimpleX cryptocontainer is unmounted or completely destroyed. Secure channels cannot connect. Please restore settings from seed phrase or configure manually."
                        },
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            dismissKeyboard()
                            currentStep = 1
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (lang == "RU") "НАСТРОИТЬ ПАРАМЕТРЫ" else "CONFIGURE SETTINGS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color.Black
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            dismissKeyboard()
                            currentStep = 2
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (lang == "RU") "ВОССТАНОВИТЬ НАСТРОЙКИ" else "RESTORE SETTINGS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    TextButton(onClick = {
                        dismissKeyboard()
                        onDismiss()
                    }) {
                        Text(if (lang == "RU") "ВЫЙТИ В ИГРУ" else "BACK TO GAME", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                
                1 -> {
                    // CONFIG WIZARD
                    if (!showSeedPhraseBackupStep) {
                        Text(
                            text = if (lang == "RU") "⚙️ Ручная настройка сети" else "⚙️ Manual Network Config",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = if (lang == "RU") {
                                "Каждый параметр зашифрован в памяти. Задайте имя вашей учетной записи и Onion-адреса серверов для SimpleX связи:"
                            } else {
                                "Every parameter is encrypted in RAM. Set your handle/username and Onion relay addresses to build a new container:"
                            },
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 14.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Nickname input
                        OutlinedTextField(
                            value = setupHandle,
                            onValueChange = { setupHandle = it },
                            readOnly = viewModel.isNoGameModeEnabled,
                            singleLine = true,
                            maxLines = 1,
                            label = { Text(if (lang == "RU") "Никнейм (SimpleX handle)" else "Username (SimpleX handle)", fontSize = 12.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (viewModel.isNoGameModeEnabled) activeSetupField = "handle"
                                },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedBorderColor = if (viewModel.isNoGameModeEnabled && activeSetupField == "handle") Color(0xFF00FF41) else Color(0xFF00E676),
                                unfocusedBorderColor = if (viewModel.isNoGameModeEnabled && activeSetupField == "handle") Color(0xFF00FF41) else Color.White.copy(alpha = 0.12f)
                            )
                        )

                        // SMP onion input
                        OutlinedTextField(
                            value = setupSmpAddress,
                            onValueChange = { setupSmpAddress = it },
                            readOnly = viewModel.isNoGameModeEnabled,
                            singleLine = false,
                            maxLines = 6,
                            label = { Text("SMP Relay Server Address (Up to 6 lines)", fontSize = 12.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clickable {
                                    if (viewModel.isNoGameModeEnabled) activeSetupField = "smp"
                                },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedBorderColor = if (viewModel.isNoGameModeEnabled && activeSetupField == "smp") Color(0xFF00FF41) else Color(0xFF00E676),
                                unfocusedBorderColor = if (viewModel.isNoGameModeEnabled && activeSetupField == "smp") Color(0xFF00FF41) else Color.White.copy(alpha = 0.12f)
                            )
                        )

                        // SMP Quick-select Row
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (lang == "RU") "Быстрый выбор SMP (активные и проверенные):" else "Quick-select SMP (active & verified):",
                                color = Color.LightGray.copy(alpha = 0.6f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                viewModel.verifiedSmpServers.forEach { server ->
                                    val isSelected = setupSmpAddress == server
                                    val isOnion = server.contains(".onion")
                                    val displayName = if (isOnion) {
                                        val host = server.substringBefore(".onion").substringAfterLast("@")
                                        if (host.length > 8) "${host.take(8)}...onion" else "onion"
                                    } else {
                                        server.substringAfterLast("@").substringAfterLast("://")
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) Color(0xFF00E676).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f))
                                            .border(1.dp, if (isSelected) Color(0xFF00E676) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                            .clickable { setupSmpAddress = server }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isOnion) Icons.Default.Lock else Icons.Default.Share,
                                                contentDescription = null,
                                                tint = if (isSelected) Color(0xFF00E676) else if (isOnion) Color(0xFFAB47BC) else Color(0xFF29B6F6),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = displayName,
                                                color = if (isSelected) Color(0xFF00E676) else Color.White,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // XFTP onion input
                        OutlinedTextField(
                            value = setupXftpAddress,
                            onValueChange = { setupXftpAddress = it },
                            readOnly = viewModel.isNoGameModeEnabled,
                            singleLine = false,
                            maxLines = 6,
                            label = { Text("XFTP Storage Server Address (Up to 6 lines)", fontSize = 12.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clickable {
                                    if (viewModel.isNoGameModeEnabled) activeSetupField = "xftp"
                                },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedBorderColor = if (viewModel.isNoGameModeEnabled && activeSetupField == "xftp") Color(0xFF00FF41) else Color(0xFF00E676),
                                unfocusedBorderColor = if (viewModel.isNoGameModeEnabled && activeSetupField == "xftp") Color(0xFF00FF41) else Color.White.copy(alpha = 0.12f)
                            )
                        )

                        // XFTP Quick-select Row
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (lang == "RU") "Быстрый выбор XFTP (активные и проверенные):" else "Quick-select XFTP (active & verified):",
                                color = Color.LightGray.copy(alpha = 0.6f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                viewModel.verifiedXftpServers.forEach { server ->
                                    val isSelected = setupXftpAddress == server
                                    val isOnion = server.contains(".onion")
                                    val displayName = if (isOnion) {
                                        val host = server.substringBefore(".onion").substringAfterLast("@")
                                        if (host.length > 8) "${host.take(8)}...onion" else "onion"
                                    } else {
                                        server.substringAfterLast("@").substringAfterLast("://")
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) Color(0xFF00E676).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f))
                                            .border(1.dp, if (isSelected) Color(0xFF00E676) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                            .clickable { setupXftpAddress = server }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isOnion) Icons.Default.Lock else Icons.Default.Share,
                                                contentDescription = null,
                                                tint = if (isSelected) Color(0xFF00E676) else if (isOnion) Color(0xFFAB47BC) else Color(0xFF29B6F6),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = displayName,
                                                color = if (isSelected) Color(0xFF00E676) else Color.White,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                dismissKeyboard()
                                if (setupHandle.isNotBlank()) {
                                    showSeedPhraseBackupStep = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (lang == "RU") "ПРОДОЛЖИТЬ" else "CONTINUE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }

                        OutlinedButton(
                            onClick = {
                                dismissKeyboard()
                                currentStep = 0
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (lang == "RU") "НАЗАД" else "BACK", fontSize = 10.sp)
                        }
                    } else {
                        // SEED PHRASE BACKUP STEP
                        Text(
                            text = if (lang == "RU") "🔐 Безопасность: Сид-фраза BIP-39" else "🔐 Security: BIP-39 Seed Phrase",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = if (lang == "RU") {
                                "Ниже показана ваша уникальная мнемоническая сид-фраза BIP-39. С её помощью шифруется криптоконтейнер и обеспечивается полное восстановление вашего аккаунта. Обязательно запишите её в безопасном месте!"
                            } else {
                                "Below is your unique standard BIP-39 mnemonic phrase. It encrypts your container and guarantees full account recovery. Write these 12 words down in order!"
                            },
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 14.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Grid displaying 12 words
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val words = viewModel.currentSeedPhrase.split(" ")
                            words.chunked(2).forEachIndexed { rowIndex, rowWords ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowWords.forEachIndexed { colIndex, word ->
                                        val index = rowIndex * 2 + colIndex + 1
                                        Card(
                                            modifier = Modifier.weight(1f),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF16191E)),
                                            border = BorderStroke(1.dp, Color(0xFFAB47BC).copy(alpha = 0.3f)),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "%02d".format(index),
                                                    color = Color(0xFFAB47BC),
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(end = 6.dp)
                                                )
                                                Text(
                                                    text = word,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Copy Button
                        Button(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(viewModel.currentSeedPhrase))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAB47BC)),
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (lang == "RU") "СКОПИРОВАТЬ СИД-ФРАЗУ" else "COPY SEED PHRASE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Checkbox row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.03f))
                                .clickable { hasConfirmedMnemonicBackup = !hasConfirmedMnemonicBackup }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = hasConfirmedMnemonicBackup,
                                onCheckedChange = { hasConfirmedMnemonicBackup = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF00E676),
                                    uncheckedColor = Color.LightGray
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (lang == "RU") {
                                    "Я надежно сохранил сид-фразу и понимаю, что без нее восстановить доступ к своему криптоконтейнеру будет невозможно."
                                } else {
                                    "I have securely saved my seed phrase and understand that recovery is impossible without these 12 words."
                                },
                                color = if (hasConfirmedMnemonicBackup) Color.White else Color.Gray,
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                dismissKeyboard()
                                if (hasConfirmedMnemonicBackup && setupHandle.isNotBlank()) {
                                    viewModel.simplexUserHandle = setupHandle
                                    viewModel.updateSmpOnionAddress(setupSmpAddress)
                                    viewModel.updateXftpOnionAddress(setupXftpAddress)
                                    // Register newly entered custom addresses into the verified sets
                                    viewModel.addVerifiedSmpServer(setupSmpAddress)
                                    viewModel.addVerifiedXftpServer(setupXftpAddress)
                                    viewModel.activateCryptocontainerManually()
                                    viewModel.connectAndSyncAllNetworkComponents()
                                }
                            },
                            enabled = hasConfirmedMnemonicBackup,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E676),
                                disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (lang == "RU") "СОЗДАТЬ И ПОДКЛЮЧИТЬ" else "CREATE & MOUNT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (hasConfirmedMnemonicBackup) Color.Black else Color.DarkGray)
                        }

                        OutlinedButton(
                            onClick = {
                                dismissKeyboard()
                                showSeedPhraseBackupStep = false
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (lang == "RU") "НАЗАД К НАСТРОЙКАМ" else "BACK TO SETTINGS", fontSize = 10.sp)
                        }
                    }

                    if (viewModel.isNoGameModeEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        MatrixStyleKeyboard(
                            onKey = { key ->
                                val currentVal = when (activeSetupField) {
                                    "handle" -> setupHandle
                                    "smp" -> setupSmpAddress
                                    "xftp" -> setupXftpAddress
                                    else -> ""
                                }
                                val updatedVal = when (key) {
                                    is MatrixKey.CharKey -> {
                                        if (activeSetupField == "handle" && currentVal.length >= 18) currentVal
                                        else currentVal + key.text
                                    }
                                    MatrixKey.Space -> currentVal + " "
                                    MatrixKey.Backspace -> {
                                        if (currentVal.isNotEmpty()) currentVal.dropLast(1) else ""
                                    }
                                    else -> currentVal
                                }
                                when (activeSetupField) {
                                    "handle" -> setupHandle = updatedVal
                                    "smp" -> setupSmpAddress = updatedVal
                                    "xftp" -> setupXftpAddress = updatedVal
                                }
                            },
                            lang = if (viewModel.selectedChatLanguage == "RU") "RU" else "EN"
                        )
                    }
                }

                2 -> {
                    // RESTORE WIZARD
                    Text(
                        text = if (lang == "RU") "🔐 Восстановление доступа" else "🔐 Restore Access",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = if (lang == "RU") {
                            "Введите вашу резервную сид-фразу из 12 слов. Если у вас также есть полный Base64-код резервной копии, вы можете развернуть настройки напрямую:"
                        } else {
                            "Enter your 12-word seed mnemonic phrase. If you also possess the complete Base64 backup payload, you can paste it directly to construct state:"
                        },
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 14.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Seed phrase input
                    OutlinedTextField(
                        value = restoreSeedPhrase,
                        onValueChange = { restoreSeedPhrase = it },
                        readOnly = viewModel.isNoGameModeEnabled,
                        singleLine = false,
                        maxLines = 6,
                        placeholder = { Text("anchor beacon crypto ... (12 words)", color = Color.Gray, fontSize = 12.sp) },
                        label = { Text(if (lang == "RU") "Сид-фраза из 12 слов (каждое слово видно)" else "12-word Seed Phrase (fully visible)", fontSize = 12.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(115.dp)
                            .clickable {
                                if (viewModel.isNoGameModeEnabled) activeRestoreField = "seed"
                            },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedBorderColor = if (viewModel.isNoGameModeEnabled && activeRestoreField == "seed") Color(0xFF00FF41) else Color(0xFF00E676),
                            unfocusedBorderColor = if (viewModel.isNoGameModeEnabled && activeRestoreField == "seed") Color(0xFF00FF41) else Color.White.copy(alpha = 0.12f)
                        )
                    )

                    // Optional container block input
                    OutlinedTextField(
                        value = restoreContainerBlock,
                        onValueChange = { restoreContainerBlock = it },
                        readOnly = viewModel.isNoGameModeEnabled,
                        singleLine = false,
                        maxLines = 10,
                        placeholder = { Text("CRAZYCONTAINER-...", color = Color.Gray, fontSize = 12.sp) },
                        label = { Text(if (lang == "RU") "Код контейнера Base64 (Опционально)" else "Base64 Container Code (Optional)", fontSize = 12.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clickable {
                                if (viewModel.isNoGameModeEnabled) activeRestoreField = "container"
                            },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedBorderColor = if (viewModel.isNoGameModeEnabled && activeRestoreField == "container") Color(0xFF00FF41) else Color(0xFF00E676),
                            unfocusedBorderColor = if (viewModel.isNoGameModeEnabled && activeRestoreField == "container") Color(0xFF00FF41) else Color.White.copy(alpha = 0.12f)
                        )
                    )

                    if (restoreStatusError.isNotEmpty()) {
                        Text(
                            text = restoreStatusError,
                            color = Color.Red,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val seed = restoreSeedPhrase.trim()
                            if (!Bip39Helper.validateMnemonic(context, seed)) {
                                restoreStatusError = if (lang == "RU") {
                                    "Некорректная сид-фраза BIP-39! Проверьте правильность написания слов и их порядок (должно быть ровно 12 слов из официального словаря с верной контрольной суммой)."
                                } else {
                                    "Invalid BIP-39 seed phrase! Check spelling, order, and ensure it consists of exactly 12 standard words with correct checksum."
                                }
                            } else {
                                val success = viewModel.importCryptocontainerWithSeed(seed, restoreContainerBlock.trim())
                                if (success) {
                                    viewModel.connectAndSyncAllNetworkComponents()
                                } else {
                                    restoreStatusError = if (lang == "RU") "Не удалось расшифровать криптоконтейнер! Неверная сид-фраза или поврежденный Base64-контейнер." else "Failed to decrypt cryptocontainer! Incorrect seed phrase or corrupted Base64 container block."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (lang == "RU") "ПРИМЕНИТЬ И ВОССТАНОВИТЬ" else "IMPORT & RESTORE ACCESS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }

                    OutlinedButton(
                        onClick = { currentStep = 0 },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (lang == "RU") { "НАЗАД" } else { "BACK" }, fontSize = 10.sp)
                    }

                    if (viewModel.isNoGameModeEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        MatrixStyleKeyboard(
                            onKey = { key ->
                                val currentVal = when (activeRestoreField) {
                                    "seed" -> restoreSeedPhrase
                                    "container" -> restoreContainerBlock
                                    else -> ""
                                }
                                val updatedVal = when (key) {
                                    is MatrixKey.CharKey -> currentVal + key.text
                                    MatrixKey.Space -> currentVal + " "
                                    MatrixKey.Backspace -> {
                                        if (currentVal.isNotEmpty()) currentVal.dropLast(1) else ""
                                    }
                                    else -> currentVal
                                }
                                when (activeRestoreField) {
                                    "seed" -> restoreSeedPhrase = updatedVal
                                    "container" -> restoreContainerBlock = updatedVal
                                }
                            },
                            lang = if (viewModel.selectedChatLanguage == "RU") "RU" else "EN"
                        )
                    }
                }
            }
        }
    }
}
