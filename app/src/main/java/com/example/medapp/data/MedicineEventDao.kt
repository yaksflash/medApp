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
}
