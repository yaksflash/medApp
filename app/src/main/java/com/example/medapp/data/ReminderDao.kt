package com.example.medapp.data

import androidx.room.*
import com.example.medapp.models.Reminder

@Dao
interface ReminderDao {

    // Вставка с заменой существующей записи при конфликте (возвращает ID)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Reminder): Long

    // Обновление существующей записи
    @Update
    suspend fun update(reminder: Reminder)

    // Удаление конкретного напоминания
    @Delete
    suspend fun delete(reminder: Reminder)

    // Получение всех напоминаний
    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<Reminder>

    // Получение всех напоминаний для конкретного владельца
    @Query("SELECT * FROM reminders WHERE ownerId = :ownerId ORDER BY dayOfWeek, time")
    suspend fun getAllForOwner(ownerId: Int): List<Reminder>

    // Удаление всех напоминаний для конкретного владельца
    @Query("DELETE FROM reminders WHERE ownerId = :ownerId")
    suspend fun deleteForOwner(ownerId: Int)

    // Получение конкретного напоминания по ID
    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: Int): Reminder?

}
