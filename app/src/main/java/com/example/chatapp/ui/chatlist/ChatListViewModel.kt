package com.example.chatapp.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.domain.ChatRepository
import com.example.chatapp.domain.ChatSummary
import com.example.chatapp.domain.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatListUiState(
    val chats: List<ChatSummary> = emptyList(),
    val availableUsers: List<UserProfile> = emptyList(),
    val currentUserId: String = "",
    val currentUserName: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val repo: ChatRepository,
) : ViewModel() {

    private val _isActionLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val state: StateFlow<ChatListUiState> = combine(
        repo.observeUserChats().catch { emit(emptyList()) },
        repo.observeUsers().catch { emit(emptyList()) },
        _isActionLoading,
        _error,
    ) { chats, users, loading, err ->
        ChatListUiState(
            chats = chats,
            availableUsers = users,
            currentUserId = repo.currentUserId.orEmpty(),
            currentUserName = repo.currentUserName.orEmpty(),
            isLoading = loading,
            errorMessage = err,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatListUiState(
            currentUserId = repo.currentUserId.orEmpty(),
            currentUserName = repo.currentUserName.orEmpty(),
        ),
    )

    init {
        viewModelScope.launch {
            try {
                repo.syncCurrentUserProfile()
            } catch (_: Exception) {
            }
        }
    }

    fun startChat(otherUserId: String, otherUserName: String, onChatReady: (chatId: String, name: String) -> Unit) {
        viewModelScope.launch {
            _isActionLoading.value = true
            _error.value = null
            try {
                val chatId = repo.getOrCreateChatWithUser(otherUserId, otherUserName)
                _isActionLoading.value = false
                onChatReady(chatId, otherUserName)
            } catch (e: Exception) {
                _isActionLoading.value = false
                _error.value = e.localizedMessage ?: "Failed to start chat"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
