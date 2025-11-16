package com.example.medapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.medapp.R
import com.example.medapp.models.Child
import java.util.*

class ChildrenAdapter(
    private val children: List<Child>,
    private val onItemClick: (Child) -> Unit,
    private val onDeleteClick: (Child) -> Unit,
    private val onQRClick: (Child) -> Unit // колбэк для кнопки QR
) : RecyclerView.Adapter<ChildrenAdapter.ChildViewHolder>() {

    inner class ChildViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvChildName)
        val tvAge: TextView = itemView.findViewById(R.id.tvChildBirthdate)
        val btnDelete: Button = itemView.findViewById(R.id.btnDeleteChild)
        val btnQR: Button = itemView.findViewById(R.id.btnQR)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChildViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_child, parent, false)
        return ChildViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChildViewHolder, position: Int) {
        val child = children[position]
        holder.tvName.text = child.name
        holder.tvAge.text = "Возраст: ${getAge(child.birthDate)} лет"

        holder.itemView.setBackgroundResource(R.drawable.child_card_bg)
        holder.itemView.setPadding(16, 16, 16, 16)

        holder.itemView.setOnClickListener { onItemClick(child) }
        holder.btnDelete.setOnClickListener { onDeleteClick(child) }
        holder.btnQR.setOnClickListener { onQRClick(child) }
    }

    override fun getItemCount(): Int = children.size

    private fun getAge(birthDate: String): Int {
        val parts = birthDate.split("/").map { it.toInt() }
        val day = parts[0]
        val month = parts[1]
        val year = parts[2]
        val today = Calendar.getInstance()
        var age = today.get(Calendar.YEAR) - year
        if (today.get(Calendar.MONTH) + 1 < month ||
            (today.get(Calendar.MONTH) + 1 == month && today.get(Calendar.DAY_OF_MONTH) < day)
        ) age -= 1
        return age
    }
}
