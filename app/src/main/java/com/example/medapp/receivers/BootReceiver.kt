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
                reminders.forEach { r ->
                    ReminderScheduler.scheduleWeeklyReminder(
                        context,
                        r.id,
                        r.dayOfWeek,
                        r.time,
                        r.medicineName,
                        r.user
                    )
                }
            }
        }
    }
}
