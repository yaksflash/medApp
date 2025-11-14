package com.example.medapp.data

import androidx.room.*
import com.example.medapp.models.Child

@Dao
interface ChildDao {

    @Insert
    suspend fun insert(child: Child)

    @Delete
    suspend fun delete(child: Child)

    @Query("SELECT * FROM children ORDER BY name ASC")
    suspend fun getAll(): List<Child>
}
