package com.example.applaboratorio

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UserListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_list)

        recyclerView = findViewById(R.id.rvUsuarios)
        recyclerView.layoutManager = LinearLayoutManager(this)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        cargarUsuarios()
    }

    private fun cargarUsuarios() {
        val uidActual = auth.currentUser?.uid

        db.collection("usuarios").get()
            .addOnSuccessListener { resultado ->
                val listaUsuarios = mutableListOf<Usuario>()

                for (documento in resultado) {
                    val uid = documento.id
                    if (uid != uidActual) {
                        val email = documento.getString("email") ?: ""
                        listaUsuarios.add(Usuario(uid = uid, email = email))
                    }
                }

                recyclerView.adapter = UsuarioAdapter(listaUsuarios) { usuarioSeleccionado ->
                    val intent = Intent(this, ChatActivity::class.java)
                    intent.putExtra("OTRO_UID", usuarioSeleccionado.uid)
                    intent.putExtra("OTRO_EMAIL", usuarioSeleccionado.email)
                    startActivity(intent)
                }
            }
    }
}