package com.example.applaboratorio

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.EditText
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth

class SignUpActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnSignUp: Button
    private lateinit var tvGoToSignIn: TextView
    private lateinit var btnChangeLanguage: Button
    private lateinit var auth: FirebaseAuth

    override fun attachBaseContext(newBase: Context) { // . Aquí leemos qué idioma había guardado el usuario (getSavedLanguage) y lo aplicamos (setLocale) antes de que Android empiece a "inflar" (dibujar) el layout
        val language = LocaleHelper.getSavedLanguage(newBase)
        LocaleHelper.setLocale(newBase, language)
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnSignUp = findViewById(R.id.btnSignUp)
        tvGoToSignIn = findViewById(R.id.tvGoToSignIn)
        btnChangeLanguage = findViewById(R.id.btnChangeLanguage)

        auth = FirebaseAuth.getInstance()

        btnSignUp.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            if (password != confirmPassword) {
                Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        user?.sendEmailVerification()
                            ?.addOnCompleteListener { verificationTask ->
                                if (verificationTask.isSuccessful) {
                                    Toast.makeText(this, "Cuenta creada. Revisa tu correo para verificar tu cuenta", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(this, "Cuenta creada, pero no se pudo enviar el correo de verificación", Toast.LENGTH_LONG).show()
                                }
                            }
                    } else {
                        Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        tvGoToSignIn.setOnClickListener {
            val intent = Intent(this, SignInActivity::class.java)
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