package com.example.applaboratorio

import com.google.firebase.firestore.FirebaseFirestore
import org.webrtc.PeerConnection

// Constantes y ayudas de la videollamada propia (WebRTC).
// El aviso de timbre vive en llamadas/{uidDelQueRecibe}: { de, deNombre, sala, estado, timestamp }
// y la señalización (oferta, respuesta y candidatos) en videollamadas/{callId}.
object Videollamada {

    const val COLECCION = "llamadas"
    const val SONANDO = "sonando"
    const val ACEPTADA = "aceptada"
    const val RECHAZADA = "rechazada"
    const val PERDIDA = "perdida"

    // Señalización: el documento de la llamada y los candidatos de cada lado
    const val COLECCION_SENALES = "videollamadas"
    const val CANDIDATOS_DE = "candidatos_de"
    const val CANDIDATOS_PARA = "candidatos_para"

    // Pasado este tiempo una llamada que nadie contestó ya no se muestra
    const val SEGUNDOS_TIMBRE = 40

    // Quien llama se rinde si en este tiempo no llegó la respuesta del otro
    const val SEGUNDOS_SIN_RESPUESTA = 45

    // Margen para que los dos teléfonos se encuentren una vez que ya hay oferta y respuesta
    const val SEGUNDOS_PARA_CONECTAR = 30

    // Una caída de la conexión más larga que esto corta la llamada
    const val SEGUNDOS_DESCONECTADO = 10

    // El STUN solo le dice a cada dispositivo cuál es su IP pública; no pasa audio ni video por él.
    // Si se deja la lista vacía, la llamada solo funciona con los dos dispositivos en la misma red Wi-Fi.
    // No hay servidor TURN: el audio y el video van directo entre los dos, cifrados (DTLS-SRTP).
    val SERVIDORES_ICE: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    fun chatIdEntre(uidA: String, uidB: String) =
        if (uidA < uidB) "${uidA}_${uidB}" else "${uidB}_${uidA}"

    // Los uid de Firebase nunca llevan "_", así que callId.split("_") da [uidA, uidB, timestamp];
    // las reglas de Firestore dependen de eso para saber quién participa
    fun callIdPara(chatId: String, timestamp: Long) = "${chatId}_$timestamp"

    fun responder(miUid: String, estado: String) {
        FirebaseFirestore.getInstance().collection(COLECCION).document(miUid)
            .update("estado", estado)
    }
}
