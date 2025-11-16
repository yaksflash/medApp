package com.example.medapp.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.medapp.data.AppDatabase
import kotlinx.coroutines.runBlocking

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val medicine = inputData.getString("medicine") ?: return Result.failure()
        val ownerId = inputData.getInt("ownerId", -1)

        // Получаем имя пользователя/ребёнка через DAO
        val ownerName = runBlocking {
            if (ownerId == -1) "Родитель"
            else AppDatabase.getDatabase(applicationContext).childDao().getChildById(ownerId)?.name ?: "Ребёнок"
        }

        val channelId = "medicine_reminder_channel"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Напоминания о лекарствах",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Напоминание о лекарстве")
            .setContentText("$ownerName нужно принять: $medicine")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)

        return Result.success()
    }
}
