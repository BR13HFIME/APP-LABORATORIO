package com.example.applaboratorio

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import org.jitsi.meet.sdk.JitsiMeetUserInfo
import java.net.URL

// Todo lo relacionado con abrir una videollamada y avisar al otro usuario.
// El aviso vive en llamadas/{uidDelQueRecibe}: { de, deNombre, sala, estado, timestamp }
object Videollamada {

    // meet.jit.si ya no sirve desde apps: exige iniciar sesión con Google/GitHub
    // para crear la sala y corta las llamadas embebidas a los 5 minutos.
    // meet.ffmuc.net es un servidor Jitsi comunitario que permite salas anónimas.
    private const val SERVIDOR_JITSI = "https://meet.ffmuc.net"

    const val COLECCION = "llamadas"
    const val SONANDO = "sonando"
    const val ACEPTADA = "aceptada"
    const val RECHAZADA = "rechazada"
    const val PERDIDA = "perdida"

    // Pasado este tiempo una llamada que nadie contestó ya no se muestra
    const val SEGUNDOS_TIMBRE = 40

    fun salaPara(chatId: String) = "AppLaboratorio_$chatId"

    fun chatIdEntre(uidA: String, uidB: String) =
        if (uidA < uidB) "${uidA}_${uidB}" else "${uidB}_${uidA}"

    /**
     * Hace sonar el teléfono del otro usuario (si tiene la app abierta).
     * Devuelve la marca de tiempo que identifica esta llamada (0 si no hay sesión).
     */
    fun llamar(otroUid: String, miNombre: String, sala: String): Long {
        val miUid = FirebaseAuth.getInstance().currentUser?.uid ?: return 0L
        val ahora = System.currentTimeMillis()
        FirebaseFirestore.getInstance().collection(COLECCION).document(otroUid)
            .set(
                hashMapOf(
                    "de" to miUid,
                    "deNombre" to miNombre,
                    "sala" to sala,
                    "estado" to SONANDO,
                    "timestamp" to ahora
                )
            )
        return ahora
    }

    fun responder(miUid: String, estado: String) {
        FirebaseFirestore.getInstance().collection(COLECCION).document(miUid)
            .update("estado", estado)
    }

    fun abrir(context: Context, sala: String, miNombre: String) {
        // El servidor pide un nombre para entrar; usamos el del perfil
        val userInfo = JitsiMeetUserInfo().apply { displayName = miNombre }

        val options = JitsiMeetConferenceOptions.Builder()
            .setServerURL(URL(SERVIDOR_JITSI))
            .setRoom(sala)
            .setUserInfo(userInfo)
            .setAudioMuted(false)
            .setVideoMuted(false)
            .setFeatureFlag("welcomepage.enabled", false)
            .setFeatureFlag("prejoinpage.enabled", false)
            .setFeatureFlag("lobby-mode.enabled", false)
            .setFeatureFlag("invite.enabled", false)
            .setFeatureFlag("add-people.enabled", false)
            .setFeatureFlag("calendar.enabled", false)
            .setFeatureFlag("call-integration.enabled", false)
            .build()

        JitsiMeetActivity.launch(context, options)
    }
}
