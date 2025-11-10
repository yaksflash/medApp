package com.example.medapp.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.medapp.R
import com.example.medapp.adapters.MedicineAdapter
import com.example.medapp.models.Medicine
import com.example.medapp.repositories.MedicineRepository
import java.util.Calendar

class CatalogFragment : Fragment() {

    private lateinit var adapter: MedicineAdapter
    private lateinit var medicines: List<Medicine>
    private var userAge: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_catalog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("user_data", 0)
        val birthdate = prefs.getString("user_birthdate", "01/01/2010")

        val parts = birthdate!!.split("/").map { it.toInt() }
        val day = parts[0]
        val month = parts[1]
        val year = parts[2]

        val today = Calendar.getInstance()
        var age = today.get(Calendar.YEAR) - year
        if (today.get(Calendar.MONTH) + 1 < month ||
            (today.get(Calendar.MONTH) + 1 == month && today.get(Calendar.DAY_OF_MONTH) < day)
        ) {
            age -= 1
        }

        userAge = age

        val rv = view.findViewById<RecyclerView>(R.id.rvMedicines)
        val searchInput = view.findViewById<EditText>(R.id.searchInput)
        val instructionView = view.findViewById<TextView>(R.id.tvInstruction)

        medicines = MedicineRepository.loadMedicines(requireContext())
        adapter = MedicineAdapter(medicines) { selected ->
            val instruction = MedicineRepository.getInstructionForAge(selected, userAge)
            instructionView.text = instruction
        }

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val filtered = medicines.filter {
                    it.name.contains(s.toString(), ignoreCase = true)
                }
                adapter.updateList(filtered)
            }
        })
    }
}
