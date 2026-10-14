package com.fuerzasalvaje.senda.domain.usecase.auth

import com.fuerzasalvaje.senda.domain.model.AuthError
import com.fuerzasalvaje.senda.domain.model.User
import com.fuerzasalvaje.senda.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
internal const val MIN_PASSWORD_LENGTH = 6

internal fun String.isValidEmail(): Boolean = EMAIL_PATTERN.matches(this)

class SignUpUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke(name: String, email: String, password: String): Result<User> {
        val cleanName = name.trim()
        val cleanEmail = email.trim()
        return when {
            cleanName.isEmpty() -> Result.failure(AuthError.EmptyName)
            !cleanEmail.isValidEmail() -> Result.failure(AuthError.InvalidEmail)
            password.length < MIN_PASSWORD_LENGTH -> Result.failure(AuthError.WeakPassword)
            else -> repository.signUp(cleanName, cleanEmail, password)
        }
    }
}

class SignInUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        val cleanEmail = email.trim()
        return when {
            !cleanEmail.isValidEmail() -> Result.failure(AuthError.InvalidEmail)
            password.isEmpty() -> Result.failure(AuthError.EmptyPassword)
            else -> repository.signIn(cleanEmail, password)
        }
    }
}

class SignOutUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke() = repository.signOut()
}

class ObserveSessionUseCase @Inject constructor(private val repository: AuthRepository) {
    operator fun invoke(): Flow<User?> = repository.observeSession()
}

class ResetPasswordUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String): Result<Unit> {
        val cleanEmail = email.trim()
        return if (!cleanEmail.isValidEmail()) Result.failure(AuthError.InvalidEmail)
        else repository.sendPasswordReset(cleanEmail)
    }
}
