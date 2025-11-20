package com.example.medapp.fragments

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
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
        val noteField = dialogView.findViewById<EditText>(R.id.etNote)

        // --- Создание переключателя режимов (Ежедневно / По дням) ---
        val modeRadioGroup = RadioGroup(requireContext()).apply {
            orientation = RadioGroup.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 16)
        }
        val rbDaily = RadioButton(requireContext()).apply {
            text = "Ежедневно"
            id = View.generateViewId()
            isChecked = true 
        }
        val rbWeekly = RadioButton(requireContext()).apply {
            text = "По дням"
            id = View.generateViewId()
        }
        modeRadioGroup.addView(rbDaily)
        modeRadioGroup.addView(rbWeekly)

        (daysContainer.parent as ViewGroup).addView(modeRadioGroup, (daysContainer.parent as ViewGroup).indexOfChild(daysContainer))

        // --- Структуры данных ---
        val dayTimeMap = mutableMapOf<String, MutableList<String>>()
        val dailyTimesList = mutableListOf<String>()
        val daysOfWeek = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")

        // --- Вспомогательная функция для создания чипса времени ---
        fun createTimeChip(timeStr: String, onDelete: () -> Unit): View {
            val chipLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(android.R.drawable.btn_default_small)
                setPadding(16, 8, 16, 8)
                
                val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                params.setMargins(0, 0, 16, 0)
                layoutParams = params

                setOnClickListener { onDelete() }
            }

            val tvTime = TextView(requireContext()).apply {
                text = timeStr
                textSize = 14f
                setPadding(0, 0, 8, 0) // Отступ от текста до иконки
            }

            val iconClose = ImageView(requireContext()).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                // Делаем иконку немного прозрачной или серой, если нужно, но стандартная обычно ок
                layoutParams = LinearLayout.LayoutParams(40, 40) // Небольшой размер
            }

            chipLayout.addView(tvTime)
            chipLayout.addView(iconClose)
            return chipLayout
        }

        // --- Функции отрисовки UI ---

        fun setupDailyUI() {
            daysContainer.removeAllViews()

            val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            val btnAddDailyTime = Button(requireContext()).apply { text = "Добавить время приема" }
            val timesContainer = LinearLayout(requireContext()).apply { 
                orientation = LinearLayout.HORIZONTAL 
                // Разрешаем перенос строк если их много? LinearLayout не умеет wrap_content multiline, 
                // но пока оставим так, для простоты горизонтальный скролл можно добавить потом если нужно
            } 
            // Чтобы список времен можно было скроллить горизонтально, обернем его
            val scrollContainer = HorizontalScrollView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            scrollContainer.addView(timesContainer)

            fun refreshDailyTimes() {
                timesContainer.removeAllViews()
                dailyTimesList.forEach { timeStr ->
                    val chip = createTimeChip(timeStr) {
                        dailyTimesList.remove(timeStr)
                        refreshDailyTimes()
                    }
                    timesContainer.addView(chip)
                }
            }

            btnAddDailyTime.setOnClickListener {
                val now = Calendar.getInstance()
                TimePickerDialog(requireContext(),
                    { _, hour, minute ->
                        val timeStr = String.format("%02d:%02d", hour, minute)
                        if (!dailyTimesList.contains(timeStr)) {
                            dailyTimesList.add(timeStr)
                            refreshDailyTimes()
                        }
                    },
                    now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true
                ).show()
            }

            layout.addView(btnAddDailyTime)
            layout.addView(scrollContainer)
            daysContainer.addView(layout)
            refreshDailyTimes()
        }

        fun setupWeeklyUI() {
            daysContainer.removeAllViews()
            daysOfWeek.forEach { day ->
                val dayLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
                val dayLabelLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
                val dayText = TextView(requireContext()).apply {
                    text = day
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val btnAddTime = Button(requireContext()).apply { text = "+" } // Компактная кнопка
                
                val timesContainer = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
                val scrollContainer = HorizontalScrollView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                scrollContainer.addView(timesContainer)

                dayLabelLayout.addView(dayText)
                dayLabelLayout.addView(btnAddTime)
                dayLayout.addView(dayLabelLayout)
                dayLayout.addView(scrollContainer)

                fun refreshDayTimes(d: String, container: LinearLayout) {
                    container.removeAllViews()
                    dayTimeMap[d]?.forEach { tStr ->
                        val chip = createTimeChip(tStr) {
                            dayTimeMap[d]?.remove(tStr)
                            refreshDayTimes(d, container)
                        }
                        container.addView(chip)
                    }
                }

                btnAddTime.setOnClickListener {
                    val now = Calendar.getInstance()
                    TimePickerDialog(requireContext(),
                        { _, hour, minute ->
                            val timeStr = String.format("%02d:%02d", hour, minute)
                            val list = dayTimeMap.getOrPut(day) { mutableListOf() }
                            list.add(timeStr)
                            refreshDayTimes(day, timesContainer)
                        },
                        now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true
                    ).show()
                }

                refreshDayTimes(day, timesContainer)
                daysContainer.addView(dayLayout)
            }
        }

        modeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == rbDaily.id) setupDailyUI() else setupWeeklyUI()
        }

        setupDailyUI() // Default

        // --- Spinner ---
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
                    val medName = if (isCustom) customNameField?.text?.toString()?.trim() ?: "" else selectedName ?: ""
                    val med = MedicineRepository.findMedicineByName(medicines, medName)
                    tvInstruction.text = med?.let { MedicineRepository.getInstructionForAge(it, age) } ?: "Инструкция недоступна"
                }
                override fun onNothingSelected(parent: AdapterView<*>?) { tvInstruction.text = "Инструкция появится здесь" }
            }
        }

        // --- Диалог ---
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(if (isCustom) "Добавить своё напоминание" else (selectedName ?: "Напоминание"))
            .setView(dialogView)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton("Отмена", null)
            .create()

        dialog.show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            lifecycleScope.launch {
                val ownersList = spinnerUser.tag as? List<Pair<String, Int>> ?: return@launch
                val selectedOwner = ownersList.getOrNull(spinnerUser.selectedItemPosition) ?: ("Родитель" to -1)

                val medicineTitle = if (isCustom) {
                    val txt = customNameField?.text?.toString()?.trim()
                    if (txt.isNullOrEmpty()) {
                        Toast.makeText(requireContext(), "Пожалуйста, введите название лекарства", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    txt
                } else {
                    selectedName ?: "Без названия"
                }

                var hasTime = false
                if (rbDaily.isChecked) {
                    if (dailyTimesList.isNotEmpty()) hasTime = true
                } else {
                    if (dayTimeMap.any { it.value.isNotEmpty() }) hasTime = true
                }

                if (!hasTime) {
                    Toast.makeText(requireContext(), "Пожалуйста, добавьте хотя бы одно время приема", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val noteText = noteField?.text?.toString()?.trim()

                if (rbDaily.isChecked) {
                    for (dayIndex in 1..7) {
                        for (time in dailyTimesList) {
                            val reminder = Reminder(
                                medicineName = medicineTitle,
                                dayOfWeek = dayIndex,
                                time = time,
                                ownerId = selectedOwner.second,
                                note = if (noteText.isNullOrEmpty()) null else noteText
                            )
                            val id = reminderDao.insert(reminder).toInt()
                            ReminderScheduler.scheduleWeeklyReminder(
                                context = requireContext(),
                                reminderId = id,
                                dayOfWeek = reminder.dayOfWeek,
                                time = reminder.time,
                                medicineName = reminder.medicineName,
                                ownerId = reminder.ownerId,
                                note = reminder.note
                            )
                        }
                    }
                } else {
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
                                ownerId = selectedOwner.second,
                                note = if (noteText.isNullOrEmpty()) null else noteText
                            )
                            val id = reminderDao.insert(reminder).toInt()
                            ReminderScheduler.scheduleWeeklyReminder(
                                context = requireContext(),
                                reminderId = id,
                                dayOfWeek = reminder.dayOfWeek,
                                time = reminder.time,
                                medicineName = reminder.medicineName,
                                ownerId = reminder.ownerId,
                                note = reminder.note
                            )
                        }
                    }
                }
                dialog.dismiss()
            }
        }
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
