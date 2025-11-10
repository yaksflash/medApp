package com.example.medapp.activities

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.medapp.R
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Подгонка под systemBars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val prefs = getSharedPreferences("user_data", MODE_PRIVATE)
        val role = prefs.getString("account_type", "child")

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setupWithNavController(navController)

        // 🔹 Настраиваем граф навигации под роль
        val graphInflater = navController.navInflater
        val graph = if (role == "parent") {
            graphInflater.inflate(R.navigation.nav_graph_parent)
        } else {
            graphInflater.inflate(R.navigation.nav_graph_child)
        }
        navController.graph = graph

        // 🔹 Скрываем ненужные кнопки меню
        if (role == "child") {
            bottomNav.menu.removeItem(R.id.familyFragment)
            bottomNav.menu.removeItem(R.id.catalogFragment)
        }
    }
}
