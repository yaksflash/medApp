package com.example.medapp.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

    // --- Чат-бот классы ---
    data class ChatMessage(val text: String, val isBot: Boolean)

    class ChatAdapter(private val messages: MutableList<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {
        class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvBot: TextView = view.findViewById(R.id.tvBotMessage)
            val tvUser: TextView = view.findViewById(R.id.tvUserMessage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
            return ChatViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            val msg = messages[position]
            if (msg.isBot) {
                holder.tvBot.text = msg.text
                holder.tvBot.visibility = View.VISIBLE
                holder.tvUser.visibility = View.GONE
            } else {
                holder.tvUser.text = msg.text
                holder.tvUser.visibility = View.VISIBLE
                holder.tvBot.visibility = View.GONE
            }
        }

        override fun getItemCount() = messages.size
    }
    // ----------------------

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnScanQR = view.findViewById(R.id.btnScanQR)
        container = view.findViewById(R.id.containerTodayReminders)
        btnHelp = view.findViewById(R.id.btnHelp)

        val prefs = requireContext().getSharedPreferences("user_data", 0)
        val accountType = prefs.getString("account_type", "child")

        btnScanQR.visibility = if (accountType == "child") View.VISIBLE else View.GONE
        btnScanQR.setOnClickListener { startQRScanner() }
        
        btnHelp.setOnClickListener {
            showHelpChatDialog()
        }

        loadTodayReminders()
    }
    
    private fun showHelpChatDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_chat_help, null)
        val rvChat = dialogView.findViewById<RecyclerView>(R.id.rvChatMessages)
        val etMessage = dialogView.findViewById<EditText>(R.id.etChatMessage)
        val btnSend = dialogView.findViewById<Button>(R.id.btnSendMessage)

        val messages = mutableListOf<ChatMessage>()
        messages.add(ChatMessage("Привет! Я твой виртуальный помощник. Спроси меня о чем-нибудь про MedApp!", true))

        val adapter = ChatAdapter(messages)
        rvChat.layoutManager = LinearLayoutManager(requireContext())
        rvChat.adapter = adapter

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Помощь (Чат)")
            .setView(dialogView)
            .setPositiveButton("Закрыть", null)
            .create()

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                // 1. Добавляем сообщение пользователя
                messages.add(ChatMessage(text, false))
                adapter.notifyItemInserted(messages.size - 1)
                rvChat.scrollToPosition(messages.size - 1)
                etMessage.setText("")

                // 2. Генерируем ответ бота
                val botResponse = getBotResponse(text)
                
                // 3. Добавляем ответ бота
                messages.add(ChatMessage(botResponse, true))
                adapter.notifyItemInserted(messages.size - 1)
                rvChat.scrollToPosition(messages.size - 1)
            }
        }

        dialog.show()
    }

    private fun getBotResponse(query: String): String {
        val q = query.lowercase(Locale.getDefault())
        
        return when {
            // 1. Удаление и редактирование
            q.contains("удалить") || q.contains("убрать") || q.contains("редактировать") || q.contains("изменить") -> 
                "Удалять и редактировать напоминания нужно в разделе 'Календарь'.\n\n" +
                "1. Перейдите в 'Календарь'.\n" +
                "2. Найдите нужное напоминание.\n" +
                "3. Нажмите на него (или на иконку карандаша).\n" +
                "4. В меню выберите 'Удалить' или измените заметку/время."

            // 2. Создание напоминаний
            (q.contains("создать") || q.contains("добавить")) && (q.contains("напоминани") || q.contains("лекарство")) ->
                "Новые напоминания создаются в разделе 'Каталог'.\n\n" +
                "1. Зайдите в 'Каталог'.\n" +
                "2. Выберите лекарство из списка или нажмите 'Добавить свое'.\n" +
                "3. В диалоге выберите, кому назначить (себе или ребенку) и укажите время (ежедневно или по дням недели)."

            // 3. Семья и дети
            q.contains("семья") || q.contains("ребенок") || q.contains("дети") || q.contains("добавить ребенка") ->
                "Управлять профилями детей можно в разделе 'Семья'.\n\n" +
                "Там вы можете добавить нового ребенка, чтобы потом назначать ему лекарства и генерировать QR-код для его телефона."

            // 4. QR код и синхронизация
            q.contains("qr") || q.contains("код") || q.contains("синхрон") ->
                "QR-код нужен, чтобы передать расписание с телефона родителя на телефон ребенка.\n\n" +
                "Как это сделать:\n" +
                "1. Родитель заходит в 'Семья' -> нажимает 'Поделиться/QR' у ребенка.\n" +
                "2. Ребенок на своем телефоне на главном экране нажимает 'QR-синхронизация' и сканирует код."

            // 5. Каталог
            q.contains("каталог") ->
                "В 'Каталоге' находится список лекарств с инструкциями. Оттуда удобнее всего назначать прием лекарств."

            // 6. Календарь
            q.contains("календарь") || q.contains("расписани") ->
                "В 'Календаре' отображается полный график приема на всю неделю. Дни недели там выделены жирным, а напоминания можно редактировать."

            // 7. Приветствие
            q.contains("привет") || q.contains("здравствуй") ->
                "Привет! Я готов помочь. Спросите меня: 'Как удалить напоминание?', 'Как добавить ребенка?' или 'Зачем нужен QR?'."

            // 8. Кто ты
            q.contains("кто ты") || q.contains("бот") ->
                "Я — умный помощник MedApp. Я знаю всё о функциях этого приложения."

            // Дефолтный ответ
            else -> "Я не уверен, что понял вопрос. Попробуйте спросить конкретнее:\n" +
                    "- Как добавить напоминание?\n" +
                    "- Как удалить лекарство?\n" +
                    "- Как работает QR код?"
        }
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

                val oldReminders = reminderDao.getAllForOwner(currentChildId)
                oldReminders.forEach { ReminderScheduler.cancelReminder(requireContext(), it.id) }

                reminderDao.deleteForOwner(currentChildId)

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

                if (reminders.isNotEmpty()) {
                    itemsToShow.add("Ваши напоминания" to reminders)
                }

            } else { // parent
                val parentReminders = reminderDao.getAllForOwner(-1)
                    .filter { it.dayOfWeek == adjustedDay }

                if (parentReminders.isNotEmpty()) {
                    itemsToShow.add("Ваши лекарства" to parentReminders)
                }

                val children = childDao.getAll()
                for (child in children) {
                    val childRem = reminderDao.getAllForOwner(child.id)
                        .filter { it.dayOfWeek == adjustedDay }

                    if (childRem.isNotEmpty()) {
                        itemsToShow.add("Для ${child.name}" to childRem)
                    }
                }
            }

            val medicines = MedicineRepository.loadMedicines(requireContext())

            withContext(Dispatchers.Main) {
                if (itemsToShow.isEmpty()) {
                    val noRemindersView = TextView(requireContext()).apply {
                        text = "Напоминаний на сегодня нет"
                        textSize = 16f
                        textAlignment = View.TEXT_ALIGNMENT_CENTER
                        setPadding(0, 32, 0, 32)
                    }
                    container.addView(noRemindersView)
                } else {
                    itemsToShow.forEach { (title, reminders) ->
                        val titleView = TextView(requireContext()).apply {
                            text = title
                            textSize = 18f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setPadding(0, 24, 0, 8)
                        }
                        container.addView(titleView)

                        reminders.forEach { reminder ->
                            val itemLayout = LinearLayout(requireContext()).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_VERTICAL
                                setBackgroundResource(R.drawable.reminder_item_bg)
                                setPadding(16, 12, 16, 12)
                                
                                val params = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                                params.setMargins(0, 0, 0, 8)
                                layoutParams = params
                            }

                            val tv = TextView(requireContext()).apply {
                                text = "${reminder.medicineName} - ${reminder.time}"
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                textSize = 16f
                            }

                            val editBtn = ImageView(requireContext()).apply {
                                setImageResource(R.drawable.pen)
                                layoutParams = LinearLayout.LayoutParams(60, 60)
                                setPadding(8, 8, 8, 8)
                                setColorFilter(android.graphics.Color.DKGRAY)
                            }

                            itemLayout.addView(tv)
                            itemLayout.addView(editBtn)

                            val clickListener = View.OnClickListener {
                                lifecycleScope.launch(Dispatchers.IO) {
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
                            itemLayout.setOnClickListener(clickListener)
                            editBtn.setOnClickListener(clickListener)
                            container.addView(itemLayout)
                        }
                    }
                }
            }
        }
    }

}
