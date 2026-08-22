package com.example.ui.components

import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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

/**
 * Запечатанный интерфейс, описывающий все возможные клавиши матричной клавиатуры.
 * Позволяет единообразно передавать нажатия родительскому компоненту.
 */
sealed interface MatrixKey {
    /** Обычная символьная клавиша (буква, цифра, символ) */
    data class CharKey(val text: String) : MatrixKey
    /** Удаление последнего символа (Backspace) */
    object Backspace : MatrixKey
    /** Пробел */
    object Space : MatrixKey
    /** Верхний регистр (Shift) */
    object Shift : MatrixKey
    /** Переключение языка (RU/EN) */
    object ToggleLang : MatrixKey
    /** Переключение между буквами и символами (ABC / ?12) */
    object ToggleMode : MatrixKey
    /** Перевод строки (Enter) */
    object Enter : MatrixKey
    /** Скрыть клавиатуру */
    object Hide : MatrixKey
}

/**
 * Высокоинтерактивная клавиатура в стиле «Матрица».
 * Включает анимацию падающего двоичного кода (Matrix Rain),
 * тактильный отклик (haptic feedback), переключение раскладок RU/EN/SYMBOLS.
 *
 * @param onKey вызывается при нажатии любой клавиши; передаётся объект MatrixKey
 * @param lang язык по умолчанию ("RU" или "EN")
 * @param modifier модификатор корневого контейнера
 * @param keyPressSound включить звук нажатия (через HapticFeedback)
 * @param keyFontSize размер шрифта подписи клавиш
 * @param keySize высота клавиш в dp
 */
@Composable
fun MatrixStyleKeyboard(
    onKey: (MatrixKey) -> Unit,
    lang: String = "RU",
    modifier: Modifier = Modifier,
    keyPressSound: Boolean = false,
    keyFontSize: Float = 18f,
    keySize: Float = 48f
) {
    // Состояния: раскладка (RU/EN), регистр (Shift), режим (буквы/символы)
    var isRussian by remember { mutableStateOf(lang == "RU") }
    var isShiftActive by remember { mutableStateOf(false) }
    var isSymbolsMode by remember { mutableStateOf(false) }

    // Звук/тактильный отклик при нажатии клавиш
    val localView = LocalView.current
    val playTypeSound = {
        try {
            localView.playSoundEffect(SoundEffectConstants.CLICK)
        } catch (e: Exception) {
            Log.w("MatrixKeyboard", "vibration error: ${e.message}")
        }
        if (keyPressSound) {
            try {
                localView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } catch (e: Exception) {
                Log.w("MatrixKeyboard", "vibration error: ${e.message}")
            }
        }
    }

    // Раскладки клавиш: английская, русская, символьная
    val enRow1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val enRow2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    val enRow3 = listOf("z", "x", "c", "v", "b", "n", "m")

    val ruRow1 = listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ъ")
    val ruRow2 = listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э")
    val ruRow3 = listOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю")

    val symRow1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val symRow2 = listOf("@", "#", "$", "%", "&", "*", "-", "+", "=", "(")
    val symRow3 = listOf(")", "!", "?", "\"", "'", ":", ";", "/", "\\")

    // Корневой контейнер с тёмным фоном и неоновой зелёной обводкой сверху
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
        
        // Фоновый эффект падающих символов (Matrix Rain)
        MatrixFallingRainBg(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        )

        // Столбец с рядами клавиш
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            
            // Верхняя панель статуса: индикатор Caps Lock и текущая раскладка
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
                    // Маленькая светящаяся точка — индикатор Caps Lock
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

                // Информация о раскладке: SYMBOLS / RU-CYRILLIC / EN-LATIN
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SECURE CLIENT INPUT  //  ${if (isSymbolsMode) "SYMBOLS" else if (isRussian) "RU-CYRILLIC" else "EN-LATIN"}",
                        color = Color(0xFF00FF41).copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Ряды клавиш: символьный режим или буквенная раскладка (RU/EN)
            if (isSymbolsMode) {
                KeyboardRow(keys = symRow1, isShift = false, onKeyClicked = {
                    playTypeSound()
                    onKey(MatrixKey.CharKey(it))
                }, keyFontSize = keyFontSize, keySize = keySize)
                KeyboardRow(keys = symRow2, isShift = false, onKeyClicked = {
                    playTypeSound()
                    onKey(MatrixKey.CharKey(it))
                }, keyFontSize = keyFontSize, keySize = keySize)
                KeyboardRow(keys = symRow3, isShift = false, onKeyClicked = {
                    playTypeSound()
                    onKey(MatrixKey.CharKey(it))
                }, keyFontSize = keyFontSize, keySize = keySize)
            } else {
                val row1 = if (isRussian) ruRow1 else enRow1
                val row2 = if (isRussian) ruRow2 else enRow2
                val row3 = if (isRussian) ruRow3 else enRow3

                // После нажатия на букву при активном Shift сбрасываем регистр
                KeyboardRow(keys = row1, isShift = isShiftActive, onKeyClicked = {
                    playTypeSound()
                    onKey(MatrixKey.CharKey(it))
                    if (isShiftActive) {
                        isShiftActive = false
                    }
                }, keyFontSize = keyFontSize, keySize = keySize)
                KeyboardRow(keys = row2, isShift = isShiftActive, onKeyClicked = {
                    playTypeSound()
                    onKey(MatrixKey.CharKey(it))
                    if (isShiftActive) {
                        isShiftActive = false
                    }
                }, keyFontSize = keyFontSize, keySize = keySize)
                KeyboardRow(keys = row3, isShift = isShiftActive, onKeyClicked = {
                    playTypeSound()
                    onKey(MatrixKey.CharKey(it))
                    if (isShiftActive) {
                        isShiftActive = false
                    }
                }, keyFontSize = keyFontSize, keySize = keySize)
            }

            // Нижний ряд с командными клавишами: Shift, переключение режима, язык, пробел, backspace, скрыть
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Клавиша Shift — включает верхний регистр (только в буквенном режиме)
                TextKeyButton(
                    text = "SHIFT",
                    onClick = {
                        playTypeSound()
                        if (!isSymbolsMode) {
                            isShiftActive = !isShiftActive
                        }
                    },
                    modifier = Modifier.weight(1.2f),
                    isActiveSignal = isShiftActive && !isSymbolsMode,
                    iconType = "shift",
                    keyFontSize = keyFontSize,
                    keySize = keySize
                )

                // Переключение буквенного и символьного режима (ABC / ?12)
                TextKeyButton(
                    text = if (isSymbolsMode) "ABC" else "?12",
                    onClick = {
                        playTypeSound()
                        isSymbolsMode = !isSymbolsMode
                    },
                    modifier = Modifier.weight(1.0f),
                    keyFontSize = keyFontSize,
                    keySize = keySize
                )

                // Клавиша переключения языка RU/EN (только в буквенном режиме)
                if (!isSymbolsMode) {
                    TextKeyButton(
                        text = if (isRussian) "RU" else "EN",
                        onClick = {
                            playTypeSound()
                            isRussian = !isRussian
                        },
                        modifier = Modifier.weight(1.0f),
                        isActiveSignal = true,
                        keyFontSize = keyFontSize,
                        keySize = keySize
                    )
                }

                // Пробел (самая широкая клавиша)
                TextKeyButton(
                    text = "SPACE",
                    onClick = {
                        playTypeSound()
                        onKey(MatrixKey.Space)
                    },
                    modifier = Modifier.weight(2.6f),
                    keyFontSize = keyFontSize,
                    keySize = keySize
                )

                // Backspace — удаление последнего символа
                TextKeyButton(
                    text = "⌫",
                    onClick = {
                        playTypeSound()
                        onKey(MatrixKey.Backspace)
                    },
                    modifier = Modifier.weight(1.2f),
                    keyFontSize = keyFontSize,
                    keySize = keySize
                )

                // Скрыть клавиатуру (красная стрелка вниз)
                TextKeyButton(
                    text = "▼",
                    onClick = {
                        playTypeSound()
                        onKey(MatrixKey.Hide)
                    },
                    modifier = Modifier.weight(0.9f),
                    color = Color(0xFFFF3333),
                    keyFontSize = keyFontSize,
                    keySize = keySize
                )
            }
        }
    }
}

/**
 * Ряд буквенных/символьных клавиш с анимацией нажатия (scale).
 * Каждая клавиша имеет моноширинный шрифт и неоновый зелёный цвет.
 *
 * @param keys список символов для отображения
 * @param isShift применять верхний регистр
 * @param onKeyClicked вызывается при нажатии на клавишу
 * @param keyFontSize размер шрифта
 * @param keySize высота клавиш
 */
@Composable
private fun KeyboardRow(
    keys: List<String>,
    isShift: Boolean,
    onKeyClicked: (String) -> Unit,
    keyFontSize: Float = 18f,
    keySize: Float = 48f
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { k ->
            // Если Shift активен — отображаем заглавную букву
            val dispChar = if (isShift) k.uppercase() else k.lowercase()
            // Источник взаимодействия для отслеживания нажатия и анимации масштаба
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.95f else 1f,
                animationSpec = tween(durationMillis = 100),
                label = "keyScale"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(keySize.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F161C).copy(alpha = 0.85f))
                    .border(1.dp, Color(0xFF00FF41).copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                    .clickable(interactionSource = interactionSource, indication = null) { onKeyClicked(dispChar) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dispChar,
                    color = Color(0xFF00FF41),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = keyFontSize.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Кнопка с текстом для командных клавиш (Shift, Space, Enter и т.д.).
 * Поддерживает состояние активности, при котором фон и обводка становятся ярче.
 *
 * @param text текст на кнопке
 * @param onClick вызывается при нажатии
 * @param modifier модификатор
 * @param isActiveSignal подсветка активного состояния
 * @param цвет текста (по умолчанию зелёный #00FF41)
 * @param iconType тип иконки (зарезервировано)
 * @param keyFontSize размер шрифта
 * @param keySize высота кнопки
 */
@Composable
private fun TextKeyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActiveSignal: Boolean = false,
    color: Color = Color(0xFF00FF41),
    iconType: String = "",
    keyFontSize: Float = 18f,
    keySize: Float = 48f
) {
    // Цвета в зависимости от активности
    val finalBorderColor = if (isActiveSignal) Color(0xFF00FF41) else Color(0xFF00FF41).copy(alpha = 0.2f)
    val finalBgColor = if (isActiveSignal) Color(0xFF00FF41).copy(alpha = 0.25f) else Color(0xFF0E141B).copy(alpha = 0.9f)
    val finalTextColor = if (isActiveSignal) Color.White else color
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "textKeyScale"
    )

    Box(
        modifier = modifier
            .height(keySize.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(finalBgColor)
            .border(1.dp, finalBorderColor, RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = finalTextColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = keyFontSize.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Фоновый эффект «цифровой дождь» в стиле Матрицы.
 * Рисует падающие символы (буквы, цифры, катакана) на Canvas с эффектом затухания хвоста.
 *
 * @param modifier модификатор Canvas
 */
@Composable
fun MatrixFallingRainBg(
    modifier: Modifier = Modifier
) {
    val colorPrimary = Color(0xFF00FF41)
    
    // Счётчик тиков для анимации (120 мс интервал)
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(120)
            tick++
        }
    }

    // Конфигурация потоков: начальные Y-смещения, скорости, символы
    val streamCount = 18
    val yOffsets = remember { mutableStateListOf<Float>().apply {
        repeat(streamCount) { add((1..20).random().toFloat() * -12f) }
    }}
    val speeds = remember { listOf(6f, 8f, 10f, 12f, 7f, 9f, 5f, 11f, 13f, 8f, 9f, 7f, 10f, 12f, 6f, 5f, 11f, 8f) }
    val characters = remember { mutableStateListOf<String>().apply {
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZアイウエオカキクケコサシスセ"
        repeat(streamCount) { add(chars.random().toString()) }
    }}

    // На каждый тик сдвигаем yOffsets вниз и иногда меняем символ
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

    // Отрисовка на Canvas
    Canvas(modifier = modifier) {
        val width = size.width
        val spacing = width / (streamCount + 1)
        
        for (col in 0 until streamCount) {
            val posX = spacing * (col + 1)
            val posY = yOffsets[col]
            
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = colorPrimary.copy(alpha = 0.25f).hashCode()
                    textSize = 18f
                    typeface = android.graphics.Typeface.MONOSPACE
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                
                // Основной символ потока
                val traceChars = characters[col]
                canvas.nativeCanvas.drawText(traceChars, posX, posY, paint)
                
                // Хвост: предыдущие символы с меньшей прозрачностью
                paint.color = colorPrimary.copy(alpha = 0.10f).hashCode()
                canvas.nativeCanvas.drawText("0", posX, posY - 18f, paint)
                canvas.nativeCanvas.drawText("1", posX, posY - 36f, paint)
                
                // Яркий бело-зелёный лидирующий пиксель
                paint.color = Color.White.copy(alpha = 0.45f).hashCode()
                canvas.nativeCanvas.drawText(traceChars, posX, posY + 1f, paint)
            }
        }
    }
}
