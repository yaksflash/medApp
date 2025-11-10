package com.example.medapp.repositories

import android.content.Context
import com.example.medapp.models.Medicine
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object MedicineRepository {

    fun loadMedicines(context: Context): List<Medicine> {
        val jsonString = context.assets.open("medicines.json")
            .bufferedReader()
            .use { it.readText() }

        val listType = object : TypeToken<List<Medicine>>() {}.type
        return Gson().fromJson(jsonString, listType)
    }

    fun findMedicineByName(medicines: List<Medicine>, name: String): Medicine? {
        return medicines.find { it.name.equals(name, ignoreCase = true) }
    }

    fun getInstructionForAge(med: Medicine, age: Int): String {
        val instruction = med.instructions.find { age in it.min_age..it.max_age }
        return instruction?.text ?: "Нет инструкции для вашего возраста"
    }
}
