package com.example.ui.components

import android.view.SoundEffectConstants
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Sealed interface for Keys to communicate beautifully with parent viewports
sealed interface MatrixKey {
    data class CharKey(val text: String) : MatrixKey
    object Backspace : MatrixKey
    object Space : MatrixKey
    object Shift : MatrixKey
    object ToggleLang : MatrixKey
    object ToggleMode : MatrixKey // Toggle between ABC and Symbols (123 / ?%#)
    object Enter : MatrixKey
    object Hide : MatrixKey
}

@Composable
fun MatrixStyleKeyboard(
    onKey: (MatrixKey) -> Unit,
    lang: String = "EN",
    modifier: Modifier = Modifier
) {
    var isSecondaryLang by remember { mutableStateOf(false) }
    var isShiftActive by remember { mutableStateOf(false) }
    var isSymbolsMode by remember { mutableStateOf(false) }

    val localView = LocalView.current
    val playTypeSound = {
        try {
            localView.playSoundEffect(SoundEffectConstants.CLICK)
        } catch (_: Exception) {}
    }

    val enRow1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val enRow2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    val enRow3 = listOf("z", "x", "c", "v", "b", "n", "m")

    val ruRow1 = listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ъ")
    val ruRow2 = listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э")
    val ruRow3 = listOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю")

    val deRow1 = listOf("q", "w", "e", "r", "t", "z", "u", "i", "o", "p", "ü")
    val deRow2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ö", "ä")
    val deRow3 = listOf("y", "x", "c", "v", "b", "n", "m")

    val esRow1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val esRow2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ñ")
    val esRow3 = listOf("z", "x", "c", "v", "b", "n", "m")

    val frRow1 = listOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p")
    val frRow2 = listOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m", "ù")
    val frRow3 = listOf("w", "x", "c", "v", "b", "n", "'", "ç")

    val zhRow1 = enRow1
    val zhRow2 = enRow2
    val zhRow3 = enRow3

    val activeLang = if (isSecondaryLang) lang else "EN"

    val (row1, row2, row3) = if (isSecondaryLang) {
        when (lang) {
            "RU" -> Triple(ruRow1, ruRow2, ruRow3)
            "DE" -> Triple(deRow1, deRow2, deRow3)
            "ES" -> Triple(esRow1, esRow2, esRow3)
            "FR" -> Triple(frRow1, frRow2, frRow3)
            "ZH" -> Triple(zhRow1, zhRow2, zhRow3)
            else -> Triple(enRow1, enRow2, enRow3)
        }
    } else {
        Triple(enRow1, enRow2, enRow3)
    }

    val symRow1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val symRow2 = listOf("@", "#", "$", "%", "&", "*", "-", "+", "=", "(")
    val symRow3 = listOf(")", "!", "?", "\"", "'", ":", ";", "/", "\\")

    fun langLabel(code: String): String = when (code) {
        "EN" -> "EN"
        "RU" -> "RU"
        "DE" -> "DE"
        "ES" -> "ES"
        "FR" -> "FR"
        "ZH" -> "中文"
        else -> code
    }

    fun langStatus(code: String): String = when (code) {
        "EN" -> "EN-LATIN"
        "RU" -> "RU-CYRILLIC"
        "DE" -> "DE-QWERTZ"
        "ES" -> "ES-ESPAÑOL"
        "FR" -> "FR-AZERTY"
        "ZH" -> "ZH-拼音"
        else -> code
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF070B0E))
            .border(
                border = BorderStroke(1.dp, Brush.verticalGradient(listOf(Color(0xFF00FF41).copy(alpha = 0.5f), Color.Transparent))),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(top = 8.dp, bottom = 12.dp, start = 4.dp, end = 4.dp)
    ) {
        MatrixFallingRainBg(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = if (isShiftActive) Color(0xFF00FF41) else Color.Gray.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(50)
                            )
                    )
                    Text(
                        text = if (isShiftActive) "CAPS ACTIVE" else "caps off",
                        color = if (isShiftActive) Color(0xFF00FF41) else Color.Gray.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SECURE CLIENT INPUT  //  ${if (isSymbolsMode) "SYMBOLS" else langStatus(activeLang)}",
                        color = Color(0xFF00FF41).copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (isSymbolsMode) {
                KeyboardRow(keys = symRow1, isShift = false, onKeyClicked = {
                    playTypeSound()
                    onKey(MatrixKey.CharKey(it))
                })
                KeyboardRow(keys = symRow2, isShift = false, onKeyClicked = {
                    playTypeSound()
                    onKey(MatrixKey.CharKey(it))
                })
                KeyboardRow(keys = symRow3, isShift = false, onKeyClicked = {
                    playTypeSound()
                    onKey(MatrixKey.CharKey(it))
                })
            } else {
                KeyboardRow(keys = row1, isShift = isShiftActive, onKeyClicked = {
                    playTypeSound()
                    onKey(MatrixKey.CharKey(it))
                    if (isShiftActive) isShiftActive = false
                })
                KeyboardRow(keys = row2, isShift = isShiftActive, onKeyClicked = {
                    playTypeSound()
                    onKey(MatrixKey.CharKey(it))
                    if (isShiftActive) isShiftActive = false
                })
                KeyboardRow(keys = row3, isShift = isShiftActive, onKeyClicked = {
                    playTypeSound()
                    onKey(MatrixKey.CharKey(it))
                    if (isShiftActive) isShiftActive = false
                })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextKeyButton(
                    text = "SHIFT",
                    onClick = {
                        playTypeSound()
                        if (!isSymbolsMode) isShiftActive = !isShiftActive
                    },
                    modifier = Modifier.weight(1.2f),
                    isActiveSignal = isShiftActive && !isSymbolsMode,
                    iconType = "shift"
                )

                TextKeyButton(
                    text = if (isSymbolsMode) "ABC" else "?12",
                    onClick = {
                        playTypeSound()
                        isSymbolsMode = !isSymbolsMode
                    },
                    modifier = Modifier.weight(1.0f)
                )

                if (!isSymbolsMode) {
                    TextKeyButton(
                        text = langLabel(activeLang),
                        onClick = {
                            playTypeSound()
                            isSecondaryLang = !isSecondaryLang
                        },
                        modifier = Modifier.weight(1.0f),
                        isActiveSignal = true
                    )
                }

                TextKeyButton(
                    text = "SPACE",
                    onClick = {
                        playTypeSound()
                        onKey(MatrixKey.Space)
                    },
                    modifier = Modifier.weight(2.6f)
                )

                TextKeyButton(
                    text = "⌫",
                    onClick = {
                        playTypeSound()
                        onKey(MatrixKey.Backspace)
                    },
                    modifier = Modifier.weight(1.2f)
                )

                TextKeyButton(
                    text = "▼",
                    onClick = {
                        playTypeSound()
                        onKey(MatrixKey.Hide)
                    },
                    modifier = Modifier.weight(0.9f),
                    color = Color(0xFFFF3333)
                )
            }
        }
    }
}

@Composable
private fun KeyboardRow(
    keys: List<String>,
    isShift: Boolean,
    onKeyClicked: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { k ->
            val dispChar = if (isShift) k.uppercase() else k.lowercase()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F161C).copy(alpha = 0.85f))
                    .border(1.dp, Color(0xFF00FF41).copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                    .clickable { onKeyClicked(dispChar) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dispChar,
                    color = Color(0xFF00FF41),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun TextKeyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActiveSignal: Boolean = false,
    color: Color = Color(0xFF00FF41),
    iconType: String = ""
) {
    val finalBorderColor = if (isActiveSignal) Color(0xFF00FF41) else Color(0xFF00FF41).copy(alpha = 0.2f)
    val finalBgColor = if (isActiveSignal) Color(0xFF00FF41).copy(alpha = 0.25f) else Color(0xFF0E141B).copy(alpha = 0.9f)
    val finalTextColor = if (isActiveSignal) Color.White else color

    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(finalBgColor)
            .border(1.dp, finalBorderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = finalTextColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Super lightweight canvas-based matrix digital code falling stream effect background
 */
@Composable
fun MatrixFallingRainBg(
    modifier: Modifier = Modifier
) {
    val colorPrimary = Color(0xFF00FF41)
    
    // Hold animation ticks
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(120)
            tick++
        }
    }

    // Dynamic stream configuration
    val streamCount = 18
    val yOffsets = remember { mutableStateListOf<Float>().apply {
        repeat(streamCount) { add((1..20).random().toFloat() * -12f) }
    }}
    val speeds = remember { listOf(6f, 8f, 10f, 12f, 7f, 9f, 5f, 11f, 13f, 8f, 9f, 7f, 10f, 12f, 6f, 5f, 11f, 8f) }
    val characters = remember { mutableStateListOf<String>().apply {
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZアイウエオカキクケコサシスセ"
        repeat(streamCount) { add(chars.random().toString()) }
    }}

    // Advance falling offsets inside a safe side effect aligned with ticks
    LaunchedEffect(tick) {
        val chars = "0123456789XY7"
        for (i in 0 until streamCount) {
            yOffsets[i] = yOffsets[i] + speeds[i]
            if (yOffsets[i] > 180f) {
                yOffsets[i] = -15f
            }
            if ((1..4).random() == 1) {
                characters[i] = chars.random().toString()
            }
        }
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val spacing = width / (streamCount + 1)
        
        for (col in 0 until streamCount) {
            val posX = spacing * (col + 1)
            val posY = yOffsets[col]
            
            // Draw a vertical falling trace string of characters
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = colorPrimary.copy(alpha = 0.25f).hashCode()
                    textSize = 18f
                    typeface = android.graphics.Typeface.MONOSPACE
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                
                // Tail fading segments
                val traceChars = characters[col]
                canvas.nativeCanvas.drawText(traceChars, posX, posY, paint)
                
                // Draw older trailing character slightly upper with low visibility
                paint.color = colorPrimary.copy(alpha = 0.10f).hashCode()
                canvas.nativeCanvas.drawText("0", posX, posY - 18f, paint)
                canvas.nativeCanvas.drawText("1", posX, posY - 36f, paint)
                
                // Bright white/green lead pixel header
                paint.color = Color.White.copy(alpha = 0.45f).hashCode()
                canvas.nativeCanvas.drawText(traceChars, posX, posY + 1f, paint)
            }
        }
    }
}
