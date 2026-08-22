/**
 * Контроллер маскировки (mimicry) приложения.
 *
 * Позволяет скрыть настоящее приложение под видом другого
 * (калькулятор, музыкальный плеер, крестики-нолики).
 * При активации переключает компоненты Android (Activity-Alias)
 * так, чтобы на главном экране отображался выбранный "декор",
 * а доступ к реальному приложению осуществлялся через
 * секретный код (ежедневно генерируемый из даты).
 */
package com.example.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Контроллер маскировки приложения под другие программы.
 *
 * Позволяет скрыть реальное приложение, активируя один из
 * Activity-алиасов (калькулятор, плеер, игра). Доступ
 * к настоящему приложению защищается ежедневным кодом,
 * генерируемым из текущей даты (формат ДДММ).
 */
object MimicryController {
    private const val TAG = "MimicryController"
    /** Ключ SharedPreferences: активна ли маскировка */
    private const val PREFS_KEY = "mimicry_active"
    /** Ключ SharedPreferences: выбранный режим маскировки */
    private const val PREFS_MODE_KEY = "mimicry_mode"
    /** Ключ SharedPreferences: код разблокировки */
    private const val PREFS_UNLOCK_KEY = "calculator_unlock_seq"

    /**
     * Режимы маскировки (виды приложений-прикрытий).
     *
     * @property aliasSuffix  Суффикс имени Activity-Alias в AndroidManifest.
     * @property labelResName Имя ресурса метки (заголовка) приложения.
     * @property iconResName  Имя ресурса иконки приложения.
     */
    enum class MimicMode(val aliasSuffix: String, val labelResName: String, val iconResName: String) {
        /** Маскировка под калькулятор */
        CALCULATOR("DecoyCalculator-Alias", "app_name_calculator", "@mipmap/ic_calculator"),
        /** Маскировка под музыкальный плеер */
        MUSIC_PLAYER("DecoyMusic-Alias", "app_name_music", "@mipmap/ic_launcher"),
        /** Маскировка под игру "крестики-нолики" */
        TIC_TAC_TOE("DecoyTic-Alias", "app_name_tic", "@mipmap/ic_launcher")
    }

    /** Текущий активный режим (по умолчанию — калькулятор) */
    private var currentMode: MimicMode = MimicMode.CALCULATOR

    /**
     * Проверяет, активна ли маскировка.
     *
     * @param prefs SharedPreferences приложения.
     * @return true, если маскировка включена.
     */
    fun isActive(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREFS_KEY, false)

    /**
     * Возвращает текущий режим маскировки.
     *
     * @param prefs SharedPreferences приложения.
     * @return Текущий режим или CALCULATOR по умолчанию.
     */
    fun getMode(prefs: SharedPreferences): MimicMode = try {
        MimicMode.valueOf(prefs.getString(PREFS_MODE_KEY, MimicMode.CALCULATOR.name) ?: MimicMode.CALCULATOR.name)
    } catch (e: Exception) { MimicMode.CALCULATOR }

    /**
     * Возвращает код разблокировки для доступа к настоящему приложению.
     *
     * @param prefs SharedPreferences приложения.
     * @return Код разблокировки (по умолчанию "1937").
     */
    fun getUnlockCode(prefs: SharedPreferences): String =
        prefs.getString(PREFS_UNLOCK_KEY, "1937") ?: "1937"

    /**
     * Активирует маскировку приложения.
     *
     * Генерирует ежедневный код разблокировки из текущей даты (ddMM),
     * отключает настоящий лаунчер (MainActivity-Alias) и включает
     * выбранный Activity-алиас прикрытия. После активации сразу
     * запускает Activity-прикрытие.
     *
     * @param ctx  Контекст приложения.
     * @param mode Режим маскировки (по умолчанию CALCULATOR).
     */
    /** Enables mimicry by disabling the real launcher and enabling the decoy activity */
    fun activate(ctx: Context, mode: MimicMode = MimicMode.CALCULATOR) {
        try {
            val prefs = ctx.getSharedPreferences("crazy_backgammon_prefs", Context.MODE_PRIVATE)
            // Генерируем ежедневный код разблокировки на основе даты
            // Changes daily so the unlock code is not static — format: ddMM (e.g., "1007" for July 10)
            val dateStr = SimpleDateFormat("ddMM", Locale.US).format(Date())
            val unlockCode = dateStr
            prefs.edit()
                .putBoolean(PREFS_KEY, true)
                .putString(PREFS_MODE_KEY, mode.name)
                .putString(PREFS_UNLOCK_KEY, unlockCode)
                .apply()

            val pm = ctx.packageManager
            val pkg = ctx.packageName

            // Отключаем настоящий лаунчер приложения
            pm.setComponentEnabledSetting(
                ComponentName(ctx, "$pkg.MainActivity-Alias"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            // Включаем лаунчер-прикрытие (декор)
            pm.setComponentEnabledSetting(
                ComponentName(ctx, "$pkg.${mode.aliasSuffix}"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            Log.i(TAG, "Mimicry activated: $mode (unlock: $unlockCode)")

            // Запускаем Activity-прикрытие
            val target = when (mode) {
                MimicMode.CALCULATOR -> "com.example.DecoyCalculatorActivity"
                MimicMode.MUSIC_PLAYER -> "com.example.DecoyMusicActivity"
                MimicMode.TIC_TAC_TOE -> "com.example.DecoyTicTacToeActivity"
            }
            val intent = Intent().apply {
                setClassName(ctx, target)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Activate failed", e)
        }
    }

    /**
     * Деактивирует маскировку и восстанавливает настоящий лаунчер.
     *
     * Включает MainActivity-Alias и отключает все Activity-алиасы
     * прикрытия (калькулятор, плеер, игра).
     *
     * @param ctx Контекст приложения.
     */
    /** Restores the real launcher and disables all decoy activity aliases */
    fun deactivate(ctx: Context) {
        try {
            val prefs = ctx.getSharedPreferences("crazy_backgammon_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean(PREFS_KEY, false).apply()

            val pm = ctx.packageManager
            val pkg = ctx.packageName

            // Включаем настоящий лаунчер
            pm.setComponentEnabledSetting(
                ComponentName(ctx, "$pkg.MainActivity-Alias"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            // Отключаем все лаунчеры-прикрытия
            for (m in MimicMode.values()) {
                pm.setComponentEnabledSetting(
                    ComponentName(ctx, "$pkg.${m.aliasSuffix}"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }

            Log.i(TAG, "Mimicry deactivated")
        } catch (e: Exception) {
            Log.e(TAG, "Deactivate failed", e)
        }
    }
}
