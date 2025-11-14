package com.example.medapp.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.medapp.R
import com.example.medapp.data.AppDatabase
import com.example.medapp.models.Child
import com.example.medapp.models.Reminder
import com.example.medapp.utils.ReminderScheduler
import com.google.gson.GsonBuilder
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Data классы для безопасного чтения QR
data class QRReminder(
    val medicineName: String,
    val time: String,
    val dayOfWeek: Int
)

data class QRData(
    val name: String,
    val birthDate: String,
    val reminders: List<QRReminder>
)

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var btnScanQR: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnScanQR = view.findViewById(R.id.btnScanQR)

        // Кнопка сканирования видна только для детей
        val isChild = checkIfChild()
        btnScanQR.visibility = if (isChild) View.VISIBLE else View.GONE

        btnScanQR.setOnClickListener {
            startQRScanner()
        }
    }

    private fun checkIfChild(): Boolean {
        val prefs = requireContext().getSharedPreferences("user_data", 0)
        return prefs.getString("account_type", "") == "child"
    }

    private fun startQRScanner() {
        val integrator = IntentIntegrator.forSupportFragment(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt("Сканируйте QR код родителя")
        integrator.setCameraId(0)
        integrator.setBeepEnabled(true)
        integrator.setBarcodeImageEnabled(true)
        qrLauncher.launch(integrator.createScanIntent())
    }

    private val qrLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val intentResult = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        if (intentResult != null && intentResult.contents != null) {
            val json = intentResult.contents
            handleQRData(json)
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
            // Используем GsonBuilder для безопасного чтения и корректной UTF-8 кодировки
            val gson = GsonBuilder().setLenient().create()
            val qrData = gson.fromJson(json, QRData::class.java)

            val birthDate = qrData.birthDate
            val remindersList = qrData.reminders

            // Получаем имя текущего ребёнка (тот, кто сканирует)
            val prefs = requireContext().getSharedPreferences("user_data", 0)
            val currentChildName = prefs.getString("user_name", "Я") ?: "Я"

            val child = Child(name = currentChildName, birthDate = birthDate)

            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(requireContext())
                val childDao = db.childDao()
                val reminderDao = db.reminderDao()

                // Вставляем/обновляем ребёнка (текущего пользователя)
                childDao.insert(child)

                // Удаляем все старые напоминания этого ребёнка
                reminderDao.deleteForUser(currentChildName)

                // Добавляем новые напоминания из QR под текущим ребёнком и планируем уведомления
                remindersList.forEach { r ->
                    val reminder = Reminder(
                        user = currentChildName,
                        medicineName = r.medicineName,
                        time = r.time,
                        dayOfWeek = r.dayOfWeek
                    )
                    val id = reminderDao.insert(reminder).toInt()

                    ReminderScheduler.scheduleWeeklyReminder(
                        context = requireContext(),
                        id = id,
                        dayOfWeek = reminder.dayOfWeek,
                        time = reminder.time,
                        medicineName = reminder.medicineName,
                        user = reminder.user
                    )
                }

                // Сообщение пользователю на главном потоке
                CoroutineScope(Dispatchers.Main).launch {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Синхронизация завершена")
                        .setMessage("Напоминания успешно обновлены")
                        .setPositiveButton("OK", null)
                        .show()
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
}
