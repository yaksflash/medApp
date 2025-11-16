package com.example.medapp.fragments

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.medapp.R
import com.example.medapp.data.AppDatabase
import com.example.medapp.models.Reminder
import com.example.medapp.utils.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CalendarFragment : Fragment() {

    private lateinit var calendarContainer: LinearLayout

    private val daysOfWeek = listOf(
        1 to "Понедельник",
        2 to "Вторник",
        3 to "Среда",
        4 to "Четверг",
        5 to "Пятница",
        6 to "Суббота",
        7 to "Воскресенье"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        calendarContainer = view.findViewById(R.id.calendarContainer)
        loadReminders()
    }

    private fun loadReminders() {
        val prefs = requireContext().getSharedPreferences("user_data", 0)
        val currentOwnerId = prefs.getInt("owner_id", -1)
        val accountType = prefs.getString("account_type", "child")

        lifecycleScope.launch {
            val reminderDao = AppDatabase.getDatabase(requireContext()).reminderDao()
            val childDao = AppDatabase.getDatabase(requireContext()).childDao()

            val reminders = withContext(Dispatchers.IO) {
                reminderDao.getAllForOwner(currentOwnerId)
            }

            displayReminders(reminders, childDao, accountType ?: "child")
        }
    }

    private fun displayReminders(
        reminders: List<Reminder>,
        childDao: com.example.medapp.data.ChildDao,
        accountType: String
    ) {
        calendarContainer.removeAllViews()

        lifecycleScope.launch {
            for ((dayNum, dayName) in daysOfWeek) {
                val dayReminders = reminders.filter { it.dayOfWeek == dayNum }
                if (dayReminders.isEmpty()) continue

                val dayTextView = TextView(requireContext()).apply {
                    text = dayName
                    textSize = 18f
                    setPadding(0, 16, 0, 8)
                }
                calendarContainer.addView(dayTextView)

                for (reminder in dayReminders) {
                    val ownerName = withContext(Dispatchers.IO) {
                        if (reminder.ownerId == -1) "Родитель"
                        else childDao.getChildById(reminder.ownerId)?.name ?: "Ребёнок"
                    }

                    val reminderTextView = TextView(requireContext()).apply {
                        text = "${reminder.medicineName} - ${reminder.time}"
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        setPadding(16, 8, 16, 8)
                        setBackgroundResource(R.drawable.reminder_item_bg)
                    }

                    // Если аккаунт не child, включаем редактирование
                    if (accountType != "child") {
                        reminderTextView.setOnClickListener {
                            showEditDialog(reminder, reminderTextView)
                        }
                    }

                    calendarContainer.addView(reminderTextView)
                }
            }
        }
    }

    private fun showEditDialog(reminder: Reminder, textView: TextView) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("${reminder.medicineName} - ${reminder.time}")
        builder.setMessage("Выберите действие:")

        builder.setNeutralButton("Изменить время") { _, _ ->
            val parts = reminder.time.split(":").map { it.toIntOrNull() ?: 0 }
            val hour = parts.getOrNull(0) ?: 0
            val minute = parts.getOrNull(1) ?: 0

            TimePickerDialog(requireContext(), { _, h, m ->
                val newTime = String.format("%02d:%02d", h, m)
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        // Отменяем старое уведомление
                        ReminderScheduler.cancelReminder(requireContext(), reminder.id)

                        // Сохраняем новое время в базе
                        reminder.time = newTime
                        AppDatabase.getDatabase(requireContext()).reminderDao().update(reminder)
                    }
                    // Ставим новое уведомление
                    ReminderScheduler.scheduleWeeklyReminder(
                        context = requireContext(),
                        reminderId = reminder.id,
                        dayOfWeek = reminder.dayOfWeek,
                        time = reminder.time,
                        medicineName = reminder.medicineName,
                        ownerId = reminder.ownerId
                    )
                    // Обновляем UI
                    textView.text = "${reminder.medicineName} - ${reminder.time}"
                }
            }, hour, minute, true).show()
        }

        builder.setNegativeButton("Удалить") { _, _ ->
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    // Удаляем напоминание из базы
                    AppDatabase.getDatabase(requireContext()).reminderDao().delete(reminder)
                }
                // Отменяем уведомление
                ReminderScheduler.cancelReminder(requireContext(), reminder.id)
                // Обновляем UI
                loadReminders()
            }
        }

        builder.setPositiveButton("Отмена", null)
        builder.show()
    }
}
