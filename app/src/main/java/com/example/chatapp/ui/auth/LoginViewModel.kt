package com.example.chatapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatapp.data.LocalAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode {
    SIGN_IN,
    REGISTER,
    FORGOT_PASSWORD,
}

enum class AuthTab {
    GOOGLE,
    USERNAME,
}

data class LoginUiState(
    val activeTab: AuthTab = AuthTab.USERNAME,
    val authMode: AuthMode = AuthMode.SIGN_IN,
    // Sign-in fields
    val usernameInput: String = "",
    val passwordInput: String = "",
    // Register fields
    val registerUsername: String = "",
    val registerPassword: String = "",
    val registerConfirmPassword: String = "",
    val selectedSecurityQuestion: String = "What was the name of your first pet?",
    val customSecurityQuestion: String = "",
    val registerSecurityAnswer: String = "",
    // Forgot Password fields
    val forgotUsername: String = "",
    val discoveredSecurityQuestion: String? = null,
    val forgotSecurityAnswer: String = "",
    val forgotNewPassword: String = "",
    // Status
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

val PRESET_SECURITY_QUESTIONS = listOf(
    "What was the name of your first pet?",
    "What city were you born in?",
    "What is your mother's maiden name?",
    "What was your childhood nickname?",
    "What was the make of your first car?",
    "Custom question...",
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val localAuthRepo: LocalAuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun setTab(tab: AuthTab) {
        _state.update { it.copy(activeTab = tab, errorMessage = null, successMessage = null) }
    }

    fun setMode(mode: AuthMode) {
        _state.update {
            it.copy(
                authMode = mode,
                errorMessage = null,
                successMessage = null,
                discoveredSecurityQuestion = null,
            )
        }
    }

    fun onUsernameChange(value: String) = _state.update { it.copy(usernameInput = value, errorMessage = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(passwordInput = value, errorMessage = null) }

    fun onRegisterUsernameChange(value: String) = _state.update { it.copy(registerUsername = value, errorMessage = null) }
    fun onRegisterPasswordChange(value: String) = _state.update { it.copy(registerPassword = value, errorMessage = null) }
    fun onRegisterConfirmPasswordChange(value: String) = _state.update { it.copy(registerConfirmPassword = value, errorMessage = null) }
    fun onSelectSecurityQuestion(value: String) = _state.update { it.copy(selectedSecurityQuestion = value) }
    fun onCustomSecurityQuestionChange(value: String) = _state.update { it.copy(customSecurityQuestion = value) }
    fun onRegisterSecurityAnswerChange(value: String) = _state.update { it.copy(registerSecurityAnswer = value, errorMessage = null) }

    fun onForgotUsernameChange(value: String) = _state.update { it.copy(forgotUsername = value, errorMessage = null) }
    fun onForgotSecurityAnswerChange(value: String) = _state.update { it.copy(forgotSecurityAnswer = value, errorMessage = null) }
    fun onForgotNewPasswordChange(value: String) = _state.update { it.copy(forgotNewPassword = value, errorMessage = null) }

    fun loginWithUsername(onSuccess: () -> Unit) {
        val s = _state.value
        val user = s.usernameInput.trim()
        val pass = s.passwordInput.trim()

        if (user.isBlank() || pass.isBlank()) {
            _state.update { it.copy(errorMessage = "Please enter both username and password") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val res = localAuthRepo.login(user, pass)
            _state.update { it.copy(isLoading = false) }
            res.onSuccess {
                onSuccess()
            }.onFailure { err ->
                _state.update { it.copy(errorMessage = err.localizedMessage ?: "Invalid credentials") }
            }
        }
    }

    fun registerLocalAccount(onSuccess: () -> Unit) {
        val s = _state.value
        val user = s.registerUsername.trim()
        val pass = s.registerPassword.trim()
        val confirm = s.registerConfirmPassword.trim()
        val question = if (s.selectedSecurityQuestion == "Custom question...") {
            s.customSecurityQuestion.trim()
        } else {
            s.selectedSecurityQuestion.trim()
        }
        val answer = s.registerSecurityAnswer.trim()

        if (user.length < 3) {
            _state.update { it.copy(errorMessage = "Username must be at least 3 characters") }
            return
        }
        if (pass.length < 4) {
            _state.update { it.copy(errorMessage = "Password must be at least 4 characters") }
            return
        }
        if (pass != confirm) {
            _state.update { it.copy(errorMessage = "Passwords do not match") }
            return
        }
        if (question.isBlank()) {
            _state.update { it.copy(errorMessage = "Please choose or enter a security question") }
            return
        }
        if (answer.isBlank()) {
            _state.update { it.copy(errorMessage = "Please enter an answer for your security question") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val res = localAuthRepo.register(user, pass, question, answer)
            _state.update { it.copy(isLoading = false) }
            res.onSuccess {
                onSuccess()
            }.onFailure { err ->
                _state.update { it.copy(errorMessage = err.localizedMessage ?: "Registration failed") }
            }
        }
    }

    fun findSecurityQuestion() {
        val user = _state.value.forgotUsername.trim()
        if (user.isBlank()) {
            _state.update { it.copy(errorMessage = "Please enter your username") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val res = localAuthRepo.getSecurityQuestion(user)
            _state.update { it.copy(isLoading = false) }
            res.onSuccess { q ->
                _state.update { it.copy(discoveredSecurityQuestion = q, errorMessage = null) }
            }.onFailure { err ->
                _state.update { it.copy(errorMessage = err.localizedMessage ?: "User not found") }
            }
        }
    }

    fun resetPassword() {
        val s = _state.value
        val user = s.forgotUsername.trim()
        val answer = s.forgotSecurityAnswer.trim()
        val newPass = s.forgotNewPassword.trim()

        if (answer.isBlank()) {
            _state.update { it.copy(errorMessage = "Please enter your security answer") }
            return
        }
        if (newPass.length < 4) {
            _state.update { it.copy(errorMessage = "New password must be at least 4 characters") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val res = localAuthRepo.resetPassword(user, answer, newPass)
            _state.update { it.copy(isLoading = false) }
            res.onSuccess {
                _state.update {
                    it.copy(
                        authMode = AuthMode.SIGN_IN,
                        usernameInput = user,
                        passwordInput = "",
                        discoveredSecurityQuestion = null,
                        forgotSecurityAnswer = "",
                        forgotNewPassword = "",
                        successMessage = "Password successfully reset! You can now log in.",
                        errorMessage = null,
                    )
                }
            }.onFailure { err ->
                _state.update { it.copy(errorMessage = err.localizedMessage ?: "Failed to reset password") }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
