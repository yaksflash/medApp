package com.example.medapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import com.example.medapp.models.Reminder

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Reminder): Long   // ← ВОТ ЭТО ВАЖНО

    @Delete
    suspend fun delete(reminder: Reminder)

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE user = :user ORDER BY dayOfWeek, time")
    suspend fun getAllForUser(user: String): List<Reminder>
}
