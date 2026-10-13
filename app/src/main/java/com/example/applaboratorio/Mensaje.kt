package com.example.applaboratorio

data class Mensaje(
    val texto: String = "",
    val emisorId: String = "",
    val timestamp: Long = 0,
    val leido: Boolean = false
)
