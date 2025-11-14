package com.example.medapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.medapp.R
import com.example.medapp.models.Reminder

class ReminderAdapter(private var reminders: List<Reminder>) : RecyclerView.Adapter<ReminderAdapter.ReminderViewHolder>() {

    inner class ReminderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMedicine: TextView = itemView.findViewById(R.id.tvMedicine)
        val tvUser: TextView = itemView.findViewById(R.id.tvUser)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
        return ReminderViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        val reminder = reminders[position]
        holder.tvMedicine.text = reminder.medicineName
        holder.tvUser.text = reminder.user
        val dayNames = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
        val dayText = dayNames[reminder.dayOfWeek - 1]
        holder.tvTime.text = "$dayText, Время: ${reminder.time}"

    }

    override fun getItemCount(): Int = reminders.size

    fun updateList(newList: List<Reminder>) {
        reminders = newList
        notifyDataSetChanged()
    }
}
