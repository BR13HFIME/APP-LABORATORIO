package com.example.applaboratorio

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.content.Intent
import android.view.View
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class HomeActivity : AppCompatActivity() {

    private lateinit var btnLogout: Button
    private lateinit var btnChats: View
    private lateinit var cardVideollamadas: View
    private lateinit var tvAdminPanel: TextView
    private lateinit var tvWelcome: TextView
    private lateinit var tvAvatarHome: TextView
    private lateinit var ivAvatarHome: ImageView
    private lateinit var auth: FirebaseAuth
    private var escuchaPerfil: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        val usuarioActual = auth.currentUser
        if (usuarioActual == null) {
            irAInicioDeSesion()
            return
        }

        LocaleHelper.sincronizar(this)
        activarBordeABorde()
        setContentView(R.layout.activity_home)
        findViewById<View>(R.id.header).dejarEspacioArriba()
        findViewById<View>(R.id.contenido).dejarEspacioAbajo()

        btnLogout = findViewById(R.id.btnLogout)
        btnChats = findViewById(R.id.btnChats)
        cardVideollamadas = findViewById(R.id.cardVideollamadas)
        tvAdminPanel = findViewById(R.id.tvAdminPanel)
        tvWelcome = findViewById(R.id.tvWelcome)
        tvAvatarHome = findViewById(R.id.tvAvatarHome)
        ivAvatarHome = findViewById(R.id.ivAvatarHome)

        val email = usuarioActual.email ?: ""
        findViewById<TextView>(R.id.tvUserEmail).text = email
        mostrarPerfil(Usuario(uid = usuarioActual.uid, email = email))

        // El rol llega desde el inicio de sesión; al reabrir la app se lee del perfil
        mostrarRol(intent.getStringExtra("ROL_USUARIO") ?: "usuario")

        btnChats.setOnClickListener {
            startActivity(Intent(this, UserListActivity::class.java))
        }

        // Las videollamadas se inician desde el chat con cada contacto
        cardVideollamadas.setOnClickListener {
            startActivity(Intent(this, UserListActivity::class.java))
        }

        val abrirPerfil = View.OnClickListener {
            startActivity(Intent(this, PerfilActivity::class.java))
        }
        findViewById<View>(R.id.cardPerfil).setOnClickListener(abrirPerfil)
        findViewById<View>(R.id.avatarHome).setOnClickListener(abrirPerfil)

        findViewById<View>(R.id.btnChangeLanguage).setOnClickListener {
            LocaleHelper.alternarIdioma(this)
        }

        btnLogout.setOnClickListener {
            escuchaPerfil?.remove()
            auth.signOut()
            Toast.makeText(this, R.string.logout_done, Toast.LENGTH_SHORT).show()
            irAInicioDeSesion()
        }

        // Nombre, foto y rol en vivo: si se editan en "Mi perfil" se ven aquí al volver
        escuchaPerfil = FirebaseFirestore.getInstance()
            .collection("usuarios").document(usuarioActual.uid)
            .addSnapshotListener { doc, _ ->
                if (doc == null || !doc.exists()) return@addSnapshotListener
                mostrarPerfil(Usuario.desde(doc))
                mostrarRol(doc.getString("rol") ?: "usuario")
            }
    }

    private fun mostrarPerfil(usuario: Usuario) {
        tvWelcome.text = getString(R.string.hello_name, usuario.nombreVisible)
        Avatar.mostrar(tvAvatarHome, ivAvatarHome, usuario.nombreVisible, usuario.foto, colorear = false)
    }

    private fun mostrarRol(rol: String) {
        tvAdminPanel.visibility = if (rol == "admin") View.VISIBLE else View.GONE
    }

    private fun irAInicioDeSesion() {
        val intent = Intent(this, SignInActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        escuchaPerfil?.remove()
        super.onDestroy()
    }
}
