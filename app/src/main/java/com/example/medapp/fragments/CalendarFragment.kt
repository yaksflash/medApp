package com.example.medapp.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.medapp.R
import com.example.medapp.data.AppDatabase
import com.example.medapp.models.Reminder
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
        val currentUser = prefs.getString("user_name", "") ?: ""

        lifecycleScope.launch {
            val dao = AppDatabase.getDatabase(requireContext()).reminderDao()
            val reminders: List<Reminder> = withContext(Dispatchers.IO) {
                dao.getAllForUser(currentUser)
            }
            displayReminders(reminders)
        }
    }

    private fun displayReminders(reminders: List<Reminder>) {
        calendarContainer.removeAllViews()
        val dao = AppDatabase.getDatabase(requireContext()).reminderDao()

        for ((dayNum, dayName) in daysOfWeek) {
            val dayReminders = reminders.filter { it.dayOfWeek == dayNum }
            if (dayReminders.isEmpty()) continue

            val dayTextView = TextView(requireContext()).apply {
                text = dayName
                textSize = 18f
                setPadding(0, 16, 0, 8)
            }
            calendarContainer.addView(dayTextView)

            for (r in dayReminders) {
                val reminderLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                }

                val reminderText = TextView(requireContext()).apply {
                    text = "${r.medicineName} - ${r.time}"
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                    setPadding(16, 4, 0, 4)
                }

                val btnDelete = Button(requireContext()).apply {
                    text = "-"
                    setOnClickListener {
                        lifecycleScope.launch {
                            dao.delete(r)
                            calendarContainer.removeView(reminderLayout)
                        }
                    }
                }

                reminderLayout.addView(reminderText)
                reminderLayout.addView(btnDelete)
                calendarContainer.addView(reminderLayout)
            }
        }
    }
}
