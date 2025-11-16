package com.example.medapp.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.medapp.R
import com.example.medapp.data.AppDatabase
import com.example.medapp.models.Reminder
import com.example.medapp.utils.ReminderScheduler
import com.google.gson.GsonBuilder
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import com.example.medapp.models.QRData
import com.example.medapp.models.QRReminder

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var btnScanQR: Button
    private lateinit var container: LinearLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnScanQR = view.findViewById(R.id.btnScanQR)
        container = view.findViewById(R.id.containerTodayReminders)

        val prefs = requireContext().getSharedPreferences("user_data", 0)
        val accountType = prefs.getString("account_type", "child")

        btnScanQR.visibility = if (accountType == "child") View.VISIBLE else View.GONE
        btnScanQR.setOnClickListener { startQRScanner() }

        loadTodayReminders()
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

                remindersList.forEach { r ->
                    val reminder = Reminder(
                        ownerId = currentChildId,
                        medicineName = r.medicineName,
                        time = r.time,
                        dayOfWeek = r.dayOfWeek
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

                itemsToShow.add("Ваши напоминания" to reminders)

            } else { // parent

                val parentReminders = reminderDao.getAllForOwner(-1)
                    .filter { it.dayOfWeek == adjustedDay }

                itemsToShow.add("Ваши лекарства" to parentReminders)

                val children = childDao.getAll()
                for (child in children) {
                    val childRem = reminderDao.getAllForOwner(child.id)
                        .filter { it.dayOfWeek == adjustedDay }

                    itemsToShow.add("Для ${child.name}" to childRem)
                }
            }

            withContext(Dispatchers.Main) {
                itemsToShow.forEach { (title, reminders) ->
                    val titleView = TextView(requireContext()).apply {
                        text = title
                        textSize = 18f
                        setPadding(0, 16, 0, 8)
                    }
                    container.addView(titleView)

                    reminders.forEach { reminder ->
                        val tv = TextView(requireContext()).apply {
                            text = "${reminder.medicineName} - ${reminder.time}"
                            setPadding(16, 8, 16, 8)
                            setBackgroundResource(R.drawable.reminder_item_bg)
                            isClickable = false
                            isFocusable = false
                        }
                        container.addView(tv)
                    }
                }
            }
        }
    }
}
