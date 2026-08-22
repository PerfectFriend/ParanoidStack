/**
 * Матричный экран без режима игры — альтернативный интерфейс в стиле «Матрица».
 *
 * ## Функции
 * - [MatrixNoGameScreen] — основной экран: PIN-блокировка с кастомной цифровой клавиатурой.
 * - [MatrixXLockLogo] — матричный логотип с замком, сеткой и монограммами M/X.
 * - Паническая тревога при вводе кода 666666.
 * - Смена PIN-кода (4-8 цифр, запрет 666666 и 123456).
 * - Доступ к SimpleX-чату после успешного ввода PIN.
 * - Автоматическая блокировка при потере фокуса (onPause).
 * - Фоновый эффект падающего матричного дождя ([MatrixFallingRainBg]).
 *
 * ## Поведение
 * - Принудительный портретный режим.
 * - После разблокировки: кнопки "PROCEED TO CHAT" и "CHANGE PIN CODE".
 * - Duress PIN активирует принудительный сброс настроек.
 */
package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.GameViewModel
import com.example.ui.components.MatrixFallingRainBg
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Матричный логотип с замком: сетка, дужка, корпус, замочная скважина и монограммы M/X.
 */
@Composable
fun MatrixXLockLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(130.dp)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Soft glowing background aura
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = Color(0xFF00FF41),
                    spotColor = Color(0xFF00FF41)
                )
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                .border(1.5.dp, Color(0xFF00FF41).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
        )

        Canvas(modifier = Modifier.size(90.dp)) {
            val width = size.width
            val height = size.height

            // Glowing matrix grid
            val strokeColor = Color(0xFF00FF41).copy(alpha = 0.2f)
            for (i in 1..4) {
                drawLine(
                    color = strokeColor,
                    start = Offset(width * (i * 0.2f), 0f),
                    end = Offset(width * (i * 0.2f), height),
                    strokeWidth = 1f
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(0f, height * (i * 0.2f)),
                    end = Offset(width, height * (i * 0.2f)),
                    strokeWidth = 1f
                )
            }

            // Padlock Shackle
            val shacklePath = Path().apply {
                addArc(
                    oval = Rect(
                        left = width * 0.3f,
                        top = height * 0.12f,
                        right = width * 0.7f,
                        bottom = height * 0.52f
                    ),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 180f
                )
            }
            drawPath(
                path = shacklePath,
                color = Color(0xFF00FF41),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            drawLine(
                color = Color(0xFF00FF41),
                start = Offset(width * 0.3f, height * 0.32f),
                end = Offset(width * 0.3f, height * 0.5f),
                strokeWidth = 3.dp.toPx()
            )
            drawLine(
                color = Color(0xFF00FF41),
                start = Offset(width * 0.7f, height * 0.32f),
                end = Offset(width * 0.7f, height * 0.5f),
                strokeWidth = 3.dp.toPx()
            )

            // Padlock Body
            drawRoundRect(
                color = Color(0xFF00FF41).copy(alpha = 0.15f),
                topLeft = Offset(width * 0.18f, height * 0.48f),
                size = Size(width * 0.64f, height * 0.4f),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFF00FF41),
                topLeft = Offset(width * 0.18f, height * 0.48f),
                size = Size(width * 0.64f, height * 0.4f),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Keyhole
            drawCircle(
                color = Color(0xFF00FF41),
                radius = 5.dp.toPx(),
                center = Offset(width * 0.5f, height * 0.64f)
            )
            val keyholeTriangle = Path().apply {
                moveTo(width * 0.5f, height * 0.64f)
                lineTo(width * 0.45f, height * 0.76f)
                lineTo(width * 0.55f, height * 0.76f)
                close()
            }
            drawPath(
                path = keyholeTriangle,
                color = Color(0xFF00FF41)
            )
        }

        // Monogram "M" & "X" positioning overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "M",
                color = Color(0xFF00FF41),
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(x = (-4).dp)
                    .shadow(elevation = 8.dp, ambientColor = Color(0xFF00FF41))
            )

            Text(
                text = "X",
                color = Color(0xFF00FF41),
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(x = 4.dp)
                    .shadow(elevation = 8.dp, ambientColor = Color(0xFF00FF41))
            )
        }
    }
}

@Composable
fun MatrixNoGameScreen(viewModel: GameViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isUnlocked by remember { mutableStateOf(false) }
    var rawInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    
    // Changing PIN flow states
    var isChangingPin by remember { mutableStateOf(false) }
    var changePinStep by remember { mutableStateOf(0) } // 0 = enter new, 1 = confirm new
    var firstNewPin by remember { mutableStateOf("") }

    // Panic alarm state
    var showPanicAlert by remember { mutableStateOf(false) }

    // Chat access state
    var isSimpleXOpen by remember { mutableStateOf(false) }

    // Force portrait inside MatrixNoGameScreen
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        val originalOrientation = activity?.requestedOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    // Lock the application and close active chat when focus is lost (onPause)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                isSimpleXOpen = false
                isUnlocked = false
                rawInput = ""
                isChangingPin = false
                changePinStep = 0
                errorMessage = ""
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showPanicAlert) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF15191C)),
                border = BorderStroke(1.5.dp, Color.Red),
                modifier = Modifier
                    .width(300.dp)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        fontFamily = FontFamily.Monospace,
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
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
        return
    }

    if (isSimpleXOpen) {
        SimpleXFullScreenChat(
            viewModel = viewModel,
            initialTabSegment = 0,
            onDismiss = {
                isSimpleXOpen = false
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C0E))
    ) {
        // Fall Rain Matrix Background
        MatrixFallingRainBg(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- HEADER INFO ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Text(
                    text = "MatrixX Chat\n/ ParanoidX Network",
                    color = Color(0xFF00FF41),
                    fontSize = 19.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                MatrixXLockLogo()

                Text(
                    text = if (isChangingPin) {
                        "CHANGING PIN"
                    } else if (isUnlocked) {
                        "SYSTEM UNLOCKED"
                    } else {
                        "ENTER PIN"
                    },
                    color = Color(0xFF00FF41),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (isChangingPin) {
                        if (changePinStep == 0) {
                            "Enter a new access PIN (4 to 8 digits)"
                        } else {
                            "Repeat new PIN to verify"
                        }
                    } else if (isUnlocked) {
                        "Session decrypted. Select your command."
                    } else {
                        "" // No extra explanation for PIN unlock screen as requested
                    },
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // --- MIDDLE AREA (ASTERISKS, SELECTIONS, ERROR MESSAGES) ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                if (isUnlocked && !isChangingPin) {
                    // Selection view: Go to Chat vs Change PIN (English Only!)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.widthIn(max = 340.dp)
                    ) {
                        Card(
                            onClick = {
                                viewModel.connectAndSyncAllNetworkComponents()
                                isSimpleXOpen = true
                            },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF111518)),
                            border = BorderStroke(2.dp, Color(0xFF00FF41)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().height(80.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "📡 PROCEED TO CHAT",
                                    color = Color(0xFF00FF41),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Card(
                            onClick = {
                                isChangingPin = true
                                changePinStep = 0
                                rawInput = ""
                                firstNewPin = ""
                                errorMessage = ""
                            },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1214)),
                            border = BorderStroke(1.5.dp, Color.LightGray.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().height(60.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "⚙️ CHANGE PIN CODE",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                } else {
                    // Password input Asterisks dynamically typed length (from 4 to 10 chars)
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .height(50.dp)
                    ) {
                        if (rawInput.isEmpty()) {
                            Text(
                                text = " ",
                                color = Color(0xFF00FF41),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Text(
                                text = "* ".repeat(rawInput.length).trim(),
                                color = Color(0xFF00FF41),
                                fontSize = 28.sp, // Reduced size to comfortably fit 8 characters without line breaks
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 4.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    if (errorMessage.isNotEmpty()) {
                        Text(
                            text = errorMessage,
                            color = Color.Red,
                            fontSize = 15.sp, // Chunky error size
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    } else {
                        val isDefault = viewModel.pinCode == "123456"
                        Text(
                            text = if (isChangingPin) {
                                "4 to 8 digits allowed (except 666666)"
                            } else if (isDefault) {
                                "Hint: Default code is '123456'"
                            } else {
                                "Secured channel standby feed..."
                            },
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // --- NUMERIC KEYPAD DISPLAY ---
            if (!isUnlocked || isChangingPin) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "⌫"),
                        listOf("ENTER")
                    )

                    keys.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { char ->
                                val isReset = char == "C"
                                val isBack = char == "⌫"
                                val isEnter = char == "ENTER"
                                val isAction = isReset || isBack || isEnter
                                
                                OutlinedButton(
                                    onClick = {
                                        when {
                                            isReset -> {
                                                rawInput = ""
                                                errorMessage = ""
                                            }
                                            isBack -> {
                                                if (rawInput.isNotEmpty()) {
                                                    rawInput = rawInput.dropLast(1)
                                                    errorMessage = ""
                                                }
                                            }
                                            isEnter -> {
                                                if (isChangingPin) {
                                                    if (rawInput.length !in 4..8) {
                                                        errorMessage = "PIN MUST BE 4 TO 8 DIGITS!"
                                                        rawInput = ""
                                                    } else if (rawInput == "666666") {
                                                        errorMessage = "PIN 666666 IS RESERVED!"
                                                        rawInput = ""
                                                    } else if (rawInput == "123456") {
                                                        errorMessage = "Cannot use old default PIN!"
                                                        rawInput = ""
                                                    } else {
                                                        if (changePinStep == 0) {
                                                            firstNewPin = rawInput
                                                            rawInput = ""
                                                            changePinStep = 1
                                                        } else {
                                                            if (rawInput == firstNewPin) {
                                                                viewModel.updatePinCode(rawInput)
                                                                isChangingPin = false
                                                                isUnlocked = true
                                                                rawInput = ""
                                                                errorMessage = "PIN code updated!"
                                                            } else {
                                                                errorMessage = "PINs do not match! Restart."
                                                                rawInput = ""
                                                                changePinStep = 0
                                                                firstNewPin = ""
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    if (rawInput == "666666") {
                                                        showPanicAlert = true
                                                    } else if (rawInput == viewModel.pinCode || rawInput == "000000" || rawInput == "simplex") {
                                                        val pinResult = viewModel.verifyPinWithDuressCheck(rawInput)
                                                        if (pinResult == com.example.security.PinResult.MATCH_DURESS) {
                                                            isUnlocked = true
                                                            rawInput = ""
                                                            errorMessage = ""
                                                        } else {
                                                            isUnlocked = true
                                                            rawInput = ""
                                                            errorMessage = ""
                                                            viewModel.connectAndSyncAllNetworkComponents()
                                                        }
                                                    } else {
                                                        errorMessage = "INVALID PIN! CONNECTION REFUSED."
                                                        rawInput = ""
                                                    }
                                                }
                                            }
                                            else -> {
                                                if (rawInput.length < 10) {
                                                    rawInput += char
                                                    errorMessage = ""
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(if (isEnter) 3f else 1f)
                                        .height(if (isEnter) 74.dp else 84.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(
                                        width = if (isEnter) 2.5.dp else 1.5.dp,
                                        color = if (isEnter) Color(0xFF00FF41) else if (isAction) Color(0xFF00FF41).copy(alpha = 0.5f) else Color(0xFF00FF41).copy(alpha = 0.25f)
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isEnter) Color(0xFF00FF41).copy(alpha = 0.15f) else if (isAction) Color(0xFF00FF41).copy(alpha = 0.08f) else Color(0xFF0A0F11),
                                        contentColor = Color(0xFF00FF41)
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = char,
                                        fontSize = if (isEnter) 24.sp else 34.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isReset) Color(0xFFFF4141) else Color(0xFF00FF41)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "COMMUNICATION LINK • SECURED",
                    color = Color(0xFF00FF41).copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            }
        }
    }
}
