package com.example.medapp.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.medapp.R
import java.util.Calendar

class RegisterActivity : AppCompatActivity() {

    private var selectedRole: String? = null

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
        val btnParent = findViewById<Button>(R.id.btnParent)
        val btnChild = findViewById<Button>(R.id.btnChild)
        val prefs = getSharedPreferences("user_data", MODE_PRIVATE)

        // 🔹 Выбор типа аккаунта
        btnParent.setOnClickListener {
            selectedRole = "parent"
            highlightSelected(btnParent, btnChild)
        }

        btnChild.setOnClickListener {
            selectedRole = "child"
            highlightSelected(btnChild, btnParent)
        }

        // 🔹 Выбор даты рождения
        tvBirthdate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val today = Calendar.getInstance()

            val datePickerDialog = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val selectedDateCalendar = Calendar.getInstance()
                    selectedDateCalendar.set(year, month, dayOfMonth)

                    if (selectedDateCalendar.after(today)) {
                        tvBirthdate.error = "Дата не может быть из будущего"
                        tvBirthdate.text = "Выберите дату рождения"
                        return@DatePickerDialog
                    }

                    val selectedDate = "$dayOfMonth/${month + 1}/$year"
                    tvBirthdate.text = selectedDate
                    tvBirthdate.error = null
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )

            datePickerDialog.show()
        }

        // 🔹 Кнопка регистрации
        registerButton.setOnClickListener {
            val name = nameInput.text.toString()
            val birthdate = tvBirthdate.text.toString()

            if (name.isEmpty()) {
                nameInput.error = "Введите имя"
                return@setOnClickListener
            }

            if (birthdate == "Выберите дату рождения") {
                tvBirthdate.error = "Выберите дату рождения"
                return@setOnClickListener
            }

            if (selectedRole == null) {
                Toast.makeText(this, "Выберите тип аккаунта", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit().apply {
                putBoolean("is_registered", true)
                putString("user_name", name)
                putString("user_birthdate", birthdate)
                putString("account_type", selectedRole)
                apply()
            }

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    // Меняет цвет выделенной кнопки
    private fun highlightSelected(selected: Button, other: Button) {
        val selectedColor = ContextCompat.getColor(this, android.R.color.holo_green_light)
        val defaultColor = ContextCompat.getColor(this, android.R.color.darker_gray)

        selected.setBackgroundColor(selectedColor)
        other.setBackgroundColor(defaultColor)
    }
}
