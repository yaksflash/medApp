package com.example.medapp.models

data class QRData(
    val reminders: List<QRReminder>
)

data class QRReminder(
    val medicineName: String,
    val dayOfWeek: Int,
    val time: String,
    val note: String? = null
)
