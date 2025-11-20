package com.example.medapp.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.medapp.R
import com.example.medapp.data.AppDatabase
import com.example.medapp.models.Reminder
import com.example.medapp.models.QRData
import com.example.medapp.models.QRReminder
import com.example.medapp.utils.ReminderScheduler
import com.example.medapp.repositories.MedicineRepository
import com.google.gson.GsonBuilder
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var btnScanQR: Button
    private lateinit var container: LinearLayout
    private lateinit var btnHelp: ImageView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnScanQR = view.findViewById(R.id.btnScanQR)
        container = view.findViewById(R.id.containerTodayReminders)
        btnHelp = view.findViewById(R.id.btnHelp)

        val prefs = requireContext().getSharedPreferences("user_data", 0)
        val accountType = prefs.getString("account_type", "child")

        btnScanQR.visibility = if (accountType == "child") View.VISIBLE else View.GONE
        btnScanQR.setOnClickListener { startQRScanner() }
        
        // Обработчик нажатия на кнопку помощи
        btnHelp.setOnClickListener {
            showHelpDialog()
        }

        loadTodayReminders()
    }
    
    private fun showHelpDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Помощь")
            .setMessage("На этом экране отображаются напоминания о приеме лекарств на сегодня.\n\n" +
                        "Если вы Ребенок: нажмите 'QR-синхронизация', чтобы обновить расписание от родителя.\n\n" +
                        "Нажмите на напоминание, чтобы увидеть инструкцию и заметку.")
            .setPositiveButton("Закрыть", null)
            .show()
    }

    private fun startQRScanner() {
        val integrator = IntentIntegrator.forSupportFragment(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt("Сканируйте QR код родителя")
        integrator.setCameraId(0)
        integrator.setBeepEnabled(true)
        integrator.setBarcodeImageEnabled(true)
        integrator.setOrientationLocked(true)
        qrLauncher.launch(integrator.createScanIntent())
    }

    private val qrLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            val intentResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
            if (intentResult != null && intentResult.contents != null) {
                handleQRData(intentResult.contents)
            } else {
                AlertDialog.Builder(requireContext())
                    .setTitle("Ошибка")
                    .setMessage("QR код не распознан")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }

    private fun handleQRData(json: String) {
        try {
            val gson = GsonBuilder().setLenient().create()
            val qrData = gson.fromJson(json, QRData::class.java)
            val remindersList = qrData.reminders

            val prefs = requireContext().getSharedPreferences("user_data", 0)
            val currentChildId = prefs.getInt("owner_id", -1)

            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(requireContext())
                val reminderDao = db.reminderDao()

                // Отменяем старые уведомления
                val oldReminders = reminderDao.getAllForOwner(currentChildId)
                oldReminders.forEach { ReminderScheduler.cancelReminder(requireContext(), it.id) }

                // Удаляем старые напоминания
                reminderDao.deleteForOwner(currentChildId)

                // Сохраняем новые из QR
                remindersList.forEach { r: QRReminder ->
                    val reminder = Reminder(
                        ownerId = currentChildId,
                        medicineName = r.medicineName,
                        time = r.time,
                        dayOfWeek = r.dayOfWeek,
                        note = r.note
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

                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Синхронизация завершена")
                        .setMessage("Напоминания успешно обновлены")
                        .setPositiveButton("OK", null)
                        .show()
                    loadTodayReminders()
                }
            }
        } catch (e: Exception) {
            AlertDialog.Builder(requireContext())
                .setTitle("Ошибка")
                .setMessage("Не удалось обработать QR: ${e.message}")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun loadTodayReminders() {
        val prefs = requireContext().getSharedPreferences("user_data", 0)
        val accountType = prefs.getString("account_type", "child")
        val currentUserId = prefs.getInt("owner_id", -1)

        container.removeAllViews()

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val childDao = db.childDao()
            val reminderDao = db.reminderDao()

            val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            val adjustedDay = if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1

            val itemsToShow = mutableListOf<Pair<String, List<Reminder>>>()

            if (accountType == "child") {
                val reminders = reminderDao.getAllForOwner(currentUserId)
                    .filter { it.dayOfWeek == adjustedDay }

                // Добавляем, только если список не пуст
                if (reminders.isNotEmpty()) {
                    itemsToShow.add("Ваши напоминания" to reminders)
                }

            } else { // parent
                val parentReminders = reminderDao.getAllForOwner(-1)
                    .filter { it.dayOfWeek == adjustedDay }

                // Добавляем, только если список не пуст
                if (parentReminders.isNotEmpty()) {
                    itemsToShow.add("Ваши лекарства" to parentReminders)
                }

                val children = childDao.getAll()
                for (child in children) {
                    val childRem = reminderDao.getAllForOwner(child.id)
                        .filter { it.dayOfWeek == adjustedDay }

                    // Добавляем, только если список не пуст
                    if (childRem.isNotEmpty()) {
                        itemsToShow.add("Для ${child.name}" to childRem)
                    }
                }
            }

            // Загружаем лекарства для инструкций
            val medicines = MedicineRepository.loadMedicines(requireContext())

            withContext(Dispatchers.Main) {
                // Если список секций пуст — показываем заглушку "Нет напоминаний"
                if (itemsToShow.isEmpty()) {
                    val noRemindersView = TextView(requireContext()).apply {
                        text = "Напоминаний на сегодня нет"
                        textSize = 16f
                        textAlignment = View.TEXT_ALIGNMENT_CENTER
                        setPadding(0, 32, 0, 32)
                    }
                    container.addView(noRemindersView)
                } else {
                    // Иначе рисуем секции как обычно
                    itemsToShow.forEach { (title, reminders) ->
                        val titleView = TextView(requireContext()).apply {
                            text = title
                            textSize = 18f
                            // Делаем шрифт жирным для наглядности заголовка
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setPadding(0, 24, 0, 8)
                        }
                        container.addView(titleView)

                        reminders.forEach { reminder ->
                            val tv = TextView(requireContext()).apply {
                                text = "${reminder.medicineName} - ${reminder.time}"
                                setPadding(16, 12, 16, 12)
                                setBackgroundResource(R.drawable.reminder_item_bg)
                                isClickable = true
                                isFocusable = true
                                // Добавим отступ между элементами списка
                                val params = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                                params.setMargins(0, 0, 0, 8)
                                layoutParams = params
                            }

                            tv.setOnClickListener {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    // Логика определения возраста и поиска инструкции
                                    val birthdate = if (reminder.ownerId == currentUserId) {
                                        prefs.getString("user_birthdate", null)
                                    } else {
                                        childDao.getChildById(reminder.ownerId)?.birthDate
                                    }

                                    val age = birthdate?.let { birth ->
                                        try {
                                            val parts = birth.split("/").map { it.toInt() }
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
                                    val noteText = reminder.note?.takeIf { it.isNotBlank() } ?: "Нет заметки"

                                    withContext(Dispatchers.Main) {
                                        val message = buildString {
                                            if (instructionText.isNotEmpty()) append("Инструкция: $instructionText\n\n")
                                            append("Заметка: $noteText")
                                        }

                                        AlertDialog.Builder(requireContext())
                                            .setTitle("${reminder.medicineName} - ${reminder.time}")
                                            .setMessage(message)
                                            .setPositiveButton("ОК", null)
                                            .show()
                                    }
                                }
                            }
                            container.addView(tv)
                        }
                    }
                }
            }
        }
    }

}
