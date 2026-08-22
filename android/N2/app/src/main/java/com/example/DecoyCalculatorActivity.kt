/**
 * Пакет приложения — ложная Activity-калькулятор (Decoy).
 * Используется в режиме маскировки (mimicry): отображает полноценный калькулятор,
 * но содержит скрытый механизм перехода к настоящему приложению при вводе
 * секретного кода и последовательности тапов по дисплею.
 *
 * ## Механизм раскрытия
 * 1. Пользователь вводит математическое выражение, результат которого равен коду (по умолчанию 1937).
 * 2. После появления результата требуется 5 тапов по дисплею.
 * 3. Вызывается [DecoyCalculatorActivity.revealRealApp], который активирует алиас MainActivity
 *    и деактивирует алиас калькулятора через PackageManager.
 */
package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
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

/**
 * Ложная Activity, имитирующая калькулятор.
 * Служит для маскировки настоящего приложения.
 * При вводе секретного кода и нажатии на дисплей активируется [MainActivity].
 */
class DecoyCalculatorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalculatorApp(
                onUnlock = { revealRealApp(this@DecoyCalculatorActivity) }
            )
        }
    }

    companion object {
        /**
         * Раскрывает настоящее приложение: активирует алиас MainActivity и
         * деактивирует алиас калькулятора.
         */
        fun revealRealApp(ctx: Context) {
            try {
                val prefs = ctx.getSharedPreferences("crazy_backgammon_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("mimicry_active", false).apply()
                val pm = ctx.packageManager
                val realAlias = ComponentName(ctx, "${ctx.packageName}.MainActivity-Alias")
                val calcAlias = ComponentName(ctx, "${ctx.packageName}.DecoyCalculator-Alias")
                pm.setComponentEnabledSetting(
                    realAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    calcAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                val intent = Intent(ctx, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                ctx.startActivity(intent)
                (ctx as? ComponentActivity)?.finish()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

/**
 * Composable-экран калькулятора (обманка).
 * При вводе правильного кода-результата и последующих 5 тапах по дисплею
 * вызывает [onUnlock] для перехода к настоящему приложению.
 *
 * @param onUnlock коллбэк для раскрытия настоящего приложения.
 */
@Composable
fun CalculatorApp(onUnlock: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("crazy_backgammon_prefs", Context.MODE_PRIVATE) }
    val unlockCode = remember { prefs.getString("calculator_unlock_seq", "1937") ?: "1937" }
    val tapCountNeeded = 5

    var display by remember { mutableStateOf("0") }
    var expression by remember { mutableStateOf("") }
    var currentInput by remember { mutableStateOf("") }
    var unlocked by remember { mutableStateOf(false) }
    var displayTaps by remember { mutableStateOf(0) }
    var hintText by remember { mutableStateOf("") }

    /**
     * Простой рекурсивный парсер математических выражений (+, -, *, /).
     * Поддерживает скобки, унарный минус.
     */
    fun evaluate(expr: String): Double {
        if (expr.isBlank() || expr == "0") return 0.0
        val s = expr.replace("×", "*").replace("÷", "/")
        return try {
            val parser = object {
                var i = -1; var c = 0
                fun next() { c = if (++i < s.length) s[i].code else -1 }
                fun skip(c: Int): Boolean {
                    while (this.c == ' '.code) next()
                    if (this.c == c) { next(); return true }
                    return false
                }
                fun parseAdd(): Double {
                    var x = parseMul()
                    while (true) {
                        when { skip('+'.code) -> x += parseMul(); skip('-'.code) -> x -= parseMul(); else -> return x }
                    }
                }
                fun parseMul(): Double {
                    var x = parsePrim()
                    while (true) {
                        when { skip('*'.code) -> x *= parsePrim(); skip('/'.code) -> x /= parsePrim(); else -> return x }
                    }
                }
                fun parsePrim(): Double {
                    if (skip('+'.code)) return parsePrim()
                    if (skip('-'.code)) return -parsePrim()
                    val sp = i
                    if (skip('('.code)) { val x = parseAdd(); skip(')'.code); return x }
                    while (c in '0'.code..'9'.code || c == '.'.code) next()
                    return s.substring(sp, i).toDouble()
                }
            }
            parser.next(); parser.parseAdd()
        } catch (e: Exception) { 0.0 }
    }

    // Цветовая палитра калькулятора (iOS-подобная)
    val buttonColor = Color(0xFF333333)
    val operatorColor = Color(0xFFFF9500)
    val functionColor = Color(0xFFA5A5A5)
    val bgColor = Color(0xFF000000)
    val displayColor = Color(0xFF1C1C1C)

    /** Обработчик нажатия кнопок калькулятора. */
    fun pressButton(value: String) {
        when (value) {
            "C" -> { display = "0"; expression = ""; currentInput = ""; displayTaps = 0; hintText = ""; unlocked = false }
            "⌫" -> {
                if (currentInput.length > 1) { currentInput = currentInput.dropLast(1); display = currentInput }
                else { currentInput = ""; display = "0" }
            }
            "=" -> {
                try {
                    val result = evaluate(expression)
                    display = if (result == result.toLong().toDouble()) result.toLong().toString()
                    else String.format("%.8f", result).trimEnd('0').trimEnd('.')
                    expression = display
                    currentInput = display
                    displayTaps = 0
                    if (expression.trim() == unlockCode) {
                        unlocked = true
                        hintText = "Tap display $tapCountNeeded times to continue"
                    }
                } catch (e: Exception) {
                    display = "Error"; expression = ""
                }
            }
            "+", "-", "×", "÷" -> {
                expression += currentInput.ifEmpty { "0" } + when (value) { "×" -> "*"; "÷" -> "/"; else -> value }
                currentInput = ""; display = value
            }
            "." -> { if (!currentInput.contains(".")) { currentInput += "."; display = currentInput } }
            "±" -> {
                currentInput = if (currentInput.startsWith("-")) currentInput.drop(1)
                else if (currentInput.isNotEmpty() && currentInput != "0") "-$currentInput"
                else currentInput
                display = currentInput
            }
            else -> {
                currentInput = if (currentInput == "0" || currentInput == "") value else currentInput + value
                expression = currentInput; display = currentInput
            }
        }
    }

    /** Обработчик тапа по дисплею (для разблокировки). */
    fun onDisplayTap() {
        if (unlocked) {
            displayTaps++
            if (displayTaps >= tapCountNeeded) {
                onUnlock()
            } else {
                hintText = "Tap ${tapCountNeeded - displayTaps} more times"
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(bgColor),
        verticalArrangement = Arrangement.Bottom
    ) {
        // Дисплей калькулятора
        Box(
            modifier = Modifier.fillMaxWidth().height(160.dp).background(displayColor)
                .clickable { onDisplayTap() }
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Column(horizontalAlignment = Alignment.End) {
                if (hintText.isNotEmpty()) {
                    Text(hintText, color = Color(0xFF8E8E93), fontSize = 14.sp)
                }
                Text(expression.takeLast(20).ifEmpty { "" }, color = Color(0xFF8E8E93), fontSize = 24.sp, textAlign = TextAlign.End)
                Text(display.takeLast(12), color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.End)
            }
        }

        // Сетка кнопок калькулятора
        val buttons = listOf(
            listOf("C", "±", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "-"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "⌫", "=")
        )
        for (row in buttons) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (btn in row) {
                    val isOperator = btn in listOf("+", "-", "×", "÷", "=")
                    val isFunction = btn in listOf("C", "±", "%", "⌫")
                    val btnMod = if (btn == "0") Modifier.weight(2f) else Modifier.weight(1f)
                    Box(
                        modifier = btnMod.aspectRatio(if (btn == "0") 2.1f else 1f)
                            .clip(CircleShape)
                            .background(when { isOperator -> operatorColor; isFunction -> functionColor; else -> buttonColor })
                            .clickable { pressButton(btn) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(btn, color = if (isFunction) Color.Black else Color.White,
                            fontSize = if (btn == "0") 28.sp else 32.sp,
                            fontWeight = if (isOperator) FontWeight.Medium else FontWeight.Normal)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
