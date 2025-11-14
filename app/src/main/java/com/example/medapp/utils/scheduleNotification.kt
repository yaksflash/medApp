package com.example.medapp.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.medapp.models.Reminder
import com.example.medapp.receivers.ReminderReceiver
import java.util.*

fun scheduleNotification(context: Context, reminder: Reminder) {
    val parts = reminder.time.split(":").map { it.toInt() }
    val hour = parts[0]
    val minute = parts[1]

    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, hour)
    calendar.set(Calendar.MINUTE, minute)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)

    // Рассчитываем сколько дней до нужного дня недели
    val today = calendar.get(Calendar.DAY_OF_WEEK)
    var daysDiff = reminder.dayOfWeek - today
    if (daysDiff < 0 || (daysDiff == 0 && calendar.timeInMillis < System.currentTimeMillis())) {
        daysDiff += 7
    }
    calendar.add(Calendar.DAY_OF_YEAR, daysDiff)

    val intent = Intent(context, ReminderReceiver::class.java).apply {
        putExtra("medicineName", reminder.medicineName)
        putExtra("user", reminder.user)
        putExtra("time", reminder.time) // добавляем время
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        (reminder.medicineName + reminder.user + reminder.dayOfWeek + reminder.time).hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    try {
        // Проверяем, можно ли ставить точные будильники (Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                // Если точные будильники запрещены, ставим обычный повторяющийся
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } else {
            // Для Android < 12
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    } catch (e: SecurityException) {
        // Если всё равно SecurityException, можно fallback на обычный AlarmManager.set()
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }

}
