package com.example.medapp.fragments

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.medapp.R
import com.example.medapp.data.AppDatabase
import com.example.medapp.models.MedicineEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class StatisticsFragment : Fragment() {

    private lateinit var rvStatistics: RecyclerView
    private lateinit var adapter: StatisticsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_statistics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvStatistics = view.findViewById(R.id.rvStatistics)
        rvStatistics.layoutManager = LinearLayoutManager(requireContext())
        
        loadStatistics()
    }

    private fun loadStatistics() {
        val prefs = requireContext().getSharedPreferences("user_data", 0)
        val currentUserId = prefs.getInt("owner_id", -1)

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val eventsDao = db.medicineEventDao()
            
            val uniqueMedicines = withContext(Dispatchers.IO) {
                eventsDao.getUniqueMedicinesForOwner(currentUserId)
            }

            val allEvents = withContext(Dispatchers.IO) {
                eventsDao.getAllForOwner(currentUserId)
            }

            adapter = StatisticsAdapter(uniqueMedicines) { medicineName ->
                val eventsForMedicine = allEvents.filter { it.medicineName == medicineName }
                showCalendarDialog(medicineName, eventsForMedicine, currentUserId)
            }
            rvStatistics.adapter = adapter
        }
    }

    private fun showCalendarDialog(medicineName: String, events: List<MedicineEvent>, userId: Int) {
        val context = requireContext()
        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // --- Календарь ---
        val calendar = Calendar.getInstance()
        
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val btnPrev = Button(context).apply { text = "<" }
        val btnNext = Button(context).apply { text = ">" }
        val tvMonthYear = TextView(context).apply { 
            textSize = 18f 
            setPadding(16, 0, 16, 0)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }

        headerLayout.addView(btnPrev)
        headerLayout.addView(tvMonthYear)
        headerLayout.addView(btnNext)
        dialogView.addView(headerLayout)

        val gridView = GridView(context).apply {
            numColumns = 7
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing = 4
            verticalSpacing = 4
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 900)
        }
        dialogView.addView(gridView)

        // --- Сводная статистика снизу ---
        val totalDoses = events.size
        val uniqueDays = events.map { 
            val c = Calendar.getInstance().apply { timeInMillis = it.dateTimestamp }
            "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
        }.distinct().size

        val tvSummary = TextView(context).apply {
            text = "Всего дней приема: $uniqueDays\nВсего принято доз: $totalDoses"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
            setTypeface(null, Typeface.BOLD)
        }
        dialogView.addView(tvSummary)

        // --- Логика календаря ---
        fun updateCalendar() {
            val monthFormat = java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            tvMonthYear.text = monthFormat.format(calendar.time)

            val days = ArrayList<Date?>()
            val tempCal = calendar.clone() as Calendar
            tempCal.set(Calendar.DAY_OF_MONTH, 1)
            
            val firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)
            val shift = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2
            
            for (i in 0 until shift) {
                days.add(null)
            }

            val maxDay = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            for (i in 1..maxDay) {
                days.add(tempCal.time)
                tempCal.add(Calendar.DAY_OF_MONTH, 1)
            }

            val calendarAdapter = object : ArrayAdapter<Date>(context, 0, days) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = (convertView as? TextView) ?: TextView(context).apply {
                        layoutParams = android.widget.AbsListView.LayoutParams(
                            android.widget.AbsListView.LayoutParams.MATCH_PARENT,
                            130
                        )
                        gravity = Gravity.CENTER
                        textSize = 16f
                        setTextColor(Color.BLACK)
                        maxLines = 2
                    }
                    
                    val date = getItem(position)

                    if (date == null) {
                        view.text = ""
                        view.background = null
                    } else {
                        val calDay = Calendar.getInstance().apply { time = date }
                        val dayNumberStr = calDay.get(Calendar.DAY_OF_MONTH).toString()
                        
                        val count = events.count { 
                            val eventCal = Calendar.getInstance().apply { timeInMillis = it.dateTimestamp }
                            eventCal.get(Calendar.YEAR) == calDay.get(Calendar.YEAR) &&
                            eventCal.get(Calendar.DAY_OF_YEAR) == calDay.get(Calendar.DAY_OF_YEAR)
                        }

                        if (count > 0) {
                            val text = "$dayNumberStr\n($count)"
                            val spannable = SpannableString(text)
                            spannable.setSpan(StyleSpan(Typeface.BOLD), 0, dayNumberStr.length, 0)
                            spannable.setSpan(RelativeSizeSpan(0.7f), dayNumberStr.length + 1, text.length, 0)
                            view.text = spannable
                            view.setBackgroundColor(Color.GREEN)
                        } else {
                            view.text = dayNumberStr
                            view.setBackgroundColor(Color.TRANSPARENT)
                        }
                    }
                    return view
                }
            }
            gridView.adapter = calendarAdapter
        }

        updateCalendar()

        btnPrev.setOnClickListener {
            calendar.add(Calendar.MONTH, -1)
            updateCalendar()
        }

        btnNext.setOnClickListener {
            calendar.add(Calendar.MONTH, 1)
            updateCalendar()
        }

        // --- Диалог ---
        val dialog = AlertDialog.Builder(context)
            .setTitle("История: $medicineName")
            .setView(dialogView)
            .setPositiveButton("Закрыть", null)
            .setNegativeButton("Удалить историю") { _, _ ->
                AlertDialog.Builder(context)
                    .setTitle("Удаление истории")
                    .setMessage("Вы уверены, что хотите удалить ВСЮ историю приема лекарства '$medicineName'? Это действие нельзя отменить.")
                    .setPositiveButton("Удалить") { _, _ ->
                        deleteHistory(userId, medicineName)
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
            .create()
            
        dialog.show()
    }
    
    private fun deleteHistory(userId: Int, medicineName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            db.medicineEventDao().deleteHistoryByName(userId, medicineName)
            
            withContext(Dispatchers.Main) {
                // Перезагружаем список, чтобы удаленное лекарство исчезло из RecyclerView
                loadStatistics()
                Toast.makeText(requireContext(), "История удалена", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Внутренний адаптер для списка лекарств
    class StatisticsAdapter(
        private val medicines: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<StatisticsAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val name = medicines[position]
            holder.tvName.text = name
            holder.tvName.textSize = 18f
            holder.tvName.setPadding(32, 24, 32, 24)
            holder.itemView.setOnClickListener { onClick(name) }
        }

        override fun getItemCount() = medicines.size
    }
}
