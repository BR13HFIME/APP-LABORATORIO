package com.example.applaboratorio

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import org.jitsi.meet.sdk.JitsiMeetUserInfo
import java.net.URL

class ChatActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var etMensaje: EditText
    private lateinit var btnEnviar: Button
    private lateinit var btnVideollamada: Button
    private lateinit var tvChatWith: TextView
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var chatId: String
    private lateinit var otroUid: String

    private val permisosLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permisos ->
        val camaraOk = permisos[android.Manifest.permission.CAMERA] ?: false
        val audioOk = permisos[android.Manifest.permission.RECORD_AUDIO] ?: false

        if (camaraOk && audioOk) {
            iniciarVideollamada()
        } else {
            Toast.makeText(this, "Se necesitan permisos de cámara y micrófono", Toast.LENGTH_LONG).show()
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

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val otroEmail = intent.getStringExtra("OTRO_EMAIL") ?: ""
        otroUid = intent.getStringExtra("OTRO_UID") ?: ""
        tvChatWith.text = otroEmail

        findViewById<TextView>(R.id.tvAvatarChat).text = otroEmail.take(1).uppercase().ifEmpty { "U" }

        findViewById<View>(R.id.btnAtras).setOnClickListener { finish() }

        val miUid = auth.currentUser?.uid ?: ""
        chatId = if (miUid < otroUid) "${miUid}_${otroUid}" else "${otroUid}_${miUid}"

        recyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }

        escucharMensajes()

        btnEnviar.setOnClickListener {
            enviarMensaje()
        }

        btnVideollamada.setOnClickListener {
            permisosLauncher.launch(
                arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    private fun iniciarVideollamada() {
        val miUid = auth.currentUser?.uid ?: ""
        if (miUid.isEmpty() || otroUid.isEmpty()) {
            Toast.makeText(this, "No se pudo iniciar la videollamada", Toast.LENGTH_LONG).show()
            return
        }

        // El servidor pide un nombre para entrar; usamos el correo del usuario
        val userInfo = JitsiMeetUserInfo().apply {
            displayName = auth.currentUser?.email ?: "Usuario"
        }

        val options = JitsiMeetConferenceOptions.Builder()
            .setServerURL(URL(SERVIDOR_JITSI))
            .setRoom("AppLaboratorio_$chatId")
            .setUserInfo(userInfo)
            .setAudioMuted(false)
            .setVideoMuted(false)
            .setFeatureFlag("welcomepage.enabled", false)
            .setFeatureFlag("prejoinpage.enabled", false)
            .setFeatureFlag("lobby-mode.enabled", false)
            .setFeatureFlag("invite.enabled", false)
            .setFeatureFlag("add-people.enabled", false)
            .setFeatureFlag("calendar.enabled", false)
            .setFeatureFlag("call-integration.enabled", false)
            .build()

        // Avisamos al otro usuario por el chat para que se una a la misma sala
        guardarMensaje("📹 Inicié una videollamada. Toca «Videollamada» para unirte.")

        JitsiMeetActivity.launch(this, options)
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

        guardarMensaje(texto)

        etMensaje.text.clear()
    }

    private fun guardarMensaje(texto: String) {
        val miUid = auth.currentUser?.uid ?: ""

        val mensaje = hashMapOf(
            "texto" to texto,
            "emisorId" to miUid,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("chats").document(chatId).collection("mensajes")
            .add(mensaje)
    }

    companion object {
        // meet.jit.si ya no sirve desde apps: exige iniciar sesión con Google/GitHub
        // para crear la sala y corta las llamadas embebidas a los 5 minutos.
        // meet.ffmuc.net es un servidor Jitsi comunitario que permite salas anónimas.
        private const val SERVIDOR_JITSI = "https://meet.ffmuc.net"
    }
}