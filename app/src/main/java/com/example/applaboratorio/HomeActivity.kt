package com.example.applaboratorio

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth

class HomeActivity : AppCompatActivity() {

    private lateinit var btnLogout: Button
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        btnLogout = findViewById(R.id.btnLogout)
        auth = FirebaseAuth.getInstance()

        btnLogout.setOnClickListener {
            auth.signOut()   // le dice a Firebase "cierra la sesión del usuario actual"
            Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, SignInActivity::class.java) // Moverse entre pantallas
            startActivity(intent)  // : ejecuta la navegación — abre la pantalla SignInActivity.
            finish() // : cierra la Activity actual (HomeActivity)
        }
    }
}