package com.example.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Диалог показа/сохранения криптоконтейнера (seed phrase + encrypted vault).
 *
 * Извлечён из [SimpleXFullScreenChat] как часть декомпозиции 6C.1.
 */
@Composable
fun SaveCryptoKeyDialog(
    show: Boolean,
    lang: String,
    seedPhraseText: String,
    containerText: String,
    onDismiss: () -> Unit,
    onCopySeed: (String) -> Unit,
    onCopyContainer: (String) -> Unit,
) {
    if (!show) return

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161E26)),
            border = BorderStroke(1.dp, Color(0xFFAB47BC).copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (lang == "RU") "🔐 КЛЮЧИ БЕЗОПАСНОСТИ СЕТИ" else "🔐 NETWORK VAULT DECRYPTION KEYS",
                    color = Color(0xFFAB47BC),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                Text(
                    text = if (lang == "RU") {
                        "Это параметры шифрования вашего секретного криптоконтейнера SimpleX. Применяйте их для восстановления чата и настроек."
                    } else {
                        "These are the encryption parameters of your secure SimpleX container. Use them to rebuild contacts and configurations."
                    },
                    color = Color.LightGray,
                    fontSize = 11.sp
                )

                // 1. Seed Mnemonic
                Text(
                    text = if (lang == "RU") "🔑 СИД-ФРАЗА (12 слов):" else "🔑 SEED PHRASE (12 words):",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 11.sp
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = seedPhraseText,
                        color = Color(0xFF00FF66),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Button(
                    onClick = {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(seedPhraseText))
                        onCopySeed(seedPhraseText)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F262B)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(if (lang == "RU") "СКОПИРОВАТЬ СИД-ФРАЗУ" else "COPY Mnemonic Phrase", fontSize = 10.sp, color = Color.White)
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // 2. Container body
                Text(
                    text = if (lang == "RU") "📦 ТЕЛО КОНТЕЙНЕРА (Full Base64 Encrypted Block):" else "📦 CONTAINER KEY (Full Base64 Encrypted Block):",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 11.sp
                )
                OutlinedTextField(
                    value = containerText,
                    onValueChange = {},
                    readOnly = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color.Gray),
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Gray,
                        unfocusedTextColor = Color.Gray,
                        focusedBorderColor = Color.White.copy(alpha = 0.1f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.08f)
                    )
                )
                Button(
                    onClick = {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(containerText))
                        onCopyContainer(containerText)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F262B)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(if (lang == "RU") "СКОПИРОВАТЬ ТЕЛО КОНТЕЙНЕРА" else "COPY FULL VAULT BLOCK", fontSize = 10.sp, color = Color.White)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (lang == "RU") "ЗАКРЫТЬ" else "CLOSE", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
