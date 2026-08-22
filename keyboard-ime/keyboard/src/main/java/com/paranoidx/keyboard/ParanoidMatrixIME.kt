package com.paranoidx.keyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * ParanoidMatrixIME - System-wide InputMethodService with Matrix-style keyboard
 * Supports RU/EN languages, symbols, shift, haptic feedback
 */
class ParanoidMatrixIME : InputMethodService(), LifecycleEventObserver {

    private var composeView: ComposeView? = null
    private var currentInputConnection: InputConnection? = null
    private var isRussian = false
    private var isShiftActive = false
    private var isSymbolsMode = false
    private val coroutineJob = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + coroutineJob)

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        Log.d("ParanoidMatrixIME", "IME Service created")
    }

    override fun onCreateInputView(): View {
        composeView = ComposeView(this).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MatrixKeyboardUI(
                    onKey = this@ParanoidMatrixIME::onKeyPressed,
                    isRussian = isRussian,
                    isShiftActive = isShiftActive,
                    isSymbolsMode = isSymbolsMode,
                    onLanguageToggle = { isRussian = !isRussian },
                    onShiftToggle = { isShiftActive = !isShiftActive },
                    onSymbolsToggle = { isSymbolsMode = !isSymbolsMode },
                    onHideKeyboard = { requestHideSelf(0) }
                )
            }
        }
        return composeView!!
    }

    override fun onStartInputView(editorInfo: EditorInfo, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        currentInputConnection = currentInputConnection
        Log.d("ParanoidMatrixIME", "Input view started")
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        composeView = null
        Log.d("ParanoidMatrixIME", "Input view finished")
    }

    override fun onStartInput(editorInfo: EditorInfo, restarting: Boolean) {
        super.onStartInput(editorInfo, restarting)
        currentInputConnection = currentInputConnection
    }

    private fun onKeyPressed(key: MatrixKey) {
        val ic = currentInputConnection ?: return
        
        when (key) {
            is MatrixKey.CharKey -> {
                val text = if (isShiftActive && !isSymbolsMode) key.text.uppercase() else key.text
                ic.commitText(text, 1)
                if (isShiftActive && !isSymbolsMode) isShiftActive = false
                playHapticFeedback()
            }
            MatrixKey.Backspace -> {
                ic.deleteSurroundingText(1, 0)
                playHapticFeedback()
            }
            MatrixKey.Space -> {
                ic.commitText(" ", 1)
                playHapticFeedback()
            }
            MatrixKey.Enter -> {
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER))
                playHapticFeedback()
            }
            MatrixKey.Hide -> {
                requestHideSelf(0)
            }
            MatrixKey.Shift -> {
                if (!isSymbolsMode) isShiftActive = !isShiftActive
                playHapticFeedback()
                composeView?.invalidate()
            }
            MatrixKey.ToggleLang -> {
                isRussian = !isRussian
                playHapticFeedback()
                composeView?.invalidate()
            }
            MatrixKey.ToggleMode -> {
                isSymbolsMode = !isSymbolsMode
                if (isSymbolsMode) isShiftActive = false
                playHapticFeedback()
                composeView?.invalidate()
            }
        }
    }

    private fun playHapticFeedback() {
        try {
            val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(15, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(15)
            }
        } catch (e: Exception) {
            Log.w("ParanoidMatrixIME", "Haptic error: ${e.message}")
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_DESTROY -> coroutineJob.cancel()
            else -> {}
        }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        coroutineJob.cancel()
        super.onDestroy()
    }
}

/**
 * Sealed interface for keyboard keys
 */
sealed interface MatrixKey {
    data class CharKey(val text: String) : MatrixKey
    object Backspace : MatrixKey
    object Space : MatrixKey
    object Shift : MatrixKey
    object ToggleLang : MatrixKey
    object ToggleMode : MatrixKey
    object Enter : MatrixKey
    object Hide : MatrixKey
}

/**
 * Compose UI for the Matrix Keyboard
 */
@Composable
fun MatrixKeyboardUI(
    onKey: (MatrixKey) -> Unit,
    isRussian: Boolean,
    isShiftActive: Boolean,
    isSymbolsMode: Boolean,
    onLanguageToggle: () -> Unit,
    onShiftToggle: () -> Unit,
    onSymbolsToggle: () -> Unit,
    onHideKeyboard: () -> Unit
) {
    // Keyboard layouts
    val enRow1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val enRow2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    val enRow3 = listOf("z", "x", "c", "v", "b", "n", "m")
    
    val ruRow1 = listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ъ")
    val ruRow2 = listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э")
    val ruRow3 = listOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю")
    
    val symRow1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val symRow2 = listOf("@", "#", "$", "%", "&", "*", "-", "+", "=", "(")
    val symRow3 = listOf(")", "!", "?", "\"", "'", ":", ";", "/", "\\")

    val activeLang = if (isRussian) "RU" else "EN"
    val (row1, row2, row3) = if (isSymbolsMode) {
        Triple(symRow1, symRow2, symRow3)
    } else if (isRussian) {
        Triple(ruRow1, ruRow2, ruRow3)
    } else {
        Triple(enRow1, enRow2, enRow3)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF070B0E))
            .padding(top = 8.dp, bottom = 12.dp, start = 4.dp, end = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Status row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
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
                        text = "SECURE IME INPUT  //  ${if (isSymbolsMode) "SYMBOLS" else "$activeLang-${if (isRussian) "CYRILLIC" else "LATIN"}"}",
                        color = Color(0xFF00FF41).copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Keyboard rows
            if (isSymbolsMode) {
                KeyboardRow(keys = row1, isShift = false, onKeyClicked = { onKey(MatrixKey.CharKey(it)) })
                KeyboardRow(keys = row2, isShift = false, onKeyClicked = { onKey(MatrixKey.CharKey(it)) })
                KeyboardRow(keys = row3, isShift = false, onKeyClicked = { onKey(MatrixKey.CharKey(it)) })
            } else {
                KeyboardRow(keys = row1, isShift = isShiftActive, onKeyClicked = { 
                    onKey(MatrixKey.CharKey(if (isShiftActive) it.uppercase() else it))
                    if (isShiftActive) onShiftToggle()
                })
                KeyboardRow(keys = row2, isShift = isShiftActive, onKeyClicked = { 
                    onKey(MatrixKey.CharKey(if (isShiftActive) it.uppercase() else it))
                    if (isShiftActive) onShiftToggle()
                })
                KeyboardRow(keys = row3, isShift = isShiftActive, onKeyClicked = { 
                    onKey(MatrixKey.CharKey(if (isShiftActive) it.uppercase() else it))
                    if (isShiftActive) onShiftToggle()
                })
            }

            // Bottom row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextKeyButton(
                    text = "SHIFT",
                    onClick = onShiftToggle,
                    modifier = Modifier.weight(1.2f),
                    isActive = isShiftActive && !isSymbolsMode
                )

                TextKeyButton(
                    text = if (isSymbolsMode) "ABC" else "?12",
                    onClick = onSymbolsToggle,
                    modifier = Modifier.weight(1.0f)
                )

                if (!isSymbolsMode) {
                    TextKeyButton(
                        text = activeLang,
                        onClick = onLanguageToggle,
                        modifier = Modifier.weight(1.0f),
                        isActive = true
                    )
                }

                TextKeyButton(
                    text = "SPACE",
                    onClick = { onKey(MatrixKey.Space) },
                    modifier = Modifier.weight(2.6f)
                )

                TextKeyButton(
                    text = "⌫",
                    onClick = { onKey(MatrixKey.Backspace) },
                    modifier = Modifier.weight(1.2f)
                )

                TextKeyButton(
                    text = "▼",
                    onClick = onHideKeyboard,
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
                    .fillMaxWidth()
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
    isActive: Boolean = false,
    color: Color = Color(0xFF00FF41)
) {
    val finalBorderColor = if (isActive) Color(0xFF00FF41) else Color(0xFF00FF41).copy(alpha = 0.2f)
    val finalBgColor = if (isActive) Color(0xFF00FF41).copy(alpha = 0.25f) else Color(0xFF0E141B).copy(alpha = 0.9f)
    val finalTextColor = if (isActive) Color.White else color

    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(finalBgColor)
            .border(1.dp, finalBorderColor, RoundedCornerShape(8.dp))
            .fillMaxWidth()
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