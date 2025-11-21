package com.example.medapp.fragments

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.medapp.R
import com.example.medapp.adapters.ChildrenAdapter
import com.example.medapp.data.AppDatabase
import com.example.medapp.models.Child
import com.example.medapp.models.Reminder
import com.example.medapp.models.MedicineEvent
import com.example.medapp.utils.ReminderScheduler
import com.example.medapp.repositories.MedicineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class CalendarFragment : Fragment() {

    // --- UI Календаря ---
    private lateinit var calendarContainer: LinearLayout
    private lateinit var viewCalendar: LinearLayout
    private lateinit var btnOpenFamily: Button

    // --- UI Семьи ---
    private lateinit var viewFamily: LinearLayout
    private lateinit var btnCloseFamily: ImageView
    private lateinit var rvChildren: RecyclerView
    private lateinit var btnAddChild: Button
    
    private lateinit var childrenAdapter: ChildrenAdapter
    private var childrenList = mutableListOf<Child>()

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
        viewCalendar = view.findViewById(R.id.viewCalendar)
        btnOpenFamily = view.findViewById(R.id.btnOpenFamily)

        viewFamily = view.findViewById(R.id.viewFamily)
        btnCloseFamily = view.findViewById(R.id.btnCloseFamily)
        rvChildren = view.findViewById(R.id.rvChildren)
        btnAddChild = view.findViewById(R.id.btnAddChild)

        btnOpenFamily.setOnClickListener {
            viewCalendar.visibility = View.GONE
            viewFamily.visibility = View.VISIBLE
            loadChildren()
        }

        btnCloseFamily.setOnClickListener {
            viewFamily.visibility = View.GONE
            viewCalendar.visibility = View.VISIBLE
            loadReminders()
        }

        childrenAdapter = ChildrenAdapter(
            childrenList,
            onItemClick = { child -> showChildOptionsDialog(child) },
            onDeleteClick = { child -> confirmDeleteChild(child) },
            onQRClick = { child -> showChildQR(child) }
        )
        rvChildren.layoutManager = LinearLayoutManager(requireContext())
        rvChildren.adapter = childrenAdapter

        btnAddChild.setOnClickListener { showAddChildDialog() }

        loadReminders()
    }

    private fun loadReminders() {
        val prefs = requireContext().getSharedPreferences("user_data", 0)
        val currentOwnerId = prefs.getInt("owner_id", -1)
        val accountType = prefs.getString("account_type", "child") ?: "child"

        if (accountType == "child") {
            btnOpenFamily.visibility = View.GONE
        } else {
            btnOpenFamily.visibility = View.VISIBLE
        }

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
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, 16, 0, 8)
                }
                calendarContainer.addView(dayTextView)

                for (reminder in dayReminders) {
                    val itemLayout = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setBackgroundResource(R.drawable.reminder_item_bg)
                        setPadding(32, 24, 32, 24)
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.setMargins(0, 16, 0, 16)
                        layoutParams = params
                    }

                    val tv = TextView(requireContext()).apply {
                        text = "${reminder.medicineName} - ${reminder.time}"
                        textSize = 20f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    // ЧЕКБОКС УДАЛЕН ПО ЗАПРОСУ

                    val editIcon = ImageView(requireContext()).apply {
                        setImageResource(R.drawable.pen)
                        layoutParams = LinearLayout.LayoutParams(80, 80)
                        setColorFilter(android.graphics.Color.DKGRAY)
                        setPadding(16, 0, 0, 0)
                    }

                    itemLayout.addView(tv)
                    // itemLayout.addView(checkBox) <-- удалено
                    itemLayout.addView(editIcon)

                    val clickListener = View.OnClickListener {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val prefs = requireContext().getSharedPreferences("user_data", 0)
                            val birthdate = if (reminder.ownerId == currentUserId) {
                                prefs.getString("user_birthdate", null)
                            } else {
                                childDao.getChildById(reminder.ownerId)?.birthDate
                            }

                            val age = birthdate?.let {
                                try {
                                    val parts = it.split("/").map { s -> s.toInt() }
                                    val today = Calendar.getInstance()
                                    var a = today.get(Calendar.YEAR) - parts[2]
                                    if (today.get(Calendar.MONTH) + 1 < parts[1] ||
                                        (today.get(Calendar.MONTH) + 1 == parts[1] && today.get(Calendar.DAY_OF_MONTH) < parts[0])
                                    ) a -= 1
                                    a
                                } catch (e: Exception) { 0 }
                            } ?: 0

                            val medicine = MedicineRepository.findMedicineByName(medicines, reminder.medicineName)
                            val instructionText = medicine?.let { MedicineRepository.getInstructionForAge(it, age) }.orEmpty()
                            val noteText = reminder.note?.takeIf { it.isNotBlank() } ?: "Заметка отсутствует"

                            withContext(Dispatchers.Main) {
                                if (accountType == "child") {
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
                                    showEditDialog(reminder, tv)
                                }
                            }
                        }
                    }

                    itemLayout.setOnClickListener(clickListener)
                    editIcon.setOnClickListener(clickListener)
                    tv.setOnClickListener(clickListener)

                    calendarContainer.addView(itemLayout)
                }
            }
        }
    }

    private fun showEditDialog(reminder: Reminder, textView: TextView) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("${reminder.medicineName} - ${reminder.time}")

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }
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
        builder.setOnCancelListener { }
        builder.show()
    }

    private fun loadChildren() {
        val dao = AppDatabase.getDatabase(requireContext()).childDao()
        lifecycleScope.launch {
            val children = withContext(Dispatchers.IO) { dao.getAll() }
            childrenList.clear()
            childrenList.addAll(children)
            childrenAdapter.notifyDataSetChanged()
        }
    }

    private fun showChildOptionsDialog(child: Child) {
        val options = arrayOf("Список напоминаний", "Статистика приёмов")
        AlertDialog.Builder(requireContext())
            .setTitle(child.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showChildReminders(child)
                    1 -> showChildStatistics(child)
                }
            }
            .show()
    }

    private fun showChildReminders(child: Child) {
        val reminderDao = AppDatabase.getDatabase(requireContext()).reminderDao()
        lifecycleScope.launch {
            val reminders = withContext(Dispatchers.IO) { reminderDao.getAllForOwner(child.id) }
            val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16) }
            val scroll = android.widget.ScrollView(requireContext())
            
            val grouped = reminders.groupBy { it.dayOfWeek }
            val days = listOf(
                "Понедельник","Вторник","Среда","Четверг","Пятница","Суббота","Воскресенье"
            )

            var hasItems = false
            days.forEachIndexed { index, day ->
                val dayReminders = grouped[index + 1] ?: emptyList()
                if (dayReminders.isNotEmpty()) {
                    hasItems = true
                    val dayText = TextView(requireContext()).apply {
                        text = day
                        textSize = 18f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setPadding(0, 16, 0, 8)
                    }
                    layout.addView(dayText)

                    dayReminders.forEach { reminder ->
                        val reminderTextView = TextView(requireContext()).apply {
                            text = "${reminder.medicineName} - ${reminder.time}"
                            textSize = 16f
                            setPadding(16, 12, 16, 12)
                            setBackgroundResource(R.drawable.reminder_item_bg)
                            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            params.setMargins(0, 0, 0, 8)
                            layoutParams = params
                            
                            setOnClickListener {
                                showEditDialog(reminder, this)
                            }
                        }
                        layout.addView(reminderTextView)
                    }
                }
            }
            
            if (!hasItems) {
                layout.addView(TextView(requireContext()).apply { text = "Напоминаний нет"; textSize = 16f })
            }

            scroll.addView(layout)

            AlertDialog.Builder(requireContext())
                .setTitle("Напоминания: ${child.name}")
                .setView(scroll)
                .setPositiveButton("Закрыть", null)
                .show()
        }
    }
    
    private fun showChildStatistics(child: Child) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val eventsDao = db.medicineEventDao()
            
            val uniqueMedicines = withContext(Dispatchers.IO) {
                eventsDao.getUniqueMedicinesForOwner(child.id)
            }
            val allEvents = withContext(Dispatchers.IO) {
                eventsDao.getAllForOwner(child.id)
            }

            val layout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
            }
            val list = ListView(requireContext())
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, uniqueMedicines)
            list.adapter = adapter
            list.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 800)
            
            layout.addView(list)

            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Статистика: ${child.name}")
                .setView(layout)
                .setPositiveButton("Закрыть", null)
                .create()

            list.setOnItemClickListener { _, _, position, _ ->
                val medName = uniqueMedicines[position]
                val medEvents = allEvents.filter { it.medicineName == medName }
                showCalendarForChild(medName, medEvents, child.id)
            }
            
            dialog.show()
        }
    }

    private fun showCalendarForChild(medicineName: String, events: List<MedicineEvent>, userId: Int) {
        val context = requireContext()
        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val calendar = Calendar.getInstance()
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val btnPrev = Button(context).apply { text = "<" }
        val btnNext = Button(context).apply { text = ">" }
        val tvMonthYear = TextView(context).apply { 
            textSize = 18f 
            setPadding(16, 0, 16, 0)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        headerLayout.addView(btnPrev); headerLayout.addView(tvMonthYear); headerLayout.addView(btnNext)
        dialogView.addView(headerLayout)

        val gridView = GridView(context).apply {
            numColumns = 7
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing = 4; verticalSpacing = 4
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 900)
        }
        dialogView.addView(gridView)

        val totalDoses = events.size
        val uniqueDays = events.map { 
            val c = Calendar.getInstance().apply { timeInMillis = it.dateTimestamp }
            "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
        }.distinct().size
        val tvSummary = TextView(context).apply {
            text = "Всего дней приема: $uniqueDays\nВсего принято доз: $totalDoses"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
            setTypeface(null, Typeface.BOLD)
        }
        dialogView.addView(tvSummary)

        fun updateCalendar() {
            val monthFormat = java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            tvMonthYear.text = monthFormat.format(calendar.time)
            val days = ArrayList<Date?>()
            val tempCal = calendar.clone() as Calendar
            tempCal.set(Calendar.DAY_OF_MONTH, 1)
            val firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)
            val shift = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2
            for (i in 0 until shift) days.add(null)
            val maxDay = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            for (i in 1..maxDay) { days.add(tempCal.time); tempCal.add(Calendar.DAY_OF_MONTH, 1) }

            gridView.adapter = object : ArrayAdapter<Date>(context, 0, days) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = (convertView as? TextView) ?: TextView(context).apply {
                        layoutParams = android.widget.AbsListView.LayoutParams(android.widget.AbsListView.LayoutParams.MATCH_PARENT, 130)
                        gravity = Gravity.CENTER
                        textSize = 16f
                        setTextColor(Color.BLACK)
                        maxLines = 2
                    }
                    val date = getItem(position)
                    if (date == null) { view.text = ""; view.background = null } else {
                        val calDay = Calendar.getInstance().apply { time = date }
                        val dayNumberStr = calDay.get(Calendar.DAY_OF_MONTH).toString()
                        val count = events.count { 
                            val eventCal = Calendar.getInstance().apply { timeInMillis = it.dateTimestamp }
                            eventCal.get(Calendar.YEAR) == calDay.get(Calendar.YEAR) && eventCal.get(Calendar.DAY_OF_YEAR) == calDay.get(Calendar.DAY_OF_YEAR)
                        }
                        if (count > 0) {
                            val text = "$dayNumberStr\n($count)"
                            val spannable = SpannableString(text)
                            spannable.setSpan(StyleSpan(Typeface.BOLD), 0, dayNumberStr.length, 0)
                            spannable.setSpan(RelativeSizeSpan(0.7f), dayNumberStr.length + 1, text.length, 0)
                            view.text = spannable
                            view.setBackgroundColor(Color.GREEN)
                        } else {
                            view.text = dayNumberStr
                            view.setBackgroundColor(Color.TRANSPARENT)
                        }
                    }
                    return view
                }
            }
        }
        updateCalendar()
        btnPrev.setOnClickListener { calendar.add(Calendar.MONTH, -1); updateCalendar() }
        btnNext.setOnClickListener { calendar.add(Calendar.MONTH, 1); updateCalendar() }

        AlertDialog.Builder(context)
            .setTitle("История: $medicineName")
            .setView(dialogView)
            .setPositiveButton("Закрыть", null)
            .setNegativeButton("Удалить историю") { _, _ ->
                AlertDialog.Builder(context)
                    .setTitle("Удаление истории")
                    .setMessage("Удалить историю приема?")
                    .setPositiveButton("Удалить") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            AppDatabase.getDatabase(context).medicineEventDao().deleteHistoryByName(userId, medicineName)
                            withContext(Dispatchers.Main) { Toast.makeText(context, "Удалено", Toast.LENGTH_SHORT).show(); showChildStatistics(Child(userId, "", "")) }
                        }
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
            .show()
    }

    private fun showAddChildDialog() {
        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16) }

        val nameInput = EditText(requireContext()).apply { hint = "Имя" }
        layout.addView(nameInput)

        val dobButton = Button(requireContext()).apply { text = "Выберите дату рождения" }
        layout.addView(dobButton)

        var selectedDate: String? = null

        dobButton.setOnClickListener {
            val today = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    selectedDate = String.format("%02d/%02d/%04d", dayOfMonth, month + 1, year)
                    dobButton.text = selectedDate
                },
                today.get(Calendar.YEAR),
                today.get(Calendar.MONTH),
                today.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Добавить ребенка")
            .setView(layout)
            .setPositiveButton("Добавить") { dialog, _ ->
                val name = nameInput.text.toString()
                if (name.isNotEmpty() && selectedDate != null) {
                    val child = Child(name = name, birthDate = selectedDate!!)
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            AppDatabase.getDatabase(requireContext()).childDao().insert(child)
                        }
                        loadChildren()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun confirmDeleteChild(child: Child) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить ребенка?")
            .setMessage("Вы уверены, что хотите удалить ${child.name} и все его напоминания?")
            .setPositiveButton("Удалить") { dialog, _ ->
                lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(requireContext())
                    withContext(Dispatchers.IO) {
                        db.reminderDao().deleteForOwner(child.id)
                        db.childDao().delete(child)
                    }
                    loadChildren()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun getAge(birthDate: String): Int {
        try {
            val parts = birthDate.split("/").map { it.toInt() }
            val day = parts[0]
            val month = parts[1]
            val year = parts[2]
            val today = Calendar.getInstance()
            var age = today.get(Calendar.YEAR) - year
            if (today.get(Calendar.MONTH) + 1 < month ||
                (today.get(Calendar.MONTH) + 1 == month && today.get(Calendar.DAY_OF_MONTH) < day)
            ) age -= 1
            return age
        } catch (e: Exception) { return 0 }
    }

    private fun showChildQR(child: Child) {
        val reminderDao = AppDatabase.getDatabase(requireContext()).reminderDao()
        lifecycleScope.launch {
            val reminders = withContext(Dispatchers.IO) { reminderDao.getAllForOwner(child.id) }
            val jsonMap = mapOf(
                "name" to child.name,
                "birthDate" to child.birthDate,
                "reminders" to reminders.map { r ->
                    mapOf(
                        "medicineName" to r.medicineName,
                        "time" to r.time,
                        "dayOfWeek" to r.dayOfWeek,
                        "note" to r.note
                    )
                }
            )
            val jsonString = com.google.gson.Gson().toJson(jsonMap)
            val qrBitmap = generateQR(jsonString)
            showQRDialog(qrBitmap)
        }
    }

    private fun generateQR(data: String): android.graphics.Bitmap {
        val size = 512
        val hints = mapOf(
            com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8",
            com.google.zxing.EncodeHintType.MARGIN to 1
        )

        val bitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(
            data,
            com.google.zxing.BarcodeFormat.QR_CODE,
            size,
            size,
            hints
        )

        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bmp
    }

    private fun showQRDialog(qr: android.graphics.Bitmap) {
        val imageView = ImageView(requireContext()).apply { setImageBitmap(qr) }
        AlertDialog.Builder(requireContext())
            .setTitle("QR-код для синхронизации")
            .setView(imageView)
            .setPositiveButton("Закрыть", null)
            .show()
    }
}
