package com.example.chatapp.data.remote

import com.example.chatapp.domain.ConnectionState
import kotlinx.coroutines.flow.*
import okhttp3.*
import javax.inject.Inject
import javax.inject.Singleton

/** Thin WebSocket wrapper. Payloads are opaque (already encrypted) strings. */
@Singleton
class ChatSocket @Inject constructor(private val client: OkHttpClient) {
    private var socket: WebSocket? = null

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state

    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incoming: SharedFlow<String> = _incoming

    fun connect(url: String, token: String) {
        _state.value = ConnectionState.CONNECTING
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) { _state.value = ConnectionState.CONNECTED }
            override fun onMessage(ws: WebSocket, text: String) { _incoming.tryEmit(text) }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) { _state.value = ConnectionState.DISCONNECTED }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) { _state.value = ConnectionState.DISCONNECTED }
        })
    }

    fun send(payload: String): Boolean = socket?.send(payload) ?: false
    fun disconnect() { socket?.close(1000, null); socket = null }
}
