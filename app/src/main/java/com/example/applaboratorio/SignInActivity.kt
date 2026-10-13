package com.example.applaboratorio

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.EditText
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.content.Context
import android.content.Intent
import android.view.View
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignInActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSignIn: Button
    private lateinit var tvGoToSignUp: TextView
    private lateinit var btnChangeLanguage: Button
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun attachBaseContext(newBase: Context) {
        val language = LocaleHelper.getSavedLanguage(newBase)
        LocaleHelper.setLocale(newBase, language)
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activarBordeABorde()
        setContentView(R.layout.activity_sign_in)
        findViewById<View>(R.id.hero).dejarEspacioArriba()
        findViewById<View>(R.id.scrollRoot).dejarEspacioAbajo()

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnSignIn = findViewById(R.id.btnSignIn)
        tvGoToSignUp = findViewById(R.id.tvGoToSignUp)
        btnChangeLanguage = findViewById(R.id.btnChangeLanguage)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        btnSignIn.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = auth.currentUser?.uid

                        if (uid != null) {
                            db.collection("usuarios").document(uid).get()
                                .addOnSuccessListener { document ->
                                    val rol = document.getString("rol") ?: "usuario"

                                    Toast.makeText(this, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()

                                    val intent = Intent(this, HomeActivity::class.java)
                                    intent.putExtra("ROL_USUARIO", rol)
                                    startActivity(intent)
                                    finish()
                                }
                        }
                    } else {
                        Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        tvGoToSignUp.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }

        btnChangeLanguage.setOnClickListener {
            val currentLanguage = LocaleHelper.getSavedLanguage(this)
            val newLanguage = if (currentLanguage == "es") "en" else "es"
            LocaleHelper.setLocale(this, newLanguage)
            recreate()
        }
    }
}