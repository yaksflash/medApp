package com.example.medapp.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.os.Handler
import android.os.Looper
import com.example.medapp.R

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val prefs = getSharedPreferences("user_data", MODE_PRIVATE)
        val isRegistered = prefs.getBoolean("is_registered", false)

        // Показываем заставку 1,5 секунды
        Handler(Looper.getMainLooper()).postDelayed({
            if (isRegistered) {
                // Пользователь уже зарегистрирован → MainActivity
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                // Пользователь не зарегистрирован → RegisterActivity
                startActivity(Intent(this, RegisterActivity::class.java))
            }
            finish()
        }, 1500)
    }
}
