package com.example.medapp.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.medapp.data.AppDatabase
import com.example.medapp.models.MedicineEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra("reminderId", -1)
        val medicineName = intent.getStringExtra("medicineName")
        val ownerId = intent.getIntExtra("ownerId", -1)

        if (reminderId != -1 && medicineName != null) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(reminderId) // Скрываем уведомление

            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(context)
                val reminderDao = db.reminderDao()
                val eventDao = db.medicineEventDao()

                // 1. Обновляем статус напоминания
                val reminder = reminderDao.getReminderById(reminderId)
                if (reminder != null) {
                    reminder.isTaken = true
                    reminderDao.update(reminder)
                }

                // 2. Добавляем запись в историю
                val event = MedicineEvent(
                    medicineName = medicineName,
                    ownerId = ownerId,
                    dateTimestamp = System.currentTimeMillis(),
                    isTaken = true
                )
                eventDao.insert(event)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "$medicineName принято!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
