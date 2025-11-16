package com.example.medapp.fragments

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.medapp.R
import com.example.medapp.adapters.MedicineAdapter
import com.example.medapp.data.AppDatabase
import com.example.medapp.models.Medicine
import com.example.medapp.models.Reminder
import com.example.medapp.repositories.MedicineRepository
import com.example.medapp.utils.ReminderScheduler
import kotlinx.coroutines.launch
import java.util.*

class CatalogFragment : Fragment() {

    private lateinit var adapter: MedicineAdapter
    private lateinit var medicines: List<Medicine>
    private var userAge: Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_catalog, container, false)
    }

    private fun showMedicineDialog(selected: Medicine) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_medicine_details, null)

        val spinnerUser = dialogView.findViewById<Spinner>(R.id.spinnerUser)
        val daysContainer = dialogView.findViewById<LinearLayout>(R.id.daysContainer)

        val dayTimeMap = mutableMapOf<String, MutableList<String>>()
        val daysOfWeek = listOf("Понедельник","Вторник","Среда","Четверг","Пятница","Суббота","Воскресенье")

        val childrenDao = AppDatabase.getDatabase(requireContext()).childDao()
        val prefs = requireContext().getSharedPreferences("user_data", 0)
        val currentOwnerId = prefs.getInt("owner_id", -1)

        lifecycleScope.launch {
            val children = childrenDao.getAll()
            val owners = mutableListOf<Pair<String, Int>>() // Pair<name, ownerId>
            owners.add("Родитель" to -1)
            owners.addAll(children.map { it.name to it.id })

            val adapterSpinner = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, owners.map { it.first })
            adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerUser.adapter = adapterSpinner

            // Сохраняем список owners для использования при сохранении
            spinnerUser.tag = owners
        }

        // UI дни недели
        daysOfWeek.forEach { day ->
            val dayLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            val dayLabelLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            val dayText = TextView(requireContext()).apply {
                text = day
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btnAddTime = Button(requireContext()).apply { text = "Добавить время" }
            dayLabelLayout.addView(dayText)
            dayLabelLayout.addView(btnAddTime)
            dayLayout.addView(dayLabelLayout)

            val timesContainer = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            dayLayout.addView(timesContainer)

            btnAddTime.setOnClickListener {
                val now = Calendar.getInstance()
                TimePickerDialog(requireContext(),
                    { _, hour, minute ->
                        val timeStr = String.format("%02d:%02d", hour, minute)
                        val list = dayTimeMap.getOrPut(day) { mutableListOf() }
                        list.add(timeStr)

                        val timeView = TextView(requireContext()).apply {
                            text = timeStr
                            setPadding(16, 8, 16, 8)
                            setBackgroundResource(android.R.drawable.btn_default_small)
                            setOnClickListener {
                                list.remove(timeStr)
                                timesContainer.removeView(this)
                            }
                        }
                        timesContainer.addView(timeView)
                    },
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE),
                    true
                ).show()
            }

            daysContainer.addView(dayLayout)
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(selected.name)
            .setView(dialogView)
            .setPositiveButton("Сохранить") { dialog, _ ->
                val dao = AppDatabase.getDatabase(requireContext()).reminderDao()
                lifecycleScope.launch {
                    val ownersList = spinnerUser.tag as List<Pair<String, Int>>
                    val selectedPosition = spinnerUser.selectedItemPosition
                    val selectedOwner = ownersList[selectedPosition]

                    for ((dayName, times) in dayTimeMap) {
                        val dayOfWeek = when(dayName) {
                            "Понедельник" -> 1
                            "Вторник" -> 2
                            "Среда" -> 3
                            "Четверг" -> 4
                            "Пятница" -> 5
                            "Суббота" -> 6
                            "Воскресенье" -> 7
                            else -> 1
                        }

                        for (time in times) {
                            val reminder = Reminder(
                                medicineName = selected.name,
                                dayOfWeek = dayOfWeek,
                                time = time,
                                ownerId = selectedOwner.second
                            )
                            val id = dao.insert(reminder).toInt()
                            ReminderScheduler.scheduleWeeklyReminder(
                                context = requireContext(),
                                reminderId = id,
                                dayOfWeek = reminder.dayOfWeek,
                                time = reminder.time,
                                medicineName = reminder.medicineName,
                                ownerId = reminder.ownerId
                            )
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("user_data", 0)
        val birthdate = prefs.getString("user_birthdate", "01/01/2010")
        val parts = birthdate!!.split("/").map { it.toInt() }
        val day = parts[0]; val month = parts[1]; val year = parts[2]

        val today = Calendar.getInstance()
        var age = today.get(Calendar.YEAR) - year
        if (today.get(Calendar.MONTH) + 1 < month ||
            (today.get(Calendar.MONTH) + 1 == month && today.get(Calendar.DAY_OF_MONTH) < day)) age -= 1
        userAge = age

        val rv = view.findViewById<RecyclerView>(R.id.rvMedicines)
        val searchInput = view.findViewById<EditText>(R.id.searchInput)

        medicines = MedicineRepository.loadMedicines(requireContext())
        adapter = MedicineAdapter(medicines) { selected -> showMedicineDialog(selected) }

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val filtered = medicines.filter { it.name.contains(s.toString(), ignoreCase = true) }
                adapter.updateList(filtered)
            }
        })
    }
}
