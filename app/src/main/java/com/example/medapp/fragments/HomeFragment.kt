package com.example.medapp.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
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
import com.example.medapp.models.MedicineEvent
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
        // (Логика бота остается прежней, сокращена для читаемости при записи, но в реальном файле будет полной)
        // Я использую ту же логику, что была в предыдущем шаге
        val q = query.lowercase(Locale.getDefault())
        val hasDelete = q.contains("удалить") || q.contains("убрать") || q.contains("отменить")
        val hasAdd = q.contains("добавить") || q.contains("создать") || q.contains("новый") || q.contains("назначить")
        val hasEdit = q.contains("редактировать") || q.contains("изменить") || q.contains("поменять")
        
        val hasChild = q.contains("ребенк") || q.contains("дет") || q.contains("сын") || q.contains("доч") || q.contains("профиль")
        val hasReminder = q.contains("напоминани") || q.contains("лекарств") || q.contains("таблет") || q.contains("прием")
        val hasCustomMedicine = q.contains("нет в списке") || q.contains("свое") || q.contains("другое") || q.contains("не нашел")

        return when {
            (hasAdd || hasCustomMedicine) && (q.contains("лекарств") || q.contains("напоминани")) && hasCustomMedicine -> "Если нужного лекарства нет в каталоге:\n1. Зайдите в раздел 'Каталог'.\n2. Нажмите на текстовую кнопку 'Добавить свое'.\n3. Введите название лекарства вручную."
            hasDelete && hasChild && hasReminder -> "Чтобы удалить лекарство из расписания ребенка:\n1. Перейдите в 'Календарь'.\n2. Найдите нужное напоминание.\n3. Нажмите на него и выберите 'Удалить'."
            hasAdd && hasChild && hasReminder -> "Чтобы назначить лекарство ребенку:\n1. Зайдите в 'Каталог'.\n2. Выберите лекарство (или нажмите 'Добавить свое').\n3. В окне выберите имя ребенка в выпадающем списке.\n4. Укажите время и сохраните."
            hasDelete && hasChild -> "Чтобы удалить профиль ребенка из приложения:\n1. Перейдите в раздел 'Семья'.\n2. Найдите карточку ребенка.\n3. Обычно там есть иконка корзины или удаление доступно по долгому нажатию."
            hasAdd && hasChild -> "Чтобы добавить нового ребенка:\n1. Откройте раздел 'Семья'.\n2. Нажмите кнопку '+' (внизу или в углу).\n3. Введите имя и дату рождения."
            hasEdit && hasChild -> "Изменить данные ребенка (имя или дату рождения) можно в разделе 'Семья', нажав на карточку ребенка."
            hasDelete && hasReminder -> "Удаление напоминаний происходит в Календаре:\nКалендарь -> Нажать на напоминание -> Удалить."
            hasAdd && hasReminder -> "Все новые напоминания создаются через Каталог:\nКаталог -> Выбрать лекарство -> Настроить время."
            hasEdit && hasReminder -> "Изменить время приема или заметку можно в Календаре, нажав на значок карандаша рядом с напоминанием."
            q.contains("qr") || q.contains("код") || q.contains("синхрон") -> "QR-код нужен для переноса расписания на телефон ребенка.\nРодитель: Раздел 'Семья' -> кнопка QR на ребенке.\nРебенок: Главный экран -> кнопка 'QR-синхронизация'."
            q.contains("уведомлен") || q.contains("не приходят") -> "Если уведомления не приходят:\n1. Проверьте звук.\n2. Убедитесь, что уведомления разрешены в настройках телефона."
            q.contains("каталог") -> "В Каталоге хранится список всех лекарств."
            q.contains("семья") -> "В разделе Семья вы управляете аккаунтами детей."
            q.contains("календарь") -> "Календарь показывает расписание на неделю."
            q.contains("привет") || q.contains("здравствуй") -> "Привет! Я помогу вам разобраться с приложением."
            q.contains("кто ты") -> "Я умный помощник MedApp."
            else -> "Я не совсем понял вопрос. Попробуйте спросить иначе:\n- Как удалить лекарство ребенка?\n- Как добавить профиль?\n- Зачем нужен QR код?"
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
            val eventDao = db.medicineEventDao()

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
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                                textSize = 20f
                            }

                            val checkBox = CheckBox(requireContext()).apply {
                                isChecked = reminder.isTaken
                                scaleX = 1.3f
                                scaleY = 1.3f
                                
                                setOnCheckedChangeListener { _, isChecked ->
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        reminder.isTaken = isChecked
                                        reminderDao.update(reminder)

                                        // Сохраняем историю приема
                                        if (isChecked) {
                                            val event = MedicineEvent(
                                                medicineName = reminder.medicineName,
                                                ownerId = reminder.ownerId,
                                                dateTimestamp = System.currentTimeMillis(),
                                                isTaken = true
                                            )
                                            eventDao.insert(event)
                                        }
                                    }
                                }
                            }

                            itemLayout.addView(tv)
                            itemLayout.addView(checkBox)

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
                            tv.setOnClickListener(clickListener)
                            container.addView(itemLayout)
                        }
                    }
                }
            }
        }
    }

}
