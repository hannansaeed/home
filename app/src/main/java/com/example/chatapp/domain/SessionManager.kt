package com.example.chatapp.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UserSession {
    data object None : UserSession
    data class Google(val uid: String, val name: String, val email: String) : UserSession
    data class Local(val username: String) : UserSession
}

@Singleton
class SessionManager @Inject constructor() {
    private val _session = MutableStateFlow<UserSession>(UserSession.None)
    val session: StateFlow<UserSession> = _session.asStateFlow()

    fun setGoogleSession(uid: String, name: String, email: String) {
        _session.value = UserSession.Google(uid, name, email)
    }

    fun setLocalSession(username: String) {
        _session.value = UserSession.Local(username)
    }

    fun clearSession() {
        _session.value = UserSession.None
    }
}
