package com.example.medapp.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.medapp.R
import java.util.Calendar

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)

        // Подгонка под systemBars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.register_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val nameInput = findViewById<EditText>(R.id.etName)
        val tvBirthdate = findViewById<TextView>(R.id.tvBirthdate)
        val registerButton = findViewById<Button>(R.id.btnRegister)
        val prefs = getSharedPreferences("user_data", MODE_PRIVATE)

        // Выбор даты рождения
        tvBirthdate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val today = Calendar.getInstance() // текущая дата

            val datePickerDialog = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val selectedDateCalendar = Calendar.getInstance()
                    selectedDateCalendar.set(year, month, dayOfMonth)

                    if (selectedDateCalendar.after(today)) {
                        // Ошибка при выборе будущей даты
                        tvBirthdate.error = "Дата не может быть из будущего"
                        tvBirthdate.text = "Выберите дату рождения"
                        return@DatePickerDialog
                    }

                    val selectedDate = "$dayOfMonth/${month + 1}/$year"
                    tvBirthdate.text = selectedDate
                    tvBirthdate.error = null // убираем ошибку
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )

            datePickerDialog.show()
        }

        // Кнопка регистрации
        registerButton.setOnClickListener {
            val name = nameInput.text.toString()
            val birthdate = tvBirthdate.text.toString()

            // Проверка имени
            if (name.isEmpty()) {
                nameInput.error = "Введите имя"
                return@setOnClickListener
            }

            // Проверка даты рождения
            if (birthdate == "Выберите дату рождения") {
                tvBirthdate.error = "Выберите дату рождения"
                return@setOnClickListener
            }

            // Сохранение данных
            prefs.edit().apply {
                putBoolean("is_registered", true)
                putString("user_name", name)
                putString("user_birthdate", birthdate)
                apply()
            }

            // Переход на MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
