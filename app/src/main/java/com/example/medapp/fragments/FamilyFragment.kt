package com.example.medapp.fragments

import android.app.AlertDialog
import android.app.DatePickerDialog
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
import kotlinx.coroutines.launch
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
            onQRClick = { child -> showChildQR(child) } // QR
        )

        rvChildren.layoutManager = LinearLayoutManager(requireContext())
        rvChildren.adapter = childrenAdapter

        loadChildren()

        btnAddChild.setOnClickListener {
            showAddChildDialog()
        }
    }

    private fun loadChildren() {
        val dao = AppDatabase.getDatabase(requireContext()).childDao()
        lifecycleScope.launch {
            childrenList.clear()
            childrenList.addAll(dao.getAll())
            childrenAdapter.notifyDataSetChanged()
        }
    }

    private fun showAddChildDialog() {
        val layout = LinearLayout(requireContext())
        layout.orientation = LinearLayout.VERTICAL

        val nameInput = EditText(requireContext())
        nameInput.hint = "Имя"
        layout.addView(nameInput)

        val dobButton = Button(requireContext())
        dobButton.text = "Выберите дату рождения"
        layout.addView(dobButton)

        var selectedDate: String? = null

        dobButton.setOnClickListener {
            val today = Calendar.getInstance()
            val datePicker = DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    selectedDate = String.format("%02d/%02d/%04d", dayOfMonth, month + 1, year)
                    dobButton.text = selectedDate
                },
                today.get(Calendar.YEAR),
                today.get(Calendar.MONTH),
                today.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Добавить ребенка")
            .setView(layout)
            .setPositiveButton("Добавить") { dialog, _ ->
                val name = nameInput.text.toString()
                if (name.isNotEmpty() && selectedDate != null) {
                    val child = Child(name = name, birthDate = selectedDate!!)
                    lifecycleScope.launch {
                        AppDatabase.getDatabase(requireContext()).childDao().insert(child)
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
                    db.reminderDao().deleteForUser(child.name)
                    db.childDao().delete(child)
                    loadChildren()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showChildReminders(child: Child) {
        val dao = AppDatabase.getDatabase(requireContext()).reminderDao()
        lifecycleScope.launch {
            val reminders = dao.getAllForUser(child.name)
            val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }

            val grouped = reminders.groupBy { it.dayOfWeek }
            val days = listOf(
                "Понедельник","Вторник","Среда","Четверг","Пятница","Суббота","Воскресенье"
            )

            days.forEachIndexed { index, day ->
                val dayReminders = grouped[index + 1] ?: emptyList()
                if (dayReminders.isNotEmpty()) {
                    val dayText = TextView(requireContext())
                    dayText.text = day
                    dayText.textSize = 18f
                    layout.addView(dayText)

                    dayReminders.forEach { reminder ->
                        val reminderLayout = LinearLayout(requireContext())
                        reminderLayout.orientation = LinearLayout.HORIZONTAL

                        val tv = TextView(requireContext())
                        tv.text = "${reminder.medicineName} - ${reminder.time}"
                        tv.layoutParams =
                            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                        val btnDelete = Button(requireContext())
                        btnDelete.text = "-"
                        btnDelete.setOnClickListener {
                            lifecycleScope.launch {
                                dao.delete(reminder)
                                layout.removeView(reminderLayout)
                            }
                        }

                        reminderLayout.addView(tv)
                        reminderLayout.addView(btnDelete)
                        layout.addView(reminderLayout)
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
            val reminders = reminderDao.getAllForUser(child.name)
            val jsonMap = mapOf(
                "name" to child.name,
                "birthDate" to child.birthDate,
                "reminders" to reminders.map { r ->
                    mapOf(
                        "medicineName" to r.medicineName,
                        "time" to r.time,
                        "dayOfWeek" to r.dayOfWeek
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
            com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8", // важно
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
        val imageView = ImageView(requireContext())
        imageView.setImageBitmap(qr)
        AlertDialog.Builder(requireContext())
            .setTitle("QR для синхронизации")
            .setView(imageView)
            .setPositiveButton("Закрыть", null)
            .show()
    }
}
