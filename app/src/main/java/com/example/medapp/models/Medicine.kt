package com.example.medapp.models

data class Instruction(
    val min_age: Int,
    val max_age: Int,
    val text: String
)

data class Medicine(
    val id: String,
    val name: String,
    val description: String,
    val instructions: List<Instruction>
)
