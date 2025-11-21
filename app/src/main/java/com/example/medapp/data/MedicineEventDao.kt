package com.example.medapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.medapp.models.MedicineEvent

@Dao
interface MedicineEventDao {
    @Insert
    suspend fun insert(event: MedicineEvent)

    @Query("SELECT * FROM medicine_events WHERE ownerId = :ownerId")
    suspend fun getAllForOwner(ownerId: Int): List<MedicineEvent>
    
    @Query("SELECT DISTINCT medicineName FROM medicine_events WHERE ownerId = :ownerId")
    suspend fun getUniqueMedicinesForOwner(ownerId: Int): List<String>

    @Query("DELETE FROM medicine_events WHERE ownerId = :ownerId AND medicineName = :medicineName AND dateTimestamp BETWEEN :startTime AND :endTime")
    suspend fun deleteEventForDay(ownerId: Int, medicineName: String, startTime: Long, endTime: Long)

    // Новый метод для удаления всей истории для лекарства
    @Query("DELETE FROM medicine_events WHERE ownerId = :ownerId AND medicineName = :medicineName")
    suspend fun deleteHistoryByName(ownerId: Int, medicineName: String)
}
