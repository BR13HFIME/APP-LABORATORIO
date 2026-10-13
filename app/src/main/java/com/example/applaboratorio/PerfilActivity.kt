package com.example.applaboratorio

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

// Editar nombre y foto de perfil
class PerfilActivity : AppCompatActivity() {

    private lateinit var etNombre: EditText
    private lateinit var tvAvatar: TextView
    private lateinit var ivAvatar: ImageView
    private lateinit var btnQuitarFoto: View
    private lateinit var btnGuardar: View
    private lateinit var db: FirebaseFirestore

    private var uid = ""
    private var email = ""
    private var foto = ""

    // Selector de fotos del sistema (no necesita pedir permisos)
    private val elegirFoto = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        btnGuardar.isEnabled = false
        Thread {
            val comprimida = Avatar.comprimir(this, uri)
            runOnUiThread {
                btnGuardar.isEnabled = true
                if (comprimida == null) {
                    Toast.makeText(this, R.string.profile_photo_error, Toast.LENGTH_LONG).show()
                } else {
                    foto = comprimida
                    refrescarAvatar()
                }
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activarBordeABorde()
        setContentView(R.layout.activity_perfil)
        findViewById<View>(R.id.header).dejarEspacioArriba()
        findViewById<View>(R.id.scrollRoot).dejarEspacioAbajo()

        etNombre = findViewById(R.id.etNombre)
        tvAvatar = findViewById(R.id.tvAvatarPerfil)
        ivAvatar = findViewById(R.id.ivAvatarPerfil)
        btnQuitarFoto = findViewById(R.id.btnQuitarFoto)
        btnGuardar = findViewById(R.id.btnGuardar)
        db = FirebaseFirestore.getInstance()

        val usuario = FirebaseAuth.getInstance().currentUser
        if (usuario == null) {
            finish()
            return
        }
        uid = usuario.uid
        email = usuario.email ?: ""
        findViewById<EditText>(R.id.etEmailPerfil).setText(email)
        refrescarAvatar()

        db.collection("usuarios").document(uid).get().addOnSuccessListener { doc ->
            val perfil = Usuario.desde(doc)
            // No pisamos lo que el usuario ya haya empezado a escribir o elegir
            if (etNombre.text.isNullOrEmpty()) etNombre.setText(perfil.nombre)
            if (foto.isEmpty()) foto = perfil.foto
            refrescarAvatar()
        }

        findViewById<View>(R.id.btnAtras).setOnClickListener { finish() }

        val abrirSelector = View.OnClickListener {
            elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        findViewById<View>(R.id.btnCamara).setOnClickListener(abrirSelector)
        findViewById<View>(R.id.btnCambiarFoto).setOnClickListener(abrirSelector)

        btnQuitarFoto.setOnClickListener {
            foto = ""
            refrescarAvatar()
        }

        btnGuardar.setOnClickListener { guardar() }
    }

    private fun refrescarAvatar() {
        val nombre = etNombre.text.toString().trim().ifEmpty { email.substringBefore('@') }
        Avatar.mostrar(tvAvatar, ivAvatar, nombre, foto, colorear = false)
        btnQuitarFoto.visibility = if (foto.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun guardar() {
        val nombre = etNombre.text.toString().trim()
        if (nombre.isEmpty()) {
            etNombre.error = getString(R.string.error_required)
            return
        }

        btnGuardar.isEnabled = false
        db.collection("usuarios").document(uid)
            .set(
                hashMapOf("nombre" to nombre, "foto" to foto, "email" to email),
                SetOptions.merge()
            )
            .addOnSuccessListener {
                Toast.makeText(this, R.string.profile_saved, Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                btnGuardar.isEnabled = true
                Toast.makeText(this, getString(R.string.error_generic, e.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
            }
    }
}
