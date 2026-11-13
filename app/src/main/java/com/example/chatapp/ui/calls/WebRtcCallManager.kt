package com.example.chatapp.ui.calls

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.webrtc.*

/**
 * Voice-only WebRTC PeerConnection & audio controller backed by Firestore SDP/ICE signaling.
 */
class WebRtcCallManager(
    context: Context,
    private val db: FirebaseFirestore,
) {
    private val appContext = context.applicationContext
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private var callDocListener: ListenerRegistration? = null
    private var remoteCandidatesListener: ListenerRegistration? = null
    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false

    var isMuted by mutableStateOf(false)
        private set
    var isSpeakerOn by mutableStateOf(false)
        private set
    var connectionState by mutableStateOf("connecting")
        private set

    private fun ensureFactoryInitialized() {
        if (factory != null) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .createPeerConnectionFactory()
    }

    fun startCallerSession(
        callId: String,
        onRemoteHangup: () -> Unit,
    ) {
        setupSession(callId = callId, isCaller = true, onRemoteHangup = onRemoteHangup)
    }

    fun acceptCalleeSession(
        callId: String,
        offerSdp: String,
        onRemoteHangup: () -> Unit,
    ) {
        setupSession(
            callId = callId,
            isCaller = false,
            initialOfferSdp = offerSdp,
            onRemoteHangup = onRemoteHangup,
        )
    }

    private fun setupSession(
        callId: String,
        isCaller: Boolean,
        initialOfferSdp: String? = null,
        onRemoteHangup: () -> Unit,
    ) {
        releaseSessionOnly()
        ensureFactoryInitialized()

        remoteDescriptionSet = false
        pendingIceCandidates.clear()
        isMuted = false
        connectionState = if (isCaller) "ringing" else "connecting"
        setSpeakerphone(false)

        val pcFactory = factory ?: return
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        val candidatesCollection = if (isCaller) "callerCandidates" else "calleeCandidates"
        val peerCandidatesCollection = if (isCaller) "calleeCandidates" else "callerCandidates"

        val pc = pcFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null) return
                db.collection("call_sessions").document(callId)
                    .collection(candidatesCollection)
                    .add(
                        mapOf(
                            "sdpMid" to candidate.sdpMid,
                            "sdpMLineIndex" to candidate.sdpMLineIndex,
                            "sdp" to candidate.sdp,
                        )
                    )
            }

            override fun onTrack(transceiver: RtpTransceiver?) {}

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                when (newState) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> connectionState = "connected"
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.CLOSED -> connectionState = "ended"
                    PeerConnection.IceConnectionState.FAILED -> connectionState = "failed"
                    else -> Unit
                }
            }

            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        }) ?: return

        peerConnection = pc

        // Audio Track
        try {
            val audioConstraints = MediaConstraints()
            localAudioSource = pcFactory.createAudioSource(audioConstraints)
            localAudioTrack = pcFactory.createAudioTrack("moth_audio", localAudioSource).apply {
                setEnabled(true)
            }
            pc.addTrack(localAudioTrack, listOf("moth_stream"))
        } catch (_: Exception) {}

        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        if (isCaller) {
            pc.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    if (desc == null) return
                    pc.setLocalDescription(SimpleSdpObserver(), desc)
                    db.collection("call_sessions").document(callId)
                        .update("offerSdp", desc.description)
                }
            }, mediaConstraints)
        } else if (!initialOfferSdp.isNullOrBlank()) {
            val offerDesc = SessionDescription(SessionDescription.Type.OFFER, initialOfferSdp)
            pc.setRemoteDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    remoteDescriptionSet = true
                    flushPendingCandidates()
                    pc.createAnswer(object : SimpleSdpObserver() {
                        override fun onCreateSuccess(answer: SessionDescription?) {
                            if (answer == null) return
                            pc.setLocalDescription(SimpleSdpObserver(), answer)
                            db.collection("call_sessions").document(callId)
                                .update(
                                    mapOf(
                                        "answerSdp" to answer.description,
                                        "status" to "ACTIVE",
                                    )
                                )
                            connectionState = "connected"
                        }
                    }, mediaConstraints)
                }
            }, offerDesc)
        }

        // Listen for call status + SDP answer
        callDocListener?.remove()
        callDocListener = db.collection("call_sessions").document(callId)
            .addSnapshotListener { snap, error ->
                if (error != null) return@addSnapshotListener
                if (snap == null || !snap.exists()) {
                    onRemoteHangup()
                    return@addSnapshotListener
                }
                val status = snap.getString("status") ?: ""
                if (status == "ENDED" || status == "DECLINED" || status == "MISSED") {
                    onRemoteHangup()
                    return@addSnapshotListener
                }
                if (isCaller && !remoteDescriptionSet) {
                    val answerSdp = snap.getString("answerSdp")
                    if (!answerSdp.isNullOrBlank()) {
                        val answerDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                        pc.setRemoteDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                remoteDescriptionSet = true
                                flushPendingCandidates()
                                connectionState = "connected"
                            }
                        }, answerDesc)
                    }
                }
            }

        // Listen for peer ICE candidates
        remoteCandidatesListener?.remove()
        remoteCandidatesListener = db.collection("call_sessions").document(callId)
            .collection(peerCandidatesCollection)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener
                for (change in snap.documentChanges) {
                    val doc = change.document
                    val sdpMid = doc.getString("sdpMid") ?: continue
                    val idx = (doc.getLong("sdpMLineIndex") ?: 0L).toInt()
                    val sdp = doc.getString("sdp") ?: continue
                    val candidate = IceCandidate(sdpMid, idx, sdp)
                    if (remoteDescriptionSet) {
                        peerConnection?.addIceCandidate(candidate)
                    } else {
                        pendingIceCandidates.add(candidate)
                    }
                }
            }
    }

    private fun flushPendingCandidates() {
        val pc = peerConnection ?: return
        pendingIceCandidates.forEach { pc.addIceCandidate(it) }
        pendingIceCandidates.clear()
    }

    fun toggleMute() {
        val next = !isMuted
        isMuted = next
        localAudioTrack?.setEnabled(!next)
    }

    fun toggleSpeaker() {
        setSpeakerphone(!isSpeakerOn)
    }

    @Suppress("DEPRECATION")
    private fun setSpeakerphone(enabled: Boolean) {
        isSpeakerOn = enabled
        try {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = enabled
        } catch (_: Exception) {}
    }

    fun endSession() {
        releaseSessionOnly()
    }

    @Suppress("DEPRECATION")
    private fun releaseSessionOnly() {
        callDocListener?.remove()
        callDocListener = null
        remoteCandidatesListener?.remove()
        remoteCandidatesListener = null
        remoteDescriptionSet = false
        pendingIceCandidates.clear()

        try { localAudioTrack?.dispose() } catch (_: Exception) {}
        localAudioTrack = null
        try { localAudioSource?.dispose() } catch (_: Exception) {}
        localAudioSource = null

        try { peerConnection?.close() } catch (_: Exception) {}
        try { peerConnection?.dispose() } catch (_: Exception) {}
        peerConnection = null

        try {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.isSpeakerphoneOn = false
            am?.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }
}
