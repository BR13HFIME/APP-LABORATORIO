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
    private lateinit var btnChats: Button
    private lateinit var tvAdminPanel: TextView
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        btnLogout = findViewById(R.id.btnLogout)
        btnChats = findViewById(R.id.btnChats)
        tvAdminPanel = findViewById(R.id.tvAdminPanel)
        auth = FirebaseAuth.getInstance()

        val rol = intent.getStringExtra("ROL_USUARIO") ?: "usuario"

        if (rol == "admin") {
            tvAdminPanel.visibility = View.VISIBLE
        }

        btnChats.setOnClickListener {
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