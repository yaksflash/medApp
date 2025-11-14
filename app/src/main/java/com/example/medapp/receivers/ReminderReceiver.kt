package com.example.medapp.receivers

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.medapp.R
import com.example.medapp.activities.MainActivity
//import java.util.*
import com.example.medapp.utils.ReminderScheduler

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "medicine_reminders"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val medicineName = intent.getStringExtra("medicineName") ?: "Лекарство"
        val user = intent.getStringExtra("user") ?: "Пользователь"
        val time = intent.getStringExtra("time") ?: ""
        val id = intent.getIntExtra("reminderId", 0)
        val dayOfWeek = intent.getIntExtra("dayOfWeek", 1)

        // Создаём уведомление
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Напоминания о лекарствах",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Приём лекарства")
            .setContentText("$medicineName для $user в $time")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(id, notification)

        // Ставим уведомление на следующую неделю
        ReminderScheduler.scheduleWeeklyReminder(context, id, dayOfWeek, time, medicineName, user)
    }
}
