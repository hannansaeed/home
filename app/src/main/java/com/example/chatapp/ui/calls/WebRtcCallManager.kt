package com.example.chatapp.ui.calls

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.webrtc.*
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.Executors

/**
 * Voice-only WebRTC PeerConnection & audio controller backed by Firestore SDP/ICE signaling.
 */
class WebRtcCallManager(
    context: Context,
    private val db: FirebaseFirestore,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pcExecutor = Executors.newSingleThreadExecutor()
    private val iceLock = Any()

    private var audioDeviceModule: AudioDeviceModule? = null
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private var callDocListener: ListenerRegistration? = null
    private var remoteCandidatesListener: ListenerRegistration? = null
    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false
    private var isSettingRemoteDescription = false
    @Volatile
    private var sessionEnded = false

    var isMuted by mutableStateOf(false)
        private set
    var isSpeakerOn by mutableStateOf(false)
        private set
    var connectionState by mutableStateOf("connecting")
        private set

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun ensureFactoryInitialized() {
        if (factory != null) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        val adm = JavaAudioDeviceModule.builder(appContext)
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setUseHardwareAcousticEchoCanceler(JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported())
            .setUseHardwareNoiseSuppressor(JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported())
            .createAudioDeviceModule()
        audioDeviceModule = adm

        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .createPeerConnectionFactory()
    }

    fun startCallerSession(
        callId: String,
        onRemoteHangup: () -> Unit,
    ) {
        pcExecutor.execute {
            setupSessionInternal(callId = callId, isCaller = true, onRemoteHangup = onRemoteHangup)
        }
    }

    fun acceptCalleeSession(
        callId: String,
        offerSdp: String,
        onRemoteHangup: () -> Unit,
    ) {
        pcExecutor.execute {
            setupSessionInternal(
                callId = callId,
                isCaller = false,
                initialOfferSdp = offerSdp,
                onRemoteHangup = onRemoteHangup,
            )
        }
    }

    private fun setupSessionInternal(
        callId: String,
        isCaller: Boolean,
        initialOfferSdp: String? = null,
        onRemoteHangup: () -> Unit,
    ) {
        releaseSessionInternal()
        ensureFactoryInitialized()

        synchronized(iceLock) {
            remoteDescriptionSet = false
            isSettingRemoteDescription = false
            pendingIceCandidates.clear()
            sessionEnded = false
        }
        runOnMain {
            isMuted = false
            connectionState = if (isCaller) "ringing" else "connecting"
        }
        acquireCommunicationAudioFocus()
        setSpeakerphone(false)

        val pcFactory = factory ?: return
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.relay.metered.ca:80").createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
            PeerConnection.IceServer.builder("turns:openrelay.metered.ca:443?transport=tcp")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val candidatesCollection = if (isCaller) "callerCandidates" else "calleeCandidates"
        val peerCandidatesCollection = if (isCaller) "calleeCandidates" else "callerCandidates"

        val pc = pcFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null || sessionEnded) return
                db.collection("call_sessions").document(callId)
                    .collection(candidatesCollection)
                    .add(
                        mapOf(
                            "sdpMid" to (candidate.sdpMid ?: ""),
                            "sdpMLineIndex" to candidate.sdpMLineIndex,
                            "sdp" to candidate.sdp,
                        )
                    )
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is AudioTrack) {
                    track.setEnabled(true)
                    track.setVolume(1.0)
                }
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                val mapped = when (newState) {
                    PeerConnection.IceConnectionState.CHECKING -> "connecting"
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> "connected"
                    PeerConnection.IceConnectionState.DISCONNECTED -> "reconnecting"
                    PeerConnection.IceConnectionState.CLOSED -> "ended"
                    PeerConnection.IceConnectionState.FAILED -> "failed"
                    else -> null
                }
                if (mapped != null) {
                    runOnMain { connectionState = mapped }
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                val mapped = when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTING -> "connecting"
                    PeerConnection.PeerConnectionState.CONNECTED -> "connected"
                    PeerConnection.PeerConnectionState.DISCONNECTED -> "reconnecting"
                    PeerConnection.PeerConnectionState.FAILED -> "failed"
                    PeerConnection.PeerConnectionState.CLOSED -> "ended"
                    else -> null
                }
                if (mapped != null) {
                    runOnMain { connectionState = mapped }
                }
            }

            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {
                stream?.audioTracks?.forEach { track ->
                    track.setEnabled(true)
                    track.setVolume(1.0)
                }
            }
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        }) ?: return

        peerConnection = pc

        // Create local mic track (caller binds before createOffer; callee binds to offer's audio transceiver below)
        try {
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            }
            localAudioSource = pcFactory.createAudioSource(audioConstraints)
            localAudioTrack = pcFactory.createAudioTrack("moth_audio", localAudioSource).apply {
                setEnabled(true)
                setVolume(1.0)
            }
            if (isCaller) {
                pc.addTrack(localAudioTrack, listOf("moth_stream"))
            }
        } catch (_: Exception) {}

        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        if (isCaller) {
            pc.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    if (desc == null || sessionEnded) return
                    pcExecutor.execute {
                        if (sessionEnded) return@execute
                        pc.setLocalDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                if (sessionEnded) return
                                db.collection("call_sessions").document(callId)
                                    .update("offerSdp", desc.description)
                            }

                            override fun onSetFailure(p0: String?) {
                                runOnMain { connectionState = "failed" }
                            }
                        }, desc)
                    }
                }

                override fun onCreateFailure(p0: String?) {
                    runOnMain { connectionState = "failed" }
                }
            }, mediaConstraints)
        } else if (!initialOfferSdp.isNullOrBlank()) {
            val offerDesc = SessionDescription(SessionDescription.Type.OFFER, initialOfferSdp)
            pc.setRemoteDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    if (sessionEnded) return
                    pcExecutor.execute {
                        if (sessionEnded) return@execute
                        // Unified Plan callee: attach local track to the offer's audio transceiver (SEND_RECV)
                        try {
                            val track = localAudioTrack
                            if (track != null) {
                                val audioTransceiver = pc.transceivers.firstOrNull {
                                    it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO
                                }
                                if (audioTransceiver != null) {
                                    audioTransceiver.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
                                    audioTransceiver.sender.setTrack(track, false)
                                } else {
                                    pc.addTrack(track, listOf("moth_stream"))
                                }
                            }
                        } catch (_: Exception) {
                            try {
                                localAudioTrack?.let { pc.addTrack(it, listOf("moth_stream")) }
                            } catch (_: Exception) {}
                        }

                        synchronized(iceLock) {
                            remoteDescriptionSet = true
                            isSettingRemoteDescription = false
                            flushPendingCandidatesLocked()
                        }
                        pc.createAnswer(object : SimpleSdpObserver() {
                            override fun onCreateSuccess(answer: SessionDescription?) {
                                if (answer == null || sessionEnded) return
                                pcExecutor.execute {
                                    if (sessionEnded) return@execute
                                    pc.setLocalDescription(object : SimpleSdpObserver() {
                                        override fun onSetSuccess() {
                                            if (sessionEnded) return
                                            db.collection("call_sessions").document(callId)
                                                .update(
                                                    mapOf(
                                                        "answerSdp" to answer.description,
                                                        "status" to "ACTIVE",
                                                    )
                                                )
                                        }

                                        override fun onSetFailure(p0: String?) {
                                            runOnMain { connectionState = "failed" }
                                        }
                                    }, answer)
                                }
                            }

                            override fun onCreateFailure(p0: String?) {
                                runOnMain { connectionState = "failed" }
                            }
                        }, mediaConstraints)
                    }
                }

                override fun onSetFailure(p0: String?) {
                    runOnMain { connectionState = "failed" }
                }
            }, offerDesc)
        }

        // Listen for peer ICE candidates so none are missed while waiting for SDP
        remoteCandidatesListener?.remove()
        remoteCandidatesListener = db.collection("call_sessions").document(callId)
            .collection(peerCandidatesCollection)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null || sessionEnded) return@addSnapshotListener
                val added = snap.documentChanges
                    .filter { it.type == DocumentChange.Type.ADDED }
                    .mapNotNull { change ->
                        val doc = change.document
                        val sdp = doc.getString("sdp") ?: return@mapNotNull null
                        val sdpMid = doc.getString("sdpMid").orEmpty()
                        val idx = (doc.getLong("sdpMLineIndex") ?: 0L).toInt()
                        IceCandidate(sdpMid.ifEmpty { "0" }, idx, sdp)
                    }
                if (added.isNotEmpty()) {
                    pcExecutor.execute {
                        if (sessionEnded) return@execute
                        synchronized(iceLock) {
                            for (candidate in added) {
                                if (remoteDescriptionSet) {
                                    peerConnection?.addIceCandidate(candidate)
                                } else {
                                    pendingIceCandidates.add(candidate)
                                }
                            }
                        }
                    }
                }
            }

        // Listen for call status + SDP answer
        callDocListener?.remove()
        callDocListener = db.collection("call_sessions").document(callId)
            .addSnapshotListener { snap, error ->
                if (error != null || sessionEnded) return@addSnapshotListener
                if (snap == null || !snap.exists()) {
                    runOnMain { onRemoteHangup() }
                    return@addSnapshotListener
                }
                val status = snap.getString("status") ?: ""
                if (status == "ENDED" || status == "DECLINED" || status == "MISSED") {
                    runOnMain { onRemoteHangup() }
                    return@addSnapshotListener
                }
                if (isCaller) {
                    val answerSdp = snap.getString("answerSdp")
                    if (!answerSdp.isNullOrBlank()) {
                        runOnMain {
                            if (connectionState == "ringing") connectionState = "connecting"
                        }
                        pcExecutor.execute {
                            if (sessionEnded) return@execute
                            val canSet = synchronized(iceLock) {
                                if (!remoteDescriptionSet && !isSettingRemoteDescription) {
                                    isSettingRemoteDescription = true
                                    true
                                } else {
                                    false
                                }
                            }
                            if (!canSet) return@execute
                            val answerDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                            peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                                override fun onSetSuccess() {
                                    if (sessionEnded) return
                                    pcExecutor.execute {
                                        synchronized(iceLock) {
                                            remoteDescriptionSet = true
                                            isSettingRemoteDescription = false
                                            flushPendingCandidatesLocked()
                                        }
                                    }
                                }

                                override fun onSetFailure(p0: String?) {
                                    synchronized(iceLock) {
                                        isSettingRemoteDescription = false
                                    }
                                    runOnMain { connectionState = "failed" }
                                }
                            }, answerDesc)
                        }
                    }
                }
            }
    }

    private fun flushPendingCandidatesLocked() {
        val pc = peerConnection ?: return
        pendingIceCandidates.forEach { pc.addIceCandidate(it) }
        pendingIceCandidates.clear()
    }

    fun toggleMute() {
        val next = !isMuted
        runOnMain { isMuted = next }
        pcExecutor.execute {
            localAudioTrack?.setEnabled(!next)
        }
        try {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.isMicrophoneMute = next
        } catch (_: Exception) {}
    }

    fun toggleSpeaker() {
        setSpeakerphone(!isSpeakerOn)
    }

    @Suppress("DEPRECATION")
    private fun acquireCommunicationAudioFocus() {
        try {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isMicrophoneMute = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { }
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                am.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
        } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    private fun setSpeakerphone(enabled: Boolean) {
        runOnMain { isSpeakerOn = enabled }
        try {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val targetType = if (enabled) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } else {
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                val device = am.availableCommunicationDevices.firstOrNull { it.type == targetType }
                if (device != null) {
                    am.setCommunicationDevice(device)
                } else {
                    am.isSpeakerphoneOn = enabled
                }
            } else {
                am.isSpeakerphoneOn = enabled
            }
        } catch (_: Exception) {}
    }

    fun endSession() {
        synchronized(iceLock) {
            sessionEnded = true
        }
        pcExecutor.execute {
            releaseSessionInternal()
        }
    }

    @Suppress("DEPRECATION")
    private fun releaseSessionInternal() {
        synchronized(iceLock) {
            sessionEnded = true
            remoteDescriptionSet = false
            pendingIceCandidates.clear()
        }
        callDocListener?.remove()
        callDocListener = null
        remoteCandidatesListener?.remove()
        remoteCandidatesListener = null

        try { localAudioTrack?.dispose() } catch (_: Exception) {}
        localAudioTrack = null
        try { localAudioSource?.dispose() } catch (_: Exception) {}
        localAudioSource = null

        try { peerConnection?.close() } catch (_: Exception) {}
        try { peerConnection?.dispose() } catch (_: Exception) {}
        peerConnection = null

        try {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try { am.clearCommunicationDevice() } catch (_: Exception) {}
                }
                am.isSpeakerphoneOn = false
                am.isMicrophoneMute = false
                am.mode = AudioManager.MODE_NORMAL
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            }
        } catch (_: Exception) {}
        audioFocusRequest = null
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }
}
