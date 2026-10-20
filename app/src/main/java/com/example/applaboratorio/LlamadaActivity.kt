package com.example.applaboratorio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.Locale

// Pantalla de la videollamada propia (WebRTC), para quien llama y para quien recibe.
// El audio y el video van directo entre los dos dispositivos, cifrados. Firestore solo lleva
// la señalización: oferta y respuesta en videollamadas/{callId} y los candidatos de conexión
// en candidatos_de (quien llama) y candidatos_para (quien recibe).
class LlamadaActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LlamadaActivity"

        private const val EXTRA_LLAMA = "LLAMA"
        private const val EXTRA_OTRO_UID = "OTRO_UID"
        private const val EXTRA_OTRO_NOMBRE = "OTRO_NOMBRE"
        private const val EXTRA_MI_NOMBRE = "MI_NOMBRE"
        private const val EXTRA_CHAT_ID = "CHAT_ID"
        private const val EXTRA_CALL_ID = "CALL_ID"

        // Ids de nuestras pistas; el otro lado no los usa para nada
        private const val ID_STREAM = "famessage"
        private const val ID_AUDIO = "audio0"
        private const val ID_VIDEO = "video0"

        // Buena imagen sin gastar demasiada batería ni datos
        private const val ANCHO = 640
        private const val ALTO = 480
        private const val FPS = 30

        // initialize() carga la librería nativa: una sola vez por proceso
        private var webRtcIniciado = false

        // AppLaboratorio lo consulta para ignorar llamadas entrantes mientras hablamos ("ocupado")
        var enCurso = false
            private set

        fun intentLlamar(
            context: Context,
            otroUid: String,
            otroNombre: String,
            miNombre: String,
            chatId: String
        ) = Intent(context, LlamadaActivity::class.java)
            .putExtra(EXTRA_LLAMA, true)
            .putExtra(EXTRA_OTRO_UID, otroUid)
            .putExtra(EXTRA_OTRO_NOMBRE, otroNombre)
            .putExtra(EXTRA_MI_NOMBRE, miNombre)
            .putExtra(EXTRA_CHAT_ID, chatId)

        fun intentContestar(context: Context, callId: String, deUid: String, deNombre: String) =
            Intent(context, LlamadaActivity::class.java)
                .putExtra(EXTRA_LLAMA, false)
                .putExtra(EXTRA_CALL_ID, callId)
                .putExtra(EXTRA_OTRO_UID, deUid)
                .putExtra(EXTRA_OTRO_NOMBRE, deNombre)
    }

    private val db = FirebaseFirestore.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private val escuchas = mutableListOf<ListenerRegistration>()

    private var esQuienLlama = false
    private var miUid = ""
    private var miNombre = ""
    private var otroUid = ""
    private var otroNombre = ""
    private var chatId = ""
    private var callId = ""
    private var timestamp = 0L
    private lateinit var refLlamada: DocumentReference

    private lateinit var vistaRemota: SurfaceViewRenderer
    private lateinit var vistaLocal: SurfaceViewRenderer
    private lateinit var marcoLocal: View
    private lateinit var aro: View
    private lateinit var barraSuperior: View
    private lateinit var barraControles: View
    private lateinit var tvEstado: TextView
    private lateinit var btnMicrofono: MaterialButton
    private lateinit var btnCamara: MaterialButton
    private lateinit var btnCambiarCamara: MaterialButton

    private var eglBase: EglBase? = null
    private var moduloAudio: JavaAudioDeviceModule? = null
    private var fabrica: PeerConnectionFactory? = null
    private var conexion: PeerConnection? = null
    private var capturador: CameraVideoCapturer? = null
    private var ayudanteTextura: SurfaceTextureHelper? = null
    private var fuenteVideo: VideoSource? = null
    private var fuenteAudio: AudioSource? = null
    private var pistaVideo: VideoTrack? = null
    private var pistaAudio: AudioTrack? = null
    private var pistaRemota: VideoTrack? = null

    // Los candidatos del otro que llegan antes que su oferta/respuesta esperan aquí
    private val candidatosPendientes = mutableListOf<IceCandidate>()
    private var hayDescripcionRemota = false
    private var ofertaEnviada = false
    private var respuestaRecibida = false

    private var terminada = false
    private var conectadaDesde = 0L
    private var micActivo = true
    private var camaraActiva = true
    private var cambiandoCamara = false
    // La cámara quedó detenida (segundo plano o Android nos la quitó): onStart la vuelve a abrir
    private var capturaPausada = false

    // Cómo estaba el audio del teléfono, para dejarlo igual al colgar
    private var audioManager: AudioManager? = null
    private var pedidoFoco: AudioFocusRequest? = null
    private var modoAudioPrevio = AudioManager.MODE_NORMAL
    private var altavozPrevio = false

    private val cronometro = object : Runnable {
        override fun run() {
            val segundos = (SystemClock.elapsedRealtime() - conectadaDesde) / 1000
            tvEstado.text = String.format(Locale.ROOT, "%02d:%02d", segundos / 60, segundos % 60)
            handler.postDelayed(this, 1000)
        }
    }

    // Quien llama: el otro no respondió a tiempo
    private val sinRespuesta = Runnable {
        if (!respuestaRecibida) cortarConAviso(getString(R.string.call_missed, otroNombre))
    }

    // Ya hay oferta y respuesta pero los dos dispositivos no logran encontrarse
    private val sinConexion = Runnable { cortarConAviso(getString(R.string.call_connect_failed)) }

    // La conexión se cayó y no volvió
    private val cortePorDesconexion = Runnable {
        cortarConAviso(getString(R.string.call_connection_lost))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Si Android recreó la pantalla (p. ej. tras cerrar la app por memoria) esa llamada ya no existe
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (savedInstanceState != null || uid == null || enCurso) {
            terminada = true
            finish()
            return
        }
        // Sin cámara y micrófono no se crea nada en Firestore
        if (!tienePermisos()) {
            Toast.makeText(this, R.string.permissions_needed, Toast.LENGTH_LONG).show()
            terminada = true
            finish()
            return
        }

        miUid = uid
        esQuienLlama = intent.getBooleanExtra(EXTRA_LLAMA, false)
        otroUid = intent.getStringExtra(EXTRA_OTRO_UID).orEmpty()
        otroNombre = intent.getStringExtra(EXTRA_OTRO_NOMBRE).orEmpty().ifBlank { "Usuario" }
        if (esQuienLlama) {
            miNombre = intent.getStringExtra(EXTRA_MI_NOMBRE).orEmpty().ifBlank { "Usuario" }
            chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty()
                .ifBlank { Videollamada.chatIdEntre(miUid, otroUid) }
            timestamp = System.currentTimeMillis()
            callId = Videollamada.callIdPara(chatId, timestamp)
        } else {
            callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        }

        // Un callId válido es uidA_uidB_timestamp y nosotros somos uno de los dos
        val partes = callId.split("_")
        if (otroUid.isEmpty() || partes.size != 3 || miUid !in partes) {
            Toast.makeText(this, R.string.call_failed, Toast.LENGTH_LONG).show()
            terminada = true
            finish()
            return
        }
        refLlamada = db.collection(Videollamada.COLECCION_SENALES).document(callId)
        enCurso = true

        mostrarSobrePantallaBloqueada()
        activarBordeABorde()
        setContentView(R.layout.activity_llamada)
        prepararVistas()
        configurarAudio()

        val listo = try {
            prepararWebRtc()
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo preparar WebRTC", e)
            false
        }
        if (!listo) {
            // Quien recibe sí avisa, para que al otro no le quede sonando
            cortarConAviso(getString(R.string.call_failed), avisarAlOtro = !esQuienLlama)
            return
        }

        if (esQuienLlama) empezarComoQuienLlama() else empezarComoQuienRecibe()
    }

    // En segundo plano Android le quita la cámara a la app y WebRTC no la reabre solo:
    // la soltamos al salir y la retomamos al volver (el micrófono sí vuelve solo)
    override fun onStop() {
        super.onStop()
        if (terminada) return
        capturador?.let {
            try {
                it.stopCapture()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            capturaPausada = true
        }
    }

    override fun onStart() {
        super.onStart()
        if (capturaPausada && !terminada) {
            capturaPausada = false
            capturador?.startCapture(ANCHO, ALTO, FPS)
        }
    }

    override fun onDestroy() {
        // Por si Android cierra la pantalla sin pasar por colgar
        terminar()
        super.onDestroy()
    }

    private fun tienePermisos() =
        listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun mostrarSobrePantallaBloqueada() {
        // Si contestamos con el teléfono bloqueado, la llamada sigue a la vista
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        // Que la pantalla no se apague mientras hablamos
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun prepararVistas() {
        vistaRemota = findViewById(R.id.vistaRemota)
        vistaLocal = findViewById(R.id.vistaLocal)
        marcoLocal = findViewById(R.id.marcoLocal)
        aro = findViewById(R.id.aro)
        barraSuperior = findViewById(R.id.barraSuperior)
        barraControles = findViewById(R.id.barraControles)
        tvEstado = findViewById(R.id.tvEstadoLlamada)
        btnMicrofono = findViewById(R.id.btnMicrofono)
        btnCamara = findViewById(R.id.btnCamara)
        btnCambiarCamara = findViewById(R.id.btnCambiarCamara)

        barraSuperior.dejarEspacioArriba()
        barraControles.dejarEspacioAbajo()

        findViewById<TextView>(R.id.tvNombreLlamada).text = otroNombre
        tvEstado.setText(if (esQuienLlama) R.string.call_calling else R.string.call_connecting)

        val tvAvatar = findViewById<TextView>(R.id.tvAvatarLlamada)
        val ivAvatar = findViewById<ImageView>(R.id.ivAvatarLlamada)
        Avatar.mostrar(tvAvatar, ivAvatar, otroNombre, "", colorear = false)
        db.collection("usuarios").document(otroUid).get().addOnSuccessListener { doc ->
            if (!terminada) {
                Avatar.mostrar(tvAvatar, ivAvatar, otroNombre, Usuario.desde(doc).foto, colorear = false)
            }
        }

        btnMicrofono.setOnClickListener { alternarMicrofono() }
        btnCamara.setOnClickListener { alternarCamara() }
        btnCambiarCamara.setOnClickListener { cambiarCamara() }
        findViewById<View>(R.id.btnColgar).setOnClickListener { terminar() }

        // Atrás = colgar
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = terminar()
        })
    }

    private fun configurarAudio() {
        val am = getSystemService(AudioManager::class.java) ?: return
        audioManager = am
        modoAudioPrevio = am.mode
        @Suppress("DEPRECATION")
        altavozPrevio = am.isSpeakerphoneOn
        // Los botones de volumen controlan la llamada, no la música
        volumeControlStream = AudioManager.STREAM_VOICE_CALL

        // Pausa la música u otros sonidos de otras apps mientras dure la llamada
        val foco = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
        am.requestAudioFocus(foco)
        pedidoFoco = foco

        // Modo llamada (activa la cancelación de eco del teléfono) y altavoz encendido
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?.let { am.setCommunicationDevice(it) }
        } else {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = true
        }
    }

    private fun restaurarAudio() {
        val am = audioManager ?: return
        audioManager = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = altavozPrevio
        }
        am.mode = modoAudioPrevio
        pedidoFoco?.let { am.abandonAudioFocusRequest(it) }
        pedidoFoco = null
    }

    private fun prepararWebRtc(): Boolean {
        if (!webRtcIniciado) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                    .createInitializationOptions()
            )
            webRtcIniciado = true
        }

        // Un solo contexto de OpenGL compartido por la cámara, los codificadores y las dos vistas
        val egl = EglBase.create()
        eglBase = egl
        val contextoEgl = egl.eglBaseContext

        vistaRemota.init(contextoEgl, null)
        // Llena la pantalla si el video viene en la misma orientación; si no, se ve entero
        vistaRemota.setScalingType(
            RendererCommon.ScalingType.SCALE_ASPECT_FILL,
            RendererCommon.ScalingType.SCALE_ASPECT_FIT
        )
        vistaRemota.setEnableHardwareScaler(true)

        vistaLocal.init(contextoEgl, null)
        vistaLocal.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        vistaLocal.setEnableHardwareScaler(true)
        vistaLocal.setMirror(true)
        // Las dos vistas son SurfaceView: sin esto la ventanita quedaría debajo del video del otro
        @Suppress("DEPRECATION")
        vistaLocal.setZOrderMediaOverlay(true)

        val audio = JavaAudioDeviceModule.builder(applicationContext).createAudioDeviceModule()
        moduloAudio = audio
        val f = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audio)
            // Codifica por hardware si el teléfono puede y, si no, por software (VP8/VP9)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(contextoEgl, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(contextoEgl))
            .createPeerConnectionFactory()
        fabrica = f

        val fuenteA = f.createAudioSource(MediaConstraints())
        fuenteAudio = fuenteA
        pistaAudio = f.createAudioTrack(ID_AUDIO, fuenteA)
        iniciarCamara(f, contextoEgl)

        val configuracion = PeerConnection.RTCConfiguration(Videollamada.SERVIDORES_ICE).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val pc = f.createPeerConnection(configuracion, observadorConexion) ?: return false
        conexion = pc
        pistaAudio?.let { pc.addTrack(it, listOf(ID_STREAM)) }
        pistaVideo?.let { pc.addTrack(it, listOf(ID_STREAM)) }
        // Sin cámara propia igual pedimos el video del otro: la respuesta no puede agregarlo
        if (esQuienLlama && pistaVideo == null) {
            pc.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
            )
        }
        return true
    }

    private fun iniciarCamara(f: PeerConnectionFactory, contextoEgl: EglBase.Context) {
        // Camera2 en casi todos los teléfonos; Camera1 solo en los muy viejos que no la soportan
        val enumerador = if (Camera2Enumerator.isSupported(this)) {
            Camera2Enumerator(this)
        } else {
            Camera1Enumerator(true)
        }
        val camaras = enumerador.deviceNames
        val elegida = camaras.firstOrNull { enumerador.isFrontFacing(it) } ?: camaras.firstOrNull()
        val cap = if (elegida != null) enumerador.createCapturer(elegida, eventosCamara) else null
        if (elegida == null || cap == null) {
            // Sin cámara la llamada sigue solo con audio
            marcoLocal.visibility = View.GONE
            btnCamara.visibility = View.GONE
            btnCambiarCamara.visibility = View.GONE
            return
        }
        btnCambiarCamara.visibility = if (camaras.size > 1) View.VISIBLE else View.GONE
        vistaLocal.setMirror(enumerador.isFrontFacing(elegida))

        val ayudante = SurfaceTextureHelper.create("CapturaCamara", contextoEgl)
        val fuente = f.createVideoSource(cap.isScreencast)
        cap.initialize(ayudante, applicationContext, fuente.capturerObserver)
        cap.startCapture(ANCHO, ALTO, FPS)
        val pista = f.createVideoTrack(ID_VIDEO, fuente)
        pista.addSink(vistaLocal)

        capturador = cap
        ayudanteTextura = ayudante
        fuenteVideo = fuente
        pistaVideo = pista
    }

    // Si Android nos quita la cámara (p. ej. otra app la abrió), WebRTC la detiene y no la
    // reabre: lo anotamos para retomarla en onStart. Llegan en el hilo de la cámara
    private val eventosCamara = object : CameraVideoCapturer.CameraEventsHandler {
        override fun onCameraError(error: String?) {
            Log.w(TAG, "Error de cámara: $error")
            runOnUiThread { if (!terminada) capturaPausada = true }
        }

        override fun onCameraDisconnected() {
            Log.w(TAG, "Android le quitó la cámara a la app")
            runOnUiThread { if (!terminada) capturaPausada = true }
        }

        // Obligatorios en la interfaz, pero no los necesitamos
        override fun onCameraFreezed(error: String?) {}
        override fun onCameraOpening(camara: String?) {}
        override fun onFirstFrameAvailable() {}
        override fun onCameraClosed() {}
    }

    // Los callbacks de WebRTC llegan en otro hilo: todo se sigue en el hilo principal
    private val observadorConexion = object : PeerConnection.Observer {
        override fun onIceCandidate(candidato: IceCandidate?) {
            if (candidato != null) runOnUiThread { if (!terminada) publicarCandidato(candidato) }
        }

        override fun onConnectionChange(estado: PeerConnection.PeerConnectionState?) {
            runOnUiThread { if (!terminada) cambioDeConexion(estado) }
        }

        // Llegó una pista del otro: si es la de video, va a la vista grande
        override fun onTrack(transceptor: RtpTransceiver?) {
            val pista = transceptor?.receiver?.track() as? VideoTrack ?: return
            runOnUiThread {
                if (!terminada) {
                    pistaRemota = pista
                    pista.addSink(vistaRemota)
                    // Por si llega después de conectar
                    if (conectadaDesde != 0L) mostrarVideoRemoto()
                }
            }
        }

        // Obligatorios en la interfaz, pero no los necesitamos
        override fun onSignalingChange(estado: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(estado: PeerConnection.IceConnectionState?) {}
        override fun onIceConnectionReceivingChange(recibiendo: Boolean) {}
        override fun onIceGatheringChange(estado: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(candidatos: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(canal: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
    }

    private fun observadorSdp(
        alCrear: (SessionDescription) -> Unit = {},
        alFijar: () -> Unit = {}
    ) = object : SdpObserver {
        override fun onCreateSuccess(descripcion: SessionDescription?) {
            if (descripcion != null) runOnUiThread { if (!terminada) alCrear(descripcion) }
        }

        override fun onSetSuccess() = runOnUiThread { if (!terminada) alFijar() }
        override fun onCreateFailure(error: String?) = falloSdp(error)
        override fun onSetFailure(error: String?) = falloSdp(error)
    }

    private fun falloSdp(error: String?) {
        Log.e(TAG, "Error de SDP: $error")
        runOnUiThread { cortarConAviso(getString(R.string.call_failed)) }
    }

    // ---------- Quien llama ----------

    private fun empezarComoQuienLlama() {
        handler.postDelayed(sinRespuesta, Videollamada.SEGUNDOS_SIN_RESPUESTA * 1000L)
        escucharDocumentoLlamada()
        escucharCandidatos(Videollamada.CANDIDATOS_PARA)

        conexion?.createOffer(observadorSdp(alCrear = { oferta ->
            conexion?.setLocalDescription(observadorSdp(alFijar = { publicarOferta(oferta) }), oferta)
        }), MediaConstraints())
    }

    private fun publicarOferta(oferta: SessionDescription) {
        ofertaEnviada = true
        refLlamada.set(
            hashMapOf(
                "de" to miUid,
                "para" to otroUid,
                "timestamp" to timestamp,
                "offer" to oferta.comoMapa()
            )
        ).addOnSuccessListener {
            // Recién con la oferta guardada hacemos sonar al otro: así ya la encuentra al contestar
            if (!terminada) hacerSonar()
        }.addOnFailureListener { e ->
            Log.e(TAG, "No se pudo guardar la oferta", e)
            cortarConAviso(getString(R.string.call_failed))
        }
    }

    private fun hacerSonar() {
        db.collection(Videollamada.COLECCION).document(otroUid)
            .set(
                hashMapOf(
                    "de" to miUid,
                    "deNombre" to miNombre,
                    "sala" to callId,
                    "estado" to Videollamada.SONANDO,
                    "timestamp" to timestamp
                )
            )
            .addOnSuccessListener { if (!terminada) seguirTimbre() }
            .addOnFailureListener { e ->
                Log.e(TAG, "No se pudo hacer sonar al otro", e)
                cortarConAviso(getString(R.string.call_failed))
            }
        ChatActivity.guardarMensaje(chatId, miUid, otroUid, getString(R.string.chat_notice_call))
    }

    // Si el otro rechaza, no contesta o acepta (solo cuenta si sigue siendo esta llamada)
    private fun seguirTimbre() {
        escuchas += db.collection(Videollamada.COLECCION).document(otroUid)
            .addSnapshotListener { doc, _ ->
                if (terminada || respuestaRecibida || doc == null ||
                    doc.getLong("timestamp") != timestamp
                ) return@addSnapshotListener
                when (doc.getString("estado")) {
                    Videollamada.RECHAZADA -> cortarConAviso(
                        getString(R.string.call_rejected, otroNombre), avisarAlOtro = false
                    )
                    Videollamada.PERDIDA -> cortarConAviso(
                        getString(R.string.call_missed, otroNombre), avisarAlOtro = false
                    )
                    Videollamada.ACEPTADA -> tvEstado.setText(R.string.call_connecting)
                }
            }
    }

    // ---------- Quien recibe ----------

    private fun empezarComoQuienRecibe() {
        refLlamada.get()
            .addOnSuccessListener { doc ->
                if (terminada) return@addOnSuccessListener
                val oferta = descripcionDesde(doc.get("offer"))
                if (!doc.exists() || oferta == null || doc.getBoolean("colgada") == true) {
                    cortarConAviso(getString(R.string.call_not_available), avisarAlOtro = false)
                    return@addOnSuccessListener
                }
                escucharDocumentoLlamada()
                escucharCandidatos(Videollamada.CANDIDATOS_DE)
                esperarConexion()
                responderOferta(oferta)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "No se pudo leer la llamada", e)
                cortarConAviso(getString(R.string.call_failed))
            }
    }

    private fun responderOferta(oferta: SessionDescription) {
        conexion?.setRemoteDescription(observadorSdp(alFijar = {
            descripcionRemotaLista()
            conexion?.createAnswer(observadorSdp(alCrear = { respuesta ->
                conexion?.setLocalDescription(
                    observadorSdp(alFijar = { publicarRespuesta(respuesta) }), respuesta
                )
            }), MediaConstraints())
        }), oferta)
    }

    private fun publicarRespuesta(respuesta: SessionDescription) {
        refLlamada.set(hashMapOf("answer" to respuesta.comoMapa()), SetOptions.merge())
            .addOnFailureListener { e ->
                Log.e(TAG, "No se pudo guardar la respuesta", e)
                cortarConAviso(getString(R.string.call_failed))
            }
    }

    // ---------- Ambos ----------

    // El documento de la llamada: la respuesta (le importa a quien llama) y si alguien colgó
    private fun escucharDocumentoLlamada() {
        escuchas += refLlamada.addSnapshotListener { doc, _ ->
            if (terminada || doc == null || !doc.exists()) return@addSnapshotListener
            if (doc.getBoolean("colgada") == true) {
                terminar(avisarAlOtro = false)
                return@addSnapshotListener
            }
            if (esQuienLlama && !respuestaRecibida) {
                val respuesta = descripcionDesde(doc.get("answer")) ?: return@addSnapshotListener
                respuestaRecibida = true
                handler.removeCallbacks(sinRespuesta)
                tvEstado.setText(R.string.call_connecting)
                esperarConexion()
                conexion?.setRemoteDescription(
                    observadorSdp(alFijar = { descripcionRemotaLista() }), respuesta
                )
            }
        }
    }

    private fun escucharCandidatos(lado: String) {
        escuchas += refLlamada.collection(lado).addSnapshotListener { snapshot, _ ->
            if (terminada || snapshot == null) return@addSnapshotListener
            // Solo los agregados desde el último aviso: los candidatos no cambian ni se borran
            for (cambio in snapshot.documentChanges) {
                if (cambio.type != DocumentChange.Type.ADDED) continue
                val datos = cambio.document
                val sdp = datos.getString("candidate")
                if (sdp.isNullOrEmpty()) continue
                // Firestore devuelve los números como Long
                val indice = (datos.get("sdpMLineIndex") as? Number)?.toInt() ?: 0
                agregarCandidatoRemoto(IceCandidate(datos.getString("sdpMid") ?: "", indice, sdp))
            }
        }
    }

    private fun publicarCandidato(candidato: IceCandidate) {
        val lado = if (esQuienLlama) Videollamada.CANDIDATOS_DE else Videollamada.CANDIDATOS_PARA
        refLlamada.collection(lado).add(
            hashMapOf<String, Any?>(
                "candidate" to candidato.sdp,
                "sdpMid" to candidato.sdpMid,
                "sdpMLineIndex" to candidato.sdpMLineIndex
            )
        )
    }

    private fun agregarCandidatoRemoto(candidato: IceCandidate) {
        if (hayDescripcionRemota) {
            conexion?.addIceCandidate(candidato)
        } else {
            candidatosPendientes += candidato
        }
    }

    // Ya tenemos la oferta/respuesta del otro: ahora sí sirven sus candidatos
    private fun descripcionRemotaLista() {
        hayDescripcionRemota = true
        candidatosPendientes.forEach { conexion?.addIceCandidate(it) }
        candidatosPendientes.clear()
    }

    private fun esperarConexion() {
        handler.removeCallbacks(sinConexion)
        handler.postDelayed(sinConexion, Videollamada.SEGUNDOS_PARA_CONECTAR * 1000L)
    }

    private fun cambioDeConexion(estado: PeerConnection.PeerConnectionState?) {
        when (estado) {
            PeerConnection.PeerConnectionState.CONNECTED -> {
                handler.removeCallbacks(sinConexion)
                handler.removeCallbacks(cortePorDesconexion)
                if (conectadaDesde == 0L) {
                    conectadaDesde = SystemClock.elapsedRealtime()
                    handler.post(cronometro)
                    // Si el otro no manda video (no tiene cámara) se queda su avatar, no una pantalla negra
                    if (pistaRemota != null) mostrarVideoRemoto()
                }
            }
            PeerConnection.PeerConnectionState.DISCONNECTED -> {
                // Puede ser un corte breve de la red: esperamos antes de colgar
                handler.removeCallbacks(cortePorDesconexion)
                handler.postDelayed(cortePorDesconexion, Videollamada.SEGUNDOS_DESCONECTADO * 1000L)
            }
            PeerConnection.PeerConnectionState.FAILED -> cortarConAviso(
                getString(
                    if (conectadaDesde == 0L) R.string.call_connect_failed
                    else R.string.call_connection_lost
                )
            )
            else -> {}
        }
    }

    private fun mostrarVideoRemoto() {
        vistaRemota.visibility = View.VISIBLE
        aro.visibility = View.GONE
        // Sombras arriba y abajo para que el texto y los botones se lean sobre el video
        barraSuperior.setBackgroundResource(R.drawable.bg_scrim_top)
        barraControles.setBackgroundResource(R.drawable.bg_scrim_bottom)
    }

    // ---------- Controles ----------

    private fun alternarMicrofono() {
        micActivo = !micActivo
        pistaAudio?.setEnabled(micActivo)
        pintarControl(btnMicrofono, micActivo, R.drawable.ic_mic, R.drawable.ic_mic_off)
        btnMicrofono.contentDescription =
            getString(if (micActivo) R.string.cd_mic_mute else R.string.cd_mic_unmute)
    }

    private fun alternarCamara() {
        val pista = pistaVideo ?: return
        camaraActiva = !camaraActiva
        // Con la pista apagada el otro ve negro; la cámara sigue abierta para volver al instante
        pista.setEnabled(camaraActiva)
        marcoLocal.visibility = if (camaraActiva) View.VISIBLE else View.INVISIBLE
        pintarControl(btnCamara, camaraActiva, R.drawable.ic_videocam, R.drawable.ic_videocam_off)
        btnCamara.contentDescription =
            getString(if (camaraActiva) R.string.cd_camera_off else R.string.cd_camera_on)
    }

    private fun cambiarCamara() {
        val cap = capturador ?: return
        if (cambiandoCamara) return
        cambiandoCamara = true
        cap.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(esFrontal: Boolean) = runOnUiThread {
                cambiandoCamara = false
                // Solo la frontal va en espejo, como cuando uno se mira
                if (!terminada) vistaLocal.setMirror(esFrontal)
            }

            override fun onCameraSwitchError(error: String?) = runOnUiThread {
                cambiandoCamara = false
                Log.w(TAG, "No se pudo cambiar de cámara: $error")
            }
        })
    }

    // Encendido: botón translúcido con icono blanco. Apagado: botón blanco, para que se note
    private fun pintarControl(boton: MaterialButton, encendido: Boolean, icono: Int, iconoApagado: Int) {
        boton.setIconResource(if (encendido) icono else iconoApagado)
        boton.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (encendido) R.color.glass_white else R.color.white)
        )
        boton.iconTint = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (encendido) R.color.white else R.color.brand_primary_dark)
        )
    }

    // ---------- Colgar ----------

    private fun cortarConAviso(texto: String, avisarAlOtro: Boolean = true) {
        if (terminada) return
        Toast.makeText(applicationContext, texto, Toast.LENGTH_LONG).show()
        terminar(avisarAlOtro)
    }

    // Se puede llamar varias veces (botón, atrás, el otro colgó, errores): solo actúa la primera
    private fun terminar(avisarAlOtro: Boolean = true) {
        if (terminada) return
        terminada = true
        enCurso = false
        handler.removeCallbacksAndMessages(null)
        escuchas.forEach { it.remove() }
        escuchas.clear()
        if (avisarAlOtro) avisarQueColgamos()
        liberarMedios()
        restaurarAudio()
        finish()
    }

    private fun avisarQueColgamos() {
        // Quien llama y todavía no guardó la oferta: no hay nada que avisar
        if (esQuienLlama && !ofertaEnviada) return
        refLlamada.set(hashMapOf("colgada" to true), SetOptions.merge())
        if (esQuienLlama) {
            // Si al otro le sigue sonando esta llamada, queda como perdida
            val refTimbre = db.collection(Videollamada.COLECCION).document(otroUid)
            val miTimestamp = timestamp
            refTimbre.get().addOnSuccessListener { doc ->
                if (doc.getLong("timestamp") == miTimestamp &&
                    doc.getString("estado") == Videollamada.SONANDO
                ) {
                    refTimbre.update("estado", Videollamada.PERDIDA)
                }
            }
        }
    }

    // El orden importa: primero se sueltan las vistas, luego la cámara, la conexión (que libera
    // la pista del otro), nuestras pistas y fuentes, y al final la fábrica y OpenGL
    private fun liberarMedios() {
        pistaRemota?.removeSink(vistaRemota)
        pistaRemota = null
        pistaVideo?.removeSink(vistaLocal)
        capturador?.let {
            try {
                it.stopCapture()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            it.dispose()
        }
        capturador = null
        conexion?.dispose() // dispose() ya hace close()
        conexion = null
        pistaVideo?.dispose()
        pistaVideo = null
        pistaAudio?.dispose()
        pistaAudio = null
        fuenteVideo?.dispose()
        fuenteVideo = null
        fuenteAudio?.dispose()
        fuenteAudio = null
        ayudanteTextura?.dispose()
        ayudanteTextura = null
        vistaLocal.release()
        vistaRemota.release()
        fabrica?.dispose()
        fabrica = null
        moduloAudio?.release()
        moduloAudio = null
        eglBase?.release()
        eglBase = null
    }

    // En Firestore se guarda { type: "offer"|"answer", sdp }, igual que lo escribe el navegador
    private fun SessionDescription.comoMapa() =
        hashMapOf("type" to type.canonicalForm(), "sdp" to description)

    private fun descripcionDesde(valor: Any?): SessionDescription? {
        val mapa = valor as? Map<*, *> ?: return null
        val tipo = mapa["type"] as? String ?: return null
        val sdp = mapa["sdp"] as? String ?: return null
        return try {
            SessionDescription(SessionDescription.Type.fromCanonicalForm(tipo), sdp)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
