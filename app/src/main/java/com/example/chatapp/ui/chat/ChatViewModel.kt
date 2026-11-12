package com.example.chatapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val draft: String = "",
)

@HiltViewModel
class ChatViewModel @Inject constructor(private val repo: ChatRepository) : ViewModel() {
    private val chatId = "demo-chat" // TODO: from navigation args
    private val draft = MutableStateFlow("")

    val state: StateFlow<ChatUiState> = combine(
        repo.messages(chatId), repo.connectionState, draft
    ) { msgs, conn, d -> ChatUiState(msgs, conn, d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init { repo.connect() }

    fun onDraftChange(text: String) { draft.value = text }

    fun send() {
        val text = draft.value.trim().ifEmpty { return }
        draft.value = ""
        viewModelScope.launch { repo.send(chatId, text) }
    }

    fun react(messageId: String, emoji: String) {
        viewModelScope.launch { repo.react(messageId, emoji) }
    }

    override fun onCleared() = repo.disconnect()
}
