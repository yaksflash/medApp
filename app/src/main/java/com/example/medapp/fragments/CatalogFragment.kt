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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.medapp.R
import com.example.medapp.adapters.MedicineAdapter
import com.example.medapp.data.AppDatabase
import com.example.medapp.models.Medicine
import com.example.medapp.models.Reminder
import com.example.medapp.models.Child
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

    private fun showMedicineDialog(selectedName: String?) {
        val isCustom = selectedName == null
        val dialogLayoutRes = if (isCustom) R.layout.dialog_medicine_custom else R.layout.dialog_medicine_details
        val dialogView = LayoutInflater.from(requireContext()).inflate(dialogLayoutRes, null)

        val spinnerUser = dialogView.findViewById<Spinner>(R.id.spinnerUser)
        val daysContainer = dialogView.findViewById<LinearLayout>(R.id.daysContainer)
        val tvInstruction = dialogView.findViewById<TextView>(R.id.tvInstruction)
        val customNameField = if (isCustom) dialogView.findViewById<EditText>(R.id.etCustomName) else null

        val dayTimeMap = mutableMapOf<String, MutableList<String>>()
        val daysOfWeek = listOf("Понедельник","Вторник","Среда","Четверг","Пятница","Суббота","Воскресенье")

        val childrenDao = AppDatabase.getDatabase(requireContext()).childDao()
        val reminderDao = AppDatabase.getDatabase(requireContext()).reminderDao()

        lifecycleScope.launch {
            val children = childrenDao.getAll()
            val owners = mutableListOf<Pair<String, Int>>()
            owners.add("Родитель" to -1)
            owners.addAll(children.map { it.name to it.id })

            val adapterSpinner = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, owners.map { it.first })
            adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerUser.adapter = adapterSpinner
            spinnerUser.tag = owners

            spinnerUser.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selectedOwner = owners[position]
                    val age = if (selectedOwner.second == -1) {
                        userAge
                    } else {
                        val child = children.find { it.id == selectedOwner.second }
                        if (child != null) {
                            val parts = child.birthDate.split("/").map { it.toInt() }
                            var ageCalc = Calendar.getInstance().get(Calendar.YEAR) - parts[2]
                            if (Calendar.getInstance().get(Calendar.MONTH) + 1 < parts[1] ||
                                (Calendar.getInstance().get(Calendar.MONTH) + 1 == parts[1] &&
                                        Calendar.getInstance().get(Calendar.DAY_OF_MONTH) < parts[0])) ageCalc -= 1
                            ageCalc
                        } else 0
                    }

                    // Получаем инструкцию лекарства по возрасту
                    val medName = if (isCustom) customNameField?.text?.toString()?.trim() ?: "" else selectedName ?: ""
                    val med = MedicineRepository.findMedicineByName(medicines, medName)
                    tvInstruction.text = med?.let { MedicineRepository.getInstructionForAge(it, age) } ?: "Инструкция недоступна"
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    tvInstruction.text = "Инструкция появится здесь"
                }
            }
        }

        // UI дни недели и добавление времени
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
            .setTitle(if (isCustom) "Добавить своё упоминание" else (selectedName ?: "Напоминание"))
            .setView(dialogView)
            .setPositiveButton("Сохранить") { dialog, _ ->
                lifecycleScope.launch {
                    val ownersList = spinnerUser.tag as? List<Pair<String, Int>> ?: return@launch
                    val selectedOwner = ownersList.getOrNull(spinnerUser.selectedItemPosition) ?: ("Родитель" to -1)

                    val medicineTitle = if (isCustom) {
                        val txt = customNameField?.text?.toString()?.trim()
                        if (txt.isNullOrEmpty()) "Без названия" else txt
                    } else {
                        selectedName ?: "Без названия"
                    }

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
                                medicineName = medicineTitle,
                                dayOfWeek = dayOfWeek,
                                time = time,
                                ownerId = selectedOwner.second
                            )
                            val id = reminderDao.insert(reminder).toInt()
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
        val addCustom = view.findViewById<TextView>(R.id.btnAddCustom)

        medicines = MedicineRepository.loadMedicines(requireContext())
        adapter = MedicineAdapter(medicines) { selected ->
            showMedicineDialog(selected.name)
        }

        rv.layoutManager = GridLayoutManager(requireContext(), 2)
        rv.adapter = adapter

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val filtered = medicines.filter { it.name.contains(s.toString(), ignoreCase = true) }
                adapter.updateList(filtered)
            }
        })

        addCustom.setOnClickListener {
            showMedicineDialog(null)
        }
    }
}
