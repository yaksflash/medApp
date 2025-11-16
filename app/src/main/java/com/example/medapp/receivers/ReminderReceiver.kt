package com.example.medapp.receivers

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.medapp.R
import com.example.medapp.activities.MainActivity
import com.example.medapp.data.AppDatabase
import com.example.medapp.utils.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "medicine_reminders"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val medicineName = intent.getStringExtra("medicineName") ?: "Лекарство"
        val time = intent.getStringExtra("time") ?: ""
        val reminderId = intent.getIntExtra("reminderId", 0)
        val dayOfWeek = intent.getIntExtra("dayOfWeek", 1)
        val ownerId = intent.getIntExtra("ownerId", -1)

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val childDao = db.childDao()

            // Получаем данные текущего пользователя
            val prefs = context.getSharedPreferences("user_data", 0)
            val currentAccountType = prefs.getString("account_type", "parent") ?: "parent"
            val currentUserName = prefs.getString("user_name", "Родитель") ?: "Родитель"

            val contentText = when (currentAccountType) {
                "child" -> {
                    // Ребёнок — просто лекарство и время
                    "$medicineName в $time"
                }
                "parent" -> {
                    if (ownerId == -1) {
                        // Напоминание для родителя
                        "$medicineName для Вас в $time"
                    } else {
                        // Напоминание для ребёнка
                        val ownerName = childDao.getChildById(ownerId)?.name ?: "Ребёнок"
                        "$medicineName для $ownerName в $time"
                    }
                }
                else -> "$medicineName в $time"
            }

            withContext(Dispatchers.Main) {
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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
                    .setContentText(contentText)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()

                notificationManager.notify(reminderId, notification)

                // Перезапланируем на следующую неделю
                ReminderScheduler.scheduleWeeklyReminder(
                    context,
                    reminderId,
                    dayOfWeek,
                    time,
                    medicineName,
                    ownerId
                )
            }
        }
    }
}
