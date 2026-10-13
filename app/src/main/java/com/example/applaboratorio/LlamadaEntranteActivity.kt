package com.example.applaboratorio

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

// Pantalla completa que suena cuando alguien nos hace una videollamada
class LlamadaEntranteActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_DE_UID = "DE_UID"
        private const val EXTRA_DE_NOMBRE = "DE_NOMBRE"
        private const val EXTRA_SALA = "SALA"
        private const val EXTRA_TIMESTAMP = "TIMESTAMP"

        fun crearIntent(
            context: Context,
            deUid: String,
            deNombre: String,
            sala: String,
            timestamp: Long
        ) = Intent(context, LlamadaEntranteActivity::class.java)
            .putExtra(EXTRA_DE_UID, deUid)
            .putExtra(EXTRA_DE_NOMBRE, deNombre)
            .putExtra(EXTRA_SALA, sala)
            .putExtra(EXTRA_TIMESTAMP, timestamp)
    }

    private lateinit var miUid: String
    private lateinit var sala: String
    private var timestamp = 0L
    private var miNombre = ""
    private var respondida = false

    private var tono: Ringtone? = null
    private var vibrador: Vibrator? = null
    private var latido: ObjectAnimator? = null
    private var escucha: ListenerRegistration? = null
    private val handler = Handler(Looper.getMainLooper())

    private val permisosLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permisos ->
        if (permisos.values.all { it }) {
            contestar()
        } else {
            Toast.makeText(this, R.string.permissions_needed, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mostrarSobrePantallaBloqueada()
        activarBordeABorde()
        setContentView(R.layout.activity_llamada_entrante)

        val raiz = findViewById<View>(R.id.raiz)
        val paddingArriba = raiz.paddingTop
        val paddingAbajo = raiz.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.updatePadding(top = paddingArriba + barras.top, bottom = paddingAbajo + barras.bottom)
            insets
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            finish()
            return
        }
        miUid = uid
        miNombre = FirebaseAuth.getInstance().currentUser?.email?.substringBefore('@') ?: "Usuario"

        val deUid = intent.getStringExtra(EXTRA_DE_UID) ?: ""
        val deNombre = intent.getStringExtra(EXTRA_DE_NOMBRE).orEmpty().ifBlank { "Usuario" }
        sala = intent.getStringExtra(EXTRA_SALA) ?: ""
        timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, 0L)

        val tvAvatar = findViewById<TextView>(R.id.tvAvatarLlamada)
        val ivAvatar = findViewById<ImageView>(R.id.ivAvatarLlamada)
        findViewById<TextView>(R.id.tvNombreLlamada).text = deNombre
        Avatar.mostrar(tvAvatar, ivAvatar, deNombre, "", colorear = false)

        val db = FirebaseFirestore.getInstance()
        // Foto de quien llama y nuestro propio nombre para entrar a la sala
        if (deUid.isNotEmpty()) {
            db.collection("usuarios").document(deUid).get().addOnSuccessListener { doc ->
                val usuario = Usuario.desde(doc)
                Avatar.mostrar(tvAvatar, ivAvatar, deNombre, usuario.foto, colorear = false)
            }
        }
        db.collection("usuarios").document(miUid).get().addOnSuccessListener { doc ->
            miNombre = Usuario.desde(doc).nombreVisible
        }

        // Si quien llama cuelga antes de que contestemos, cerramos esta pantalla
        escucha = db.collection(Videollamada.COLECCION).document(miUid)
            .addSnapshotListener { doc, _ ->
                if (respondida || doc == null) return@addSnapshotListener
                val sigueSonando = doc.exists() &&
                    doc.getString("estado") == Videollamada.SONANDO &&
                    doc.getLong("timestamp") == timestamp
                if (!sigueSonando) {
                    respondida = true
                    finish()
                }
            }

        // Nadie contestó a tiempo: la marcamos como perdida
        handler.postDelayed({
            if (!respondida) {
                respondida = true
                Videollamada.responder(miUid, Videollamada.PERDIDA)
                finish()
            }
        }, Videollamada.SEGUNDOS_TIMBRE * 1000L)

        findViewById<View>(R.id.btnRechazar).setOnClickListener { rechazar() }
        findViewById<View>(R.id.btnContestar).setOnClickListener {
            permisosLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = rechazar()
        })

        empezarTimbre()
        animarAro(findViewById(R.id.aro))
    }

    private fun contestar() {
        if (respondida) return
        respondida = true
        detenerTimbre()
        Videollamada.responder(miUid, Videollamada.ACEPTADA)
        Videollamada.abrir(this, sala, miNombre)
        finish()
    }

    private fun rechazar() {
        if (!respondida) {
            respondida = true
            Videollamada.responder(miUid, Videollamada.RECHAZADA)
        }
        finish()
    }

    private fun empezarTimbre() {
        try {
            tono = RingtoneManager.getRingtone(
                this,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            )?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
        } catch (e: Exception) {
            tono = null
        }

        @Suppress("DEPRECATION")
        vibrador = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrador?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0))
    }

    private fun detenerTimbre() {
        tono?.stop()
        tono = null
        vibrador?.cancel()
        vibrador = null
        latido?.cancel()
    }

    private fun animarAro(aro: View) {
        latido = ObjectAnimator.ofPropertyValuesHolder(
            aro,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f)
        ).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun mostrarSobrePantallaBloqueada() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        detenerTimbre()
        escucha?.remove()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
