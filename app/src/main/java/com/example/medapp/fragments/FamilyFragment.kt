package com.example.medapp.fragments

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
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
import com.example.medapp.utils.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class FamilyFragment : Fragment() {

    private lateinit var rvChildren: RecyclerView
    private lateinit var btnAddChild: Button
    private lateinit var childrenAdapter: ChildrenAdapter
    private var childrenList = mutableListOf<Child>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_family, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvChildren = view.findViewById(R.id.rvChildren)
        btnAddChild = view.findViewById(R.id.btnAddChild)

        childrenAdapter = ChildrenAdapter(
            childrenList,
            onItemClick = { child -> showChildReminders(child) },
            onDeleteClick = { child -> confirmDeleteChild(child) },
            onQRClick = { child -> showChildQR(child) }
        )

        rvChildren.layoutManager = LinearLayoutManager(requireContext())
        rvChildren.adapter = childrenAdapter

        loadChildren()

        btnAddChild.setOnClickListener { showAddChildDialog() }
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

    private fun showAddChildDialog() {
        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }

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

    private fun showChildReminders(child: Child) {
        val reminderDao = AppDatabase.getDatabase(requireContext()).reminderDao()
        lifecycleScope.launch {
            val reminders = withContext(Dispatchers.IO) { reminderDao.getAllForOwner(child.id) }
            val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }

            val grouped = reminders.groupBy { it.dayOfWeek }
            val days = listOf(
                "Понедельник","Вторник","Среда","Четверг","Пятница","Суббота","Воскресенье"
            )

            days.forEachIndexed { index, day ->
                val dayReminders = grouped[index + 1] ?: emptyList()
                if (dayReminders.isNotEmpty()) {
                    val dayText = TextView(requireContext()).apply {
                        text = day
                        textSize = 18f
                    }
                    layout.addView(dayText)

                    dayReminders.forEach { reminder ->
                        val reminderTextView = TextView(requireContext()).apply {
                            text = "${reminder.medicineName} - ${reminder.time}" // заметка не отображаем
                            setPadding(16, 8, 16, 8)
                            setBackgroundResource(R.drawable.reminder_item_bg)
                            setOnClickListener {
                                showReminderEditDialog(reminder, this) // заметка редактируется только здесь
                            }
                        }
                        layout.addView(reminderTextView)
                    }
                }
            }

            AlertDialog.Builder(requireContext())
                .setTitle("${child.name} (${getAge(child.birthDate)} лет)")
                .setView(layout)
                .setPositiveButton("Закрыть", null)
                .show()
        }
    }

    private fun showReminderEditDialog(reminder: Reminder, textView: TextView) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("${reminder.medicineName} - ${reminder.time}")

        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        val noteInput = EditText(requireContext()).apply {
            hint = "Заметка"
            setText(reminder.note)
        }
        layout.addView(noteInput)
        builder.setView(layout)

        // Изменить время
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

        // Сохранить заметку
        builder.setPositiveButton("Сохранить заметку") { _, _ ->
            val newNote = noteInput.text.toString()
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    reminder.note = newNote
                    AppDatabase.getDatabase(requireContext()).reminderDao().update(reminder)
                }
            }
        }

        // Удалить напоминание
        builder.setNegativeButton("Удалить") { _, _ ->
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(requireContext()).reminderDao().delete(reminder)
                    ReminderScheduler.cancelReminder(requireContext(), reminder.id)
                }
                (textView.parent as? LinearLayout)?.removeView(textView)
            }
        }

        // Стандартная отмена (по кнопке назад или тап вне диалога)
        builder.setOnCancelListener {
            // Просто закрываем диалог
        }

        builder.show()
    }




    private fun getAge(birthDate: String): Int {
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
    }

    // ---------------------- QR ----------------------
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
            .setTitle("QR для синхронизации")
            .setView(imageView)
            .setPositiveButton("Закрыть", null)
            .show()
    }
}
