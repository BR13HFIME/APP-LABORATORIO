package com.example.applaboratorio

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ChatActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var etMensaje: EditText
    private lateinit var btnEnviar: Button
    private lateinit var tvChatWith: TextView
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var chatId: String
    private lateinit var otroUid: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        recyclerView = findViewById(R.id.rvMensajes)
        etMensaje = findViewById(R.id.etMensaje)
        btnEnviar = findViewById(R.id.btnEnviar)
        tvChatWith = findViewById(R.id.tvChatWith)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val otroEmail = intent.getStringExtra("OTRO_EMAIL") ?: ""
        otroUid = intent.getStringExtra("OTRO_UID") ?: ""
        tvChatWith.text = otroEmail

        val miUid = auth.currentUser?.uid ?: ""
        chatId = if (miUid < otroUid) "${miUid}_${otroUid}" else "${otroUid}_${miUid}"

        recyclerView.layoutManager = LinearLayoutManager(this)

        escucharMensajes()

        btnEnviar.setOnClickListener {
            enviarMensaje()
        }
    }

    private fun escucharMensajes() {
        db.collection("chats").document(chatId).collection("mensajes")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val listaMensajes = mutableListOf<Mensaje>()
                for (documento in snapshot) {
                    val mensaje = documento.toObject(Mensaje::class.java)
                    listaMensajes.add(mensaje)
                }

                val miUid = auth.currentUser?.uid ?: ""
                recyclerView.adapter = MensajeAdapter(listaMensajes, miUid)
                recyclerView.scrollToPosition(listaMensajes.size - 1)
            }
    }

    private fun enviarMensaje() {
        val texto = etMensaje.text.toString().trim()
        if (texto.isEmpty()) return

        val miUid = auth.currentUser?.uid ?: ""

        val mensaje = hashMapOf(
            "texto" to texto,
            "emisorId" to miUid,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("chats").document(chatId).collection("mensajes")
            .add(mensaje)

        etMensaje.text.clear()
    }
}