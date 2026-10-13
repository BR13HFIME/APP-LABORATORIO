package com.example.applaboratorio

// Cada contacto recibe siempre el mismo color de avatar según su correo
object AvatarColores {

    private val colores = intArrayOf(
        0xFFEC3B5E.toInt(),
        0xFFFF6B4A.toInt(),
        0xFF7C4DFF.toInt(),
        0xFF00A68C.toInt(),
        0xFF2F80ED.toInt(),
        0xFFF2994A.toInt()
    )

    fun para(texto: String): Int = colores[Math.floorMod(texto.hashCode(), colores.size)]
}
