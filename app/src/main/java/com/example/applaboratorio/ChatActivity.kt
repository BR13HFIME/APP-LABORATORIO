package com.example.applaboratorio

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

class ChatActivity : AppCompatActivity() {

    companion object {
        // También lo usa la pantalla de llamada para dejar el aviso "📹 Inicié una videollamada"
        fun guardarMensaje(chatId: String, miUid: String, otroUid: String, texto: String) {
            val ahora = System.currentTimeMillis()

            val mensaje = hashMapOf(
                "texto" to texto,
                "emisorId" to miUid,
                "timestamp" to ahora,
                "leido" to false
            )

            val chatRef = FirebaseFirestore.getInstance().collection("chats").document(chatId)
            chatRef.collection("mensajes").add(mensaje)

            // Resumen para la lista de conversaciones (último mensaje, hora y si ya se leyó)
            chatRef.set(
                hashMapOf(
                    "participantes" to listOf(miUid, otroUid),
                    "ultimoMensaje" to texto,
                    "ultimoTimestamp" to ahora,
                    "ultimoEmisor" to miUid,
                    "ultimoLeido" to false
                ),
                SetOptions.merge()
            )
        }
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var etMensaje: EditText
    private lateinit var btnEnviar: Button
    private lateinit var btnVideollamada: Button
    private lateinit var tvChatWith: TextView
    private lateinit var tvAvatarChat: TextView
    private lateinit var ivAvatarChat: ImageView
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var chatId: String
    private lateinit var otroUid: String
    private lateinit var miUid: String

    private var otroNombre = ""
    private var miNombre = ""
    private var enPantalla = false
    private var mensajesActuales: List<DocumentSnapshot> = emptyList()
    private val escuchas = mutableListOf<ListenerRegistration>()

    private val permisosLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permisos ->
        val camaraOk = permisos[android.Manifest.permission.CAMERA] ?: false
        val audioOk = permisos[android.Manifest.permission.RECORD_AUDIO] ?: false

        if (camaraOk && audioOk) {
            iniciarVideollamada()
        } else {
            Toast.makeText(this, R.string.permissions_needed, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activarBordeABorde()
        setContentView(R.layout.activity_chat)
        findViewById<View>(R.id.header).dejarEspacioArriba()
        findViewById<View>(R.id.bottomBar).dejarEspacioAbajo()

        recyclerView = findViewById(R.id.rvMensajes)
        etMensaje = findViewById(R.id.etMensaje)
        btnEnviar = findViewById(R.id.btnEnviar)
        btnVideollamada = findViewById(R.id.btnVideollamada)
        tvChatWith = findViewById(R.id.tvChatWith)
        tvAvatarChat = findViewById(R.id.tvAvatarChat)
        ivAvatarChat = findViewById(R.id.ivAvatarChat)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        miUid = auth.currentUser?.uid ?: ""
        otroUid = intent.getStringExtra("OTRO_UID") ?: ""
        otroNombre = intent.getStringExtra("OTRO_NOMBRE")
            ?: intent.getStringExtra("OTRO_EMAIL")?.substringBefore('@')
            ?: ""
        miNombre = auth.currentUser?.email?.substringBefore('@') ?: "Usuario"

        tvChatWith.text = otroNombre
        Avatar.mostrar(tvAvatarChat, ivAvatarChat, otroNombre, "", colorear = false)

        findViewById<View>(R.id.btnAtras).setOnClickListener { finish() }

        chatId = Videollamada.chatIdEntre(miUid, otroUid)

        recyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }

        btnEnviar.setOnClickListener {
            enviarMensaje()
        }

        btnVideollamada.setOnClickListener {
            permisosLauncher.launch(
                arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO)
            )
        }

        cargarPerfiles()
        escucharMensajes()
    }

    override fun onResume() {
        super.onResume()
        enPantalla = true
        marcarComoLeidos()
    }

    override fun onPause() {
        enPantalla = false
        super.onPause()
    }

    override fun onDestroy() {
        escuchas.forEach { it.remove() }
        super.onDestroy()
    }

    private fun cargarPerfiles() {
        if (otroUid.isNotEmpty()) {
            escuchas += db.collection("usuarios").document(otroUid)
                .addSnapshotListener { doc, _ ->
                    if (doc == null || !doc.exists()) return@addSnapshotListener
                    val otro = Usuario.desde(doc)
                    otroNombre = otro.nombreVisible
                    tvChatWith.text = otroNombre
                    Avatar.mostrar(tvAvatarChat, ivAvatarChat, otroNombre, otro.foto, colorear = false)
                }
        }
        if (miUid.isNotEmpty()) {
            db.collection("usuarios").document(miUid).get()
                .addOnSuccessListener { doc -> miNombre = Usuario.desde(doc).nombreVisible }
        }
    }

    private fun iniciarVideollamada() {
        if (miUid.isEmpty() || otroUid.isEmpty()) {
            Toast.makeText(this, R.string.call_failed, Toast.LENGTH_LONG).show()
            return
        }
        // La pantalla de llamada se encarga de todo: señalización, timbre, aviso en el chat
        // y avisar si el otro rechaza o no contesta
        startActivity(LlamadaActivity.intentLlamar(this, otroUid, otroNombre, miNombre, chatId))
    }

    private fun escucharMensajes() {
        escuchas += db.collection("chats").document(chatId).collection("mensajes")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                mensajesActuales = snapshot.documents
                val listaMensajes = snapshot.documents.mapNotNull { it.toObject(Mensaje::class.java) }

                recyclerView.adapter = MensajeAdapter(listaMensajes, miUid)
                recyclerView.scrollToPosition(listaMensajes.size - 1)

                marcarComoLeidos()
            }
    }

    // Confirmación de lectura: lo que el otro nos mandó queda como leído al verlo
    private fun marcarComoLeidos() {
        if (!enPantalla || miUid.isEmpty()) return

        val sinLeer = mensajesActuales.filter {
            it.getString("emisorId") == otroUid && it.getBoolean("leido") != true
        }
        if (sinLeer.isEmpty()) return

        val lote = db.batch()
        sinLeer.forEach { lote.update(it.reference, "leido", true) }
        lote.set(
            db.collection("chats").document(chatId),
            hashMapOf("ultimoLeido" to true),
            SetOptions.merge()
        )
        lote.commit()
    }

    private fun enviarMensaje() {
        val texto = etMensaje.text.toString().trim()
        if (texto.isEmpty()) return

        guardarMensaje(chatId, miUid, otroUid, texto)

        etMensaje.text.clear()
    }
}
