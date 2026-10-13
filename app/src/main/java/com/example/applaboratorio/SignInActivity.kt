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
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
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
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            // Firebase lanza una excepción (y cierra la app) si recibe campos vacíos
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Escribe tu correo y tu contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSignIn.isEnabled = false

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = auth.currentUser?.uid ?: ""
                        cargarRolYEntrar(uid, email)
                    } else {
                        btnSignIn.isEnabled = true
                        Toast.makeText(this, mensajeDeError(task.exception), Toast.LENGTH_LONG).show()
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

    private fun cargarRolYEntrar(uid: String, email: String) {
        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener { document ->
                // Cuentas creadas cuando la base bloqueaba escrituras quedaron sin documento;
                // lo creamos para que el usuario aparezca en la lista de chats
                if (!document.exists()) {
                    db.collection("usuarios").document(uid)
                        .set(hashMapOf("email" to email, "rol" to "usuario"))
                }
                irAHome(document.getString("rol") ?: "usuario")
            }
            .addOnFailureListener {
                // Antes la app se quedaba congelada aquí sin avisar. La sesión ya es válida,
                // así que entramos como usuario normal aunque no se pueda leer el rol.
                Toast.makeText(
                    this,
                    "No se pudo leer tu perfil (revisa las reglas de Firestore)",
                    Toast.LENGTH_LONG
                ).show()
                irAHome("usuario")
            }
    }

    private fun irAHome(rol: String) {
        Toast.makeText(this, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, HomeActivity::class.java)
        intent.putExtra("ROL_USUARIO", rol)
        startActivity(intent)
        finish()
    }

    private fun mensajeDeError(error: Exception?): String = when (error) {
        is FirebaseAuthInvalidCredentialsException,
        is FirebaseAuthInvalidUserException -> "Correo o contraseña incorrectos"
        is FirebaseNetworkException -> "Sin conexión a internet"
        is FirebaseTooManyRequestsException -> "Demasiados intentos. Espera un momento"
        else -> "Error: ${error?.message}"
    }
}