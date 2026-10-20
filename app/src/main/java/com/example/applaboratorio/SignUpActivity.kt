package com.example.applaboratorio

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Patterns
import android.widget.EditText
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.content.Intent
import android.view.View
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore  //Firestore

class SignUpActivity : AppCompatActivity() {

    private lateinit var etNombre: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnSignUp: Button
    private lateinit var tvGoToSignIn: TextView
    private lateinit var btnChangeLanguage: Button
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activarBordeABorde()
        setContentView(R.layout.activity_sign_up)
        findViewById<View>(R.id.hero).dejarEspacioArriba()
        findViewById<View>(R.id.scrollRoot).dejarEspacioAbajo()

        etNombre = findViewById(R.id.etNombre)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnSignUp = findViewById(R.id.btnSignUp)
        tvGoToSignIn = findViewById(R.id.tvGoToSignIn)
        btnChangeLanguage = findViewById(R.id.btnChangeLanguage)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        btnSignUp.setOnClickListener { registrar() }

        tvGoToSignIn.setOnClickListener {
            // Volvemos a la pantalla de inicio de sesión que ya está abierta debajo
            finish()
        }

        btnChangeLanguage.setOnClickListener {
            LocaleHelper.alternarIdioma(this)
        }
    }

    // Revisa los campos y marca el primero con error. Devuelve true si todo está bien.
    private fun camposValidos(nombre: String, email: String, password: String, confirmPassword: String): Boolean {
        val error: Pair<EditText, Int>? = when {
            nombre.isEmpty() -> etNombre to R.string.error_required
            email.isEmpty() -> etEmail to R.string.error_required
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> etEmail to R.string.error_email_invalid
            password.length < 6 -> etPassword to R.string.error_password_short
            password != confirmPassword -> etConfirmPassword to R.string.error_passwords_mismatch
            else -> null
        }
        if (error != null) {
            error.first.error = getString(error.second)
            error.first.requestFocus()
            return false
        }
        return true
    }

    private fun registrar() {
        val nombre = etNombre.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()

        if (!camposValidos(nombre, email, password, confirmPassword)) return

        btnSignUp.isEnabled = false

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser

                    val userData = hashMapOf( //Armando el contenido del documento
                        "email" to email,
                        "nombre" to nombre,
                        "rol" to "usuario"
                    )

                    user?.uid?.let { uid ->  //  identificador único que Firebase le asigna a cada usuario.
                        db.collection("usuarios").document(uid)
                            .set(userData)
                    }

                    auth.setLanguageCode(LocaleHelper.getSavedLanguage(this))
                    user?.sendEmailVerification()
                    Toast.makeText(this, R.string.sign_up_success, Toast.LENGTH_LONG).show()

                    // Firebase ya dejó la sesión iniciada: entramos directo
                    val intent = Intent(this, HomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    btnSignUp.isEnabled = true
                    Toast.makeText(this, SignInActivity.mensajeDeError(this, task.exception), Toast.LENGTH_LONG).show()
                }
            }
    }
}
