package com.example.medapp.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.example.medapp.receivers.ReminderReceiver
import java.util.*

object ReminderScheduler {

    fun scheduleWeeklyReminder(
        context: Context,
        reminderId: Int,
        dayOfWeek: Int,
        time: String,
        medicineName: String,
        ownerId: Int,
        note: String? = null // добавляем параметр note
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            openExactAlarmPermissionSettings(context)
            return
        }

        val (hour, minute) = time.split(":").map { it.toInt() }

        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, convertToCalendarDay(dayOfWeek))
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeBeforeNow(this)) add(Calendar.WEEK_OF_YEAR, 1)
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminderId", reminderId)
            putExtra("medicineName", medicineName)
            putExtra("ownerId", ownerId)
            putExtra("time", time)
            putExtra("dayOfWeek", dayOfWeek)
            putExtra("note", note) // передаём заметку
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }

    fun cancelReminder(context: Context, reminderId: Int) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }

    private fun timeBeforeNow(calendar: Calendar) = calendar.timeInMillis < System.currentTimeMillis()

    private fun convertToCalendarDay(appDay: Int) = when (appDay) {
        1 -> Calendar.MONDAY
        2 -> Calendar.TUESDAY
        3 -> Calendar.WEDNESDAY
        4 -> Calendar.THURSDAY
        5 -> Calendar.FRIDAY
        6 -> Calendar.SATURDAY
        7 -> Calendar.SUNDAY
        else -> Calendar.MONDAY
    }

    private fun openExactAlarmPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}
