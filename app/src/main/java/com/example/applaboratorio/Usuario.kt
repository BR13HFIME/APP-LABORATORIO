package com.example.applaboratorio

import com.google.firebase.firestore.DocumentSnapshot

data class Usuario(
    val uid: String = "",
    val email: String = "",
    val nombre: String = "",
    val foto: String = ""   // JPEG pequeño en Base64 (vacío si no tiene foto)
) {
    // Si el usuario aún no puso nombre, mostramos la parte de su correo antes de la @
    val nombreVisible: String
        get() = nombre.ifBlank { email.substringBefore('@') }.ifBlank { "Usuario" }

    companion object {
        fun desde(documento: DocumentSnapshot) = Usuario(
            uid = documento.id,
            email = documento.getString("email") ?: "",
            nombre = documento.getString("nombre") ?: "",
            foto = documento.getString("foto") ?: ""
        )
    }
}
