package com.example.applaboratorio

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.content.Intent
import android.view.View
import com.google.firebase.auth.FirebaseAuth

class HomeActivity : AppCompatActivity() {

    private lateinit var btnLogout: Button
    private lateinit var btnChats: View
    private lateinit var cardVideollamadas: View
    private lateinit var tvAdminPanel: TextView
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activarBordeABorde()
        setContentView(R.layout.activity_home)
        findViewById<View>(R.id.header).dejarEspacioArriba()
        findViewById<View>(R.id.contenido).dejarEspacioAbajo()

        btnLogout = findViewById(R.id.btnLogout)
        btnChats = findViewById(R.id.btnChats)
        cardVideollamadas = findViewById(R.id.cardVideollamadas)
        tvAdminPanel = findViewById(R.id.tvAdminPanel)
        auth = FirebaseAuth.getInstance()

        val email = auth.currentUser?.email ?: ""
        findViewById<TextView>(R.id.tvUserEmail).text = email
        findViewById<TextView>(R.id.tvAvatarHome).text = email.take(1).uppercase().ifEmpty { "U" }

        val rol = intent.getStringExtra("ROL_USUARIO") ?: "usuario"

        if (rol == "admin") {
            tvAdminPanel.visibility = View.VISIBLE
        }

        btnChats.setOnClickListener {
            startActivity(Intent(this, UserListActivity::class.java))
        }

        // Las videollamadas se inician desde el chat con cada contacto
        cardVideollamadas.setOnClickListener {
            startActivity(Intent(this, UserListActivity::class.java))
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}