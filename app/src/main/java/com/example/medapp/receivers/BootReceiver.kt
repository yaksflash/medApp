package com.example.medapp.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.medapp.data.AppDatabase
import com.example.medapp.utils.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CoroutineScope(Dispatchers.IO).launch {
                val dao = AppDatabase.getDatabase(context).reminderDao()
                val reminders = dao.getAll()

                reminders.forEach { reminder ->
                    // передаём id владельца вместо user
                    ReminderScheduler.scheduleWeeklyReminder(
                        context = context,
                        reminderId = reminder.id,
                        dayOfWeek = reminder.dayOfWeek,
                        time = reminder.time,
                        medicineName = reminder.medicineName,
                        ownerId = reminder.ownerId,
                        note = reminder.note // добавляем заметку
                    )
                }
            }
        }
    }
}
