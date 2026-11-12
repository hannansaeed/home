package com.example.chatapp.data

import com.example.chatapp.data.local.LocalAccountDao
import com.example.chatapp.data.local.LocalAccountEntity
import com.example.chatapp.domain.SessionManager
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalAuthRepository @Inject constructor(
    private val accountDao: LocalAccountDao,
    private val sessionManager: SessionManager,
) {
    private fun hash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.trim().lowercase().toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun register(
        username: String,
        password: String,
        securityQuestion: String,
        securityAnswer: String,
    ): Result<Unit> {
        val cleanUser = username.trim()
        val cleanPass = password.trim()
        val cleanQuestion = securityQuestion.trim()
        val cleanAnswer = securityAnswer.trim()

        if (cleanUser.length < 3) return Result.failure(IllegalArgumentException("Username must be at least 3 characters"))
        if (cleanPass.length < 4) return Result.failure(IllegalArgumentException("Password must be at least 4 characters"))
        if (cleanQuestion.isBlank()) return Result.failure(IllegalArgumentException("Security question cannot be blank"))
        if (cleanAnswer.isBlank()) return Result.failure(IllegalArgumentException("Security answer cannot be blank"))

        val existing = accountDao.getAccount(cleanUser)
        if (existing != null) {
            return Result.failure(IllegalArgumentException("Username '$cleanUser' is already taken"))
        }

        val account = LocalAccountEntity(
            username = cleanUser,
            passwordHash = hash(cleanPass),
            securityQuestion = cleanQuestion,
            securityAnswerHash = hash(cleanAnswer),
        )
        accountDao.insertAccount(account)
        sessionManager.setLocalSession(cleanUser)
        return Result.success(Unit)
    }

    suspend fun login(username: String, password: String): Result<Unit> {
        val cleanUser = username.trim()
        val cleanPass = password.trim()

        if (cleanUser.isBlank() || cleanPass.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter username and password"))
        }

        val account = accountDao.getAccount(cleanUser)
            ?: return Result.failure(IllegalArgumentException("No account found for '$cleanUser'"))

        if (account.passwordHash != hash(cleanPass)) {
            return Result.failure(IllegalArgumentException("Incorrect password"))
        }

        sessionManager.setLocalSession(account.username)
        return Result.success(Unit)
    }

    suspend fun getSecurityQuestion(username: String): Result<String> {
        val cleanUser = username.trim()
        if (cleanUser.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter your username"))
        }
        val account = accountDao.getAccount(cleanUser)
            ?: return Result.failure(IllegalArgumentException("Username '$cleanUser' not found"))
        return Result.success(account.securityQuestion)
    }

    suspend fun resetPassword(
        username: String,
        securityAnswer: String,
        newPassword: String,
    ): Result<Unit> {
        val cleanUser = username.trim()
        val cleanAnswer = securityAnswer.trim()
        val cleanNewPass = newPassword.trim()

        if (cleanNewPass.length < 4) {
            return Result.failure(IllegalArgumentException("New password must be at least 4 characters"))
        }

        val account = accountDao.getAccount(cleanUser)
            ?: return Result.failure(IllegalArgumentException("User not found"))

        if (account.securityAnswerHash != hash(cleanAnswer)) {
            return Result.failure(IllegalArgumentException("Incorrect answer to security question"))
        }

        accountDao.updatePassword(account.username, hash(cleanNewPass))
        return Result.success(Unit)
    }
}
