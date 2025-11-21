package com.example.medapp.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val medicineName: String,
    val ownerId: Int,
    val dayOfWeek: Int,      // 1=Пн, 7=Вс
    var time: String,         // "HH:mm"
    var note: String? = null,
    var isTaken: Boolean = false // Статус выполнения
)
