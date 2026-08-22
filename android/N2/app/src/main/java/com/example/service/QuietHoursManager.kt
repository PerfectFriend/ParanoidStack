/**
 * Manager for scheduling and evaluating quiet hours.
 *
 * Quiet hours define a recurring daily window during which
 * notifications are suppressed. Uses AlarmManager to fire
 * start and end intents and provides a runtime check to
 * determine if quiet hours are currently active.
 */
package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import java.util.Calendar

/**
 * Менеджер «тихих часов» — периодов, когда уведомления отключены.
 * Использует AlarmManager для автоматического включения/выключения.
 */
class QuietHoursManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("quiet_hours", Context.MODE_PRIVATE)

    data class QuietHoursConfig(
        val enabled: Boolean = false,
        val startHour: Int = 23,
        val startMinute: Int = 0,
        val endHour: Int = 7,
        val endMinute: Int = 0
    )

    fun getConfig(): QuietHoursConfig {
        return QuietHoursConfig(
            enabled = prefs.getBoolean("enabled", false),
            startHour = prefs.getInt("startHour", 23),
            startMinute = prefs.getInt("startMinute", 0),
            endHour = prefs.getInt("endHour", 7),
            endMinute = prefs.getInt("endMinute", 0)
        )
    }

    fun setConfig(config: QuietHoursConfig) {
        prefs.edit().apply {
            putBoolean("enabled", config.enabled)
            putInt("startHour", config.startHour)
            putInt("startMinute", config.startMinute)
            putInt("endHour", config.endHour)
            putInt("endMinute", config.endMinute)
            apply()
        }
        if (config.enabled) scheduleAlarms(config) else cancelAlarms()
    }

    /** Returns true if the current time falls within the configured quiet hours window */
    fun isQuietHoursNow(): Boolean {
        val config = getConfig()
        if (!config.enabled) return false
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        val currentMinutes = hour * 60 + minute
        val startMinutes = config.startHour * 60 + config.startMinute
        val endMinutes = config.endHour * 60 + config.endMinute
        // If end <= start, the interval spans midnight (e.g., 23:00-07:00)
        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
    }

    /** Schedules daily repeating alarms for quiet hours start and end times */
    private fun scheduleAlarms(config: QuietHoursConfig) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val startIntent = PendingIntent.getBroadcast(
            context, 0, Intent("QUIET_HOURS_START"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val endIntent = PendingIntent.getBroadcast(
            context, 1, Intent("QUIET_HOURS_END"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, config.startHour)
            set(Calendar.MINUTE, config.startMinute)
            set(Calendar.SECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, config.endHour)
            set(Calendar.MINUTE, config.endMinute)
            set(Calendar.SECOND, 0)
        }
        // RTC_WAKEUP fires even when the device is asleep; INTERVAL_DAY repeats daily
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, startCal.timeInMillis, AlarmManager.INTERVAL_DAY, startIntent)
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, endCal.timeInMillis, AlarmManager.INTERVAL_DAY, endIntent)
    }

    private fun cancelAlarms() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(PendingIntent.getBroadcast(context, 0, Intent("QUIET_HOURS_START"), PendingIntent.FLAG_IMMUTABLE))
        alarmManager.cancel(PendingIntent.getBroadcast(context, 1, Intent("QUIET_HOURS_END"), PendingIntent.FLAG_IMMUTABLE))
    }
}
