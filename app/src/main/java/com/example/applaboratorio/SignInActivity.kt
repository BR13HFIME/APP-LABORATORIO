package com.example.applaboratorio

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Patterns
import android.widget.EditText
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.text.InputType
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore

class SignInActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSignIn: Button
    private lateinit var tvGoToSignUp: TextView
    private lateinit var btnChangeLanguage: Button
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Sesión persistente: si ya había iniciado sesión, vamos directo al inicio
        if (auth.currentUser != null) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        LocaleHelper.sincronizar(this)
        activarBordeABorde()
        setContentView(R.layout.activity_sign_in)
        findViewById<View>(R.id.hero).dejarEspacioArriba()
        findViewById<View>(R.id.scrollRoot).dejarEspacioAbajo()

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnSignIn = findViewById(R.id.btnSignIn)
        tvGoToSignUp = findViewById(R.id.tvGoToSignUp)
        btnChangeLanguage = findViewById(R.id.btnChangeLanguage)

        btnSignIn.setOnClickListener { iniciarSesion() }

        findViewById<View>(R.id.tvForgotPassword).setOnClickListener {
            mostrarRecuperarContrasena()
        }

        tvGoToSignUp.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }

        btnChangeLanguage.setOnClickListener {
            LocaleHelper.alternarIdioma(this)
        }
    }

    private fun iniciarSesion() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        // Firebase lanza una excepción (y cierra la app) si recibe campos vacíos
        if (email.isEmpty()) {
            etEmail.error = getString(R.string.error_required)
            etEmail.requestFocus()
            return
        }
        if (password.isEmpty()) {
            etPassword.error = getString(R.string.error_required)
            etPassword.requestFocus()
            return
        }

        btnSignIn.isEnabled = false

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid ?: ""
                    cargarRolYEntrar(uid, email)
                } else {
                    btnSignIn.isEnabled = true
                    Toast.makeText(this, mensajeDeError(this, task.exception), Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun mostrarRecuperarContrasena() {
        val campo = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            hint = getString(R.string.hint_email)
            setText(etEmail.text.toString().trim())
        }
        val margen = (24 * resources.displayMetrics.density).toInt()
        val contenedor = FrameLayout(this).apply {
            setPadding(margen, margen / 3, margen, 0)
            addView(campo)
        }

        val dialogo = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reset_title)
            .setMessage(R.string.reset_message)
            .setView(contenedor)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.reset_send, null)
            .show()

        // Validamos antes de cerrar el diálogo
        dialogo.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val email = campo.text.toString().trim()
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                campo.error = getString(R.string.error_email_invalid)
                return@setOnClickListener
            }
            // El correo de Firebase llega en el idioma de la app
            auth.setLanguageCode(LocaleHelper.getSavedLanguage(this))
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(this, R.string.reset_sent, Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, mensajeDeError(this, e), Toast.LENGTH_LONG).show()
                }
            dialogo.dismiss()
        }
    }

    private fun cargarRolYEntrar(uid: String, email: String) {
        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener { document ->
                // Cuentas que se quedaron sin documento de perfil: lo creamos para que
                // el usuario aparezca en la lista de chats
                if (!document.exists()) {
                    db.collection("usuarios").document(uid)
                        .set(hashMapOf("email" to email, "rol" to "usuario"))
                }
                irAHome(document.getString("rol") ?: "usuario")
            }
            .addOnFailureListener {
                // La sesión ya es válida: entramos como usuario normal aunque no se pueda leer el rol
                Toast.makeText(this, R.string.error_profile_read, Toast.LENGTH_LONG).show()
                irAHome("usuario")
            }
    }

    private fun irAHome(rol: String) {
        Toast.makeText(this, R.string.sign_in_success, Toast.LENGTH_SHORT).show()

        val intent = Intent(this, HomeActivity::class.java)
        intent.putExtra("ROL_USUARIO", rol)
        startActivity(intent)
        finish()
    }

    companion object {
        // Traduce los errores de Firebase a mensajes entendibles (en el idioma de la app)
        fun mensajeDeError(context: Context, error: Exception?): String = when (error) {
            is FirebaseAuthUserCollisionException -> context.getString(R.string.error_email_in_use)
            is FirebaseAuthInvalidCredentialsException,
            is FirebaseAuthInvalidUserException -> context.getString(R.string.error_wrong_credentials)
            is FirebaseNetworkException -> context.getString(R.string.error_no_internet)
            is FirebaseTooManyRequestsException -> context.getString(R.string.error_too_many_requests)
            else -> context.getString(R.string.error_generic, error?.localizedMessage ?: "")
        }
    }
}
