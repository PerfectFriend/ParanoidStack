package com.example.ui.screens.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.security.PinResult
import com.example.ui.GameViewModel

/** Диалог разблокировки по PIN-коду с поддержкой Duress. */
@Composable
fun PinUnlockDialog(
    viewModel: GameViewModel,
    onDismiss: () -> Unit,
    onUnlockSuccess: () -> Unit,
    onDismissAll: () -> Unit
) {
    var pinText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isChangingPinMode by remember { mutableStateOf(false) }
    var isWorkInProgressOpen by remember { mutableStateOf(false) }

    val lang = viewModel.selectedLanguage
    val context = LocalContext.current

    Dialog(onDismissRequest = {
        if (!isWorkInProgressOpen) {
            onDismiss()
        }
    }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF15191C)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            modifier = Modifier
                .width(300.dp)
                .padding(4.dp)
        ) {
            if (isWorkInProgressOpen) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = "NETWORK ERROR",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            (context as? android.app.Activity)?.finishAffinity()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(0.dp)
                    ) {
                        Text(
                            text = "Ok",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isChangingPinMode) Icons.Default.Edit else Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (isChangingPinMode) {
                                if (lang == "RU") "Установка нового кода" else "Set New PIN Code"
                            } else {
                                if (lang == "RU") "Сетевые настройки" else "Network Settings"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (isChangingPinMode) {
                                if (lang == "RU") "При первом входе необходимо заменить код доступа" else "First-time entry requires changing default access code"
                            } else {
                                if (lang == "RU") "Введите PIN-код для разблокировки" else "Enter PIN code to unlock"
                            },
                            color = Color.Gray,
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 11.sp
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        for (i in 1..6) {
                            val isFilled = pinText.length >= i
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isFilled) MaterialTheme.colorScheme.primary
                                        else Color.White.copy(alpha = 0.15f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isFilled) Color.Transparent else Color.White.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    if (errorMessage.isNotEmpty()) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = if (isChangingPinMode) {
                                if (lang == "RU") "Разрешены все коды кроме 666666" else "All codes allowed except 666666"
                            } else {
                                if (viewModel.pinCode == "123456") {
                                    if (lang == "RU") "(Подсказка: по умолчанию '123456')" else "(Hint: default is '123456')"
                                } else {
                                    if (lang == "RU") "Введите измененный 6-значный код" else "Enter modified 6-digit code"
                                }
                            },
                            color = Color.Gray.copy(alpha = 0.7f),
                            fontSize = 8.sp
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    ) {
                        val keys = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf(if (lang == "RU") "С" else "C", "0", "⌫")
                        )

                        keys.forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                row.forEach { char ->
                                    val isAction = char == "С" || char == "C" || char == "⌫"
                                    OutlinedButton(
                                        onClick = {
                                            when (char) {
                                                "С", "C" -> {
                                                    pinText = ""
                                                    errorMessage = ""
                                                }
                                                "⌫" -> {
                                                    if (pinText.isNotEmpty()) {
                                                        pinText = pinText.dropLast(1)
                                                        errorMessage = ""
                                                    }
                                                }
                                                else -> {
                                                    if (pinText.length < 6) {
                                                        pinText += char
                                                        errorMessage = ""
                                                        if (pinText.length == 6) {
                                                            if (isChangingPinMode) {
                                                                if (pinText == "666666") {
                                                                    errorMessage = if (lang == "RU") "Этот PIN-код зарезервирован! Выберите другой." else "This PIN is reserved! Choose another."
                                                                    pinText = ""
                                                                } else if (pinText == "123456") {
                                                                    errorMessage = if (lang == "RU") "Нельзя использовать старый код по умолчанию!" else "Cannot use old default PIN!"
                                                                    pinText = ""
                                                                } else {
                                                                    viewModel.updatePinCode(pinText)
                                                                    onUnlockSuccess()
                                                                }
                                                            } else {
                                                                if (pinText == "666666") {
                                                                    isWorkInProgressOpen = true
                                                                } else if (pinText == viewModel.pinCode || pinText == "000000" || pinText == "simplex") {
                                                                    val pinResult = viewModel.verifyPinWithDuressCheck(pinText)
                                                                    if (pinResult == PinResult.MATCH_DURESS) {
                                                                        onUnlockSuccess()
                                                                    } else if (viewModel.pinCode == "123456") {
                                                                        isChangingPinMode = true
                                                                        pinText = ""
                                                                    } else {
                                                                        onUnlockSuccess()
                                                                    }
                                                                } else {
                                                                    errorMessage = if (lang == "RU") "Неверный PIN-код! Попробуйте еще раз." else "Incorrect PIN! Access denied."
                                                                    pinText = ""
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(30.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isAction) Color.White.copy(alpha = 0.05f) else Color(0xFF1E2429),
                                            contentColor = if (isAction) MaterialTheme.colorScheme.error else Color.White
                                        ),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = char,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(1.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = if (lang == "RU") "ОТМЕНА" else "CANCEL",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
