package com.example.applaboratorio

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

// Lista de conversaciones: cada contacto con su último mensaje, ordenados por el más reciente
class UserListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UsuarioAdapter
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var uidActual: String

    private var usuarios: List<Usuario> = emptyList()
    private var chats: Map<String, DocumentSnapshot> = emptyMap()
    private var usuariosCargados = false
    private var filtro = ""
    private val escuchas = mutableListOf<ListenerRegistration>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activarBordeABorde()
        setContentView(R.layout.activity_user_list)
        findViewById<View>(R.id.header).dejarEspacioArriba()

        recyclerView = findViewById(R.id.rvUsuarios)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.dejarEspacioAbajo()

        findViewById<View>(R.id.btnAtras).setOnClickListener { finish() }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        uidActual = auth.currentUser?.uid ?: ""

        adapter = UsuarioAdapter(uidActual) { usuarioSeleccionado ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("OTRO_UID", usuarioSeleccionado.uid)
            intent.putExtra("OTRO_EMAIL", usuarioSeleccionado.email)
            intent.putExtra("OTRO_NOMBRE", usuarioSeleccionado.nombreVisible)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        findViewById<EditText>(R.id.etBuscar).doAfterTextChanged {
            filtro = it?.toString()?.trim().orEmpty()
            pintar()
        }
    }

    // Escuchamos en vivo solo mientras la pantalla está visible
    override fun onStart() {
        super.onStart()
        escucharUsuarios()
        escucharChats()
    }

    override fun onStop() {
        escuchas.forEach { it.remove() }
        escuchas.clear()
        super.onStop()
    }

    private fun escucharUsuarios() {
        escuchas += db.collection("usuarios")
            .addSnapshotListener { resultado, error ->
                findViewById<View>(R.id.pbCargando).visibility = View.GONE
                if (error != null || resultado == null) {
                    if (!usuariosCargados) mostrarVacio(getString(R.string.users_load_error))
                    return@addSnapshotListener
                }
                usuariosCargados = true
                usuarios = resultado.documents
                    .filter { it.id != uidActual }
                    .map { Usuario.desde(it) }
                pintar()
            }
    }

    private fun escucharChats() {
        if (uidActual.isEmpty()) return
        escuchas += db.collection("chats")
            .whereArrayContains("participantes", uidActual)
            .addSnapshotListener { resultado, error ->
                // Si falla, la lista sigue funcionando, solo sin el último mensaje
                if (error != null || resultado == null) return@addSnapshotListener
                chats = resultado.documents.associateBy { it.id }
                pintar()
            }
    }

    private fun pintar() {
        if (!usuariosCargados) return

        val conversaciones = usuarios
            .filter {
                filtro.isEmpty() ||
                    it.nombreVisible.contains(filtro, ignoreCase = true) ||
                    it.email.contains(filtro, ignoreCase = true)
            }
            .map { usuario ->
                val chat = chats[Videollamada.chatIdEntre(uidActual, usuario.uid)]
                Conversacion(
                    usuario = usuario,
                    ultimoMensaje = chat?.getString("ultimoMensaje") ?: "",
                    ultimoTimestamp = chat?.getLong("ultimoTimestamp") ?: 0L,
                    ultimoEmisor = chat?.getString("ultimoEmisor") ?: "",
                    ultimoLeido = chat?.getBoolean("ultimoLeido") ?: true
                )
            }
            .sortedWith(
                compareByDescending<Conversacion> { it.ultimoTimestamp }
                    .thenBy { it.usuario.nombreVisible.lowercase() }
            )

        adapter.actualizar(conversaciones)

        if (conversaciones.isEmpty()) {
            mostrarVacio(getString(R.string.users_empty))
        } else {
            findViewById<View>(R.id.layoutVacio).visibility = View.GONE
        }
    }

    private fun mostrarVacio(mensaje: String) {
        findViewById<TextView>(R.id.tvVacio).text = mensaje
        findViewById<View>(R.id.layoutVacio).visibility = View.VISIBLE
    }
}
