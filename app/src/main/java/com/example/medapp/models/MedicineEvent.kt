package com.example.medapp.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicine_events")
data class MedicineEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val medicineName: String,
    val ownerId: Int,
    val dateTimestamp: Long, // Время приема (для сравнения дат)
    val isTaken: Boolean = true
)
