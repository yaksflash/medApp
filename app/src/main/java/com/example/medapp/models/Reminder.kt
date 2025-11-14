package com.example.medapp.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val medicineName: String,
    val user: String,        // "Я" или "Ребёнок"
    val dayOfWeek: Int,      // 1=Пн, 7=Вс
    val time: String         // "HH:mm"
)
