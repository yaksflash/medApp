package com.example.medapp.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.medapp.data.AppDatabase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            GlobalScope.launch {
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
