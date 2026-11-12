package com.example.chatapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.domain.ChatRepository
import com.example.chatapp.domain.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val chatId: String = "",
    val chatTitle: String = "",
    val messages: List<Message> = emptyList(),
    val draft: String = "",
    val isSending: Boolean = false,
    val currentUserId: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repo: ChatRepository,
) : ViewModel() {

    private val _chatId = MutableStateFlow("")
    private val _chatTitle = MutableStateFlow("")
    private val _draft = MutableStateFlow("")
    private val _isSending = MutableStateFlow(false)

    fun initialize(chatId: String, title: String) {
        _chatId.value = chatId
        _chatTitle.value = title
    }

    private val messagesFlow = _chatId.flatMapLatest { id ->
        if (id.isBlank()) flowOf(emptyList())
        else repo.observeMessages(id).catch { emit(emptyList()) }
    }

    val state: StateFlow<ChatUiState> = combine(
        _chatId,
        _chatTitle,
        messagesFlow,
        _draft,
        _isSending,
    ) { id, title, msgs, draft, sending ->
        ChatUiState(
            chatId = id,
            chatTitle = title,
            messages = msgs,
            draft = draft,
            isSending = sending,
            currentUserId = repo.currentUserId.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    fun onDraftChange(text: String) {
        _draft.value = text
    }

    fun send() {
        val id = _chatId.value.ifBlank { return }
        val text = _draft.value.trim().ifEmpty { return }
        _draft.value = ""
        viewModelScope.launch {
            _isSending.value = true
            try {
                repo.sendMessage(id, text)
            } finally {
                _isSending.value = false
            }
        }
    }
}
