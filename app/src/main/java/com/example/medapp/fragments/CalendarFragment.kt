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
import com.example.medapp.repositories.MedicineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import android.widget.EditText

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
        val accountType = prefs.getString("account_type", "child") ?: "child"

        lifecycleScope.launch {
            val reminderDao = AppDatabase.getDatabase(requireContext()).reminderDao()
            val childDao = AppDatabase.getDatabase(requireContext()).childDao()
            val reminders = withContext(Dispatchers.IO) { reminderDao.getAllForOwner(currentOwnerId) }
            val medicines = MedicineRepository.loadMedicines(requireContext())

            displayReminders(reminders, childDao, accountType, currentOwnerId, medicines)
        }
    }

    private fun displayReminders(
        reminders: List<Reminder>,
        childDao: com.example.medapp.data.ChildDao,
        accountType: String,
        currentUserId: Int,
        medicines: List<com.example.medapp.models.Medicine>
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
                    val tv = TextView(requireContext()).apply {
                        text = "${reminder.medicineName} - ${reminder.time}"
                        setPadding(16, 8, 16, 8)
                        setBackgroundResource(R.drawable.reminder_item_bg)
                        isClickable = true
                        isFocusable = true
                    }

                    tv.setOnClickListener {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val prefs = requireContext().getSharedPreferences("user_data", 0)
                            val birthdate = if (reminder.ownerId == currentUserId) {
                                prefs.getString("user_birthdate", null)
                            } else {
                                childDao.getChildById(reminder.ownerId)?.birthDate
                            }

                            val age = birthdate?.let {
                                val parts = it.split("/").map { it.toInt() }
                                val today = Calendar.getInstance()
                                var a = today.get(Calendar.YEAR) - parts[2]
                                if (today.get(Calendar.MONTH) + 1 < parts[1] ||
                                    (today.get(Calendar.MONTH) + 1 == parts[1] && today.get(Calendar.DAY_OF_MONTH) < parts[0])
                                ) a -= 1
                                a
                            } ?: 0

                            val medicine = MedicineRepository.findMedicineByName(medicines, reminder.medicineName)
                            val instructionText = medicine?.let { MedicineRepository.getInstructionForAge(it, age) }.orEmpty()
                            val noteText = reminder.note?.takeIf { it.isNotBlank() } ?: "Заметка отсутствует"

                            withContext(Dispatchers.Main) {
                                if (accountType == "child") {
                                    // Ребенок видит только информацию
                                    val message = buildString {
                                        if (instructionText.isNotEmpty()) append("Инструкция: $instructionText\n\n")
                                        append("Заметка: $noteText")
                                    }
                                    AlertDialog.Builder(requireContext())
                                        .setTitle("${reminder.medicineName} - ${reminder.time}")
                                        .setMessage(message)
                                        .setPositiveButton("ОК", null)
                                        .show()
                                } else {
                                    // Родитель — редактирование
                                    showEditDialog(reminder, tv)
                                }
                            }
                        }
                    }

                    calendarContainer.addView(tv)
                }
            }
        }
    }

    private fun showEditDialog(reminder: Reminder, textView: TextView) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("${reminder.medicineName} - ${reminder.time}")

        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        val noteInput = EditText(requireContext()).apply {
            hint = "Заметка"
            setText(reminder.note)
        }
        layout.addView(noteInput)
        builder.setView(layout)

        builder.setNeutralButton("Изменить время") { _, _ ->
            val parts = reminder.time.split(":").map { it.toIntOrNull() ?: 0 }
            val hour = parts.getOrNull(0) ?: 0
            val minute = parts.getOrNull(1) ?: 0

            TimePickerDialog(requireContext(), { _, h, m ->
                val newTime = String.format("%02d:%02d", h, m)
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        ReminderScheduler.cancelReminder(requireContext(), reminder.id)
                        reminder.time = newTime
                        AppDatabase.getDatabase(requireContext()).reminderDao().update(reminder)
                    }
                    ReminderScheduler.scheduleWeeklyReminder(
                        context = requireContext(),
                        reminderId = reminder.id,
                        dayOfWeek = reminder.dayOfWeek,
                        time = reminder.time,
                        medicineName = reminder.medicineName,
                        ownerId = reminder.ownerId
                    )
                    textView.text = "${reminder.medicineName} - ${reminder.time}"
                }
            }, hour, minute, true).show()
        }

        builder.setPositiveButton("Сохранить заметку") { _, _ ->
            val newNote = noteInput.text.toString()
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    reminder.note = newNote
                    AppDatabase.getDatabase(requireContext()).reminderDao().update(reminder)
                }
            }
        }

        builder.setNegativeButton("Удалить") { _, _ ->
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(requireContext()).reminderDao().delete(reminder)
                }
                ReminderScheduler.cancelReminder(requireContext(), reminder.id)
                loadReminders()
            }
        }

        builder.setOnCancelListener { } // Можно оставить для отмены

        builder.show()
    }
}
