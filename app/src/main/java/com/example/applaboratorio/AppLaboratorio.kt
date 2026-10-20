package com.example.applaboratorio

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

// Mientras la app está abierta escuchamos llamadas/{miUid}: cuando alguien nos llama,
// mostramos la pantalla de llamada entrante encima de la pantalla que esté a la vista.
class AppLaboratorio : Application(), Application.ActivityLifecycleCallbacks {

    private var pantallasVisibles = 0
    private var pantallaActual: Activity? = null
    private var escucha: ListenerRegistration? = null
    private var uidEscuchado: String? = null
    private var llamadaPendiente: DocumentSnapshot? = null
    private var ultimaLlamadaMostrada = 0L

    override fun onCreate() {
        super.onCreate()
        LocaleHelper.aplicarIdiomaGuardado(this)
        registerActivityLifecycleCallbacks(this)
        FirebaseAuth.getInstance().addAuthStateListener { actualizarEscucha() }
    }

    private fun actualizarEscucha() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val debeEscuchar = uid != null && pantallasVisibles > 0
        if (debeEscuchar && uid == uidEscuchado) return

        escucha?.remove()
        escucha = null
        uidEscuchado = null
        if (!debeEscuchar || uid == null) return

        uidEscuchado = uid
        escucha = FirebaseFirestore.getInstance()
            .collection(Videollamada.COLECCION).document(uid)
            .addSnapshotListener { documento, error ->
                if (error != null || documento == null || !documento.exists()) return@addSnapshotListener
                revisarLlamada(documento)
            }
    }

    private fun revisarLlamada(documento: DocumentSnapshot) {
        if (documento.getString("estado") != Videollamada.SONANDO) return

        val timestamp = documento.getLong("timestamp") ?: 0L
        val antiguedad = System.currentTimeMillis() - timestamp
        // Margen extra por si los relojes de los dos teléfonos no coinciden exactamente
        if (antiguedad > (Videollamada.SEGUNDOS_TIMBRE + 20) * 1000L) return
        if (timestamp == ultimaLlamadaMostrada) return

        val actual = pantallaActual
        if (actual == null) {
            // Estamos cambiando de pantalla: la mostramos en cuanto aparezca la siguiente
            llamadaPendiente = documento
            return
        }
        // Si ya estamos en una llamada (o viendo otra entrante) no interrumpimos: "ocupado".
        // enCurso cubre también la llamada abierta detrás de otra pantalla
        if (actual is LlamadaEntranteActivity || actual is LlamadaActivity || LlamadaActivity.enCurso) return

        ultimaLlamadaMostrada = timestamp
        llamadaPendiente = null
        actual.startActivity(
            LlamadaEntranteActivity.crearIntent(
                actual,
                deUid = documento.getString("de") ?: "",
                deNombre = documento.getString("deNombre") ?: "",
                sala = documento.getString("sala") ?: "",
                timestamp = timestamp
            )
        )
    }

    override fun onActivityStarted(activity: Activity) {
        pantallasVisibles++
    }

    override fun onActivityResumed(activity: Activity) {
        pantallaActual = activity
        actualizarEscucha()
        llamadaPendiente?.let { revisarLlamada(it) }
    }

    override fun onActivityPaused(activity: Activity) {
        if (pantallaActual === activity) pantallaActual = null
    }

    override fun onActivityStopped(activity: Activity) {
        pantallasVisibles--
        actualizarEscucha()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
