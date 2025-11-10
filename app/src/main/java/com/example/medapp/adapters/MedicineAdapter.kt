package com.example.medapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.medapp.R
import com.example.medapp.models.Medicine

class MedicineAdapter(
    private var items: List<Medicine>,
    private val onClick: (Medicine) -> Unit
) : RecyclerView.Adapter<MedicineAdapter.MedicineViewHolder>() {

    inner class MedicineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.tvMedicineName)
        val desc: TextView = itemView.findViewById(R.id.tvMedicineDesc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicineViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medicine, parent, false)
        return MedicineViewHolder(view)
    }

    override fun onBindViewHolder(holder: MedicineViewHolder, position: Int) {
        val medicine = items[position]
        holder.name.text = medicine.name
        holder.desc.text = medicine.description
        holder.itemView.setOnClickListener { onClick(medicine) }
    }

    override fun getItemCount() = items.size

    fun updateList(newList: List<Medicine>) {
        items = newList
        notifyDataSetChanged()
    }
}
