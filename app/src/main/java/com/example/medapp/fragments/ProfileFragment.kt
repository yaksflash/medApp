package com.example.medapp.fragments

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.example.medapp.R
import java.util.Calendar

class ProfileFragment : Fragment() {

    private lateinit var tvName: TextView
    private lateinit var tvBirthdate: TextView
    private lateinit var btnEditName: ImageView
    private lateinit var btnEditBirthdate: ImageView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("user_data", 0)

        tvName = view.findViewById(R.id.tvName)
        tvBirthdate = view.findViewById(R.id.tvBirthdate)
        btnEditName = view.findViewById(R.id.btnEditName)
        btnEditBirthdate = view.findViewById(R.id.btnEditBirthdate)

        // Загружаем данные
        tvName.text = prefs.getString("user_name", "Имя")
        tvBirthdate.text = prefs.getString("user_birthdate", "Дата рождения")

        btnEditName.setOnClickListener { showEditNameDialog() }
        btnEditBirthdate.setOnClickListener { showEditBirthdateDialog() }
    }

    private fun showEditNameDialog() {
        val prefs = requireContext().getSharedPreferences("user_data", 0)
        val input = EditText(requireContext()).apply {
            setText(tvName.text)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Изменить имя")
            .setView(input)
            .setPositiveButton("Сохранить") { dialog, _ ->
                val newName = input.text.toString()
                if (newName.isNotBlank()) {
                    tvName.text = newName
                    prefs.edit().putString("user_name", newName).apply()
                } else {
                    Toast.makeText(requireContext(), "Имя не может быть пустым", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showEditBirthdateDialog() {
        val prefs = requireContext().getSharedPreferences("user_data", 0)
        val currentText = tvBirthdate.text.toString()
        val parts = currentText.split("/").mapNotNull { it.toIntOrNull() }
        val calendar = Calendar.getInstance()
        if (parts.size == 3) {
            calendar.set(parts[2], parts[1] - 1, parts[0])
        }

        val today = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedDate = String.format("%02d/%02d/%04d", dayOfMonth, month + 1, year)
                if (Calendar.getInstance().apply { set(year, month, dayOfMonth) }.after(today)) {
                    Toast.makeText(requireContext(), "Дата не может быть в будущем", Toast.LENGTH_SHORT).show()
                    return@DatePickerDialog
                }
                tvBirthdate.text = selectedDate
                prefs.edit().putString("user_birthdate", selectedDate).apply()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}
