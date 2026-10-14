package com.fuerzasalvaje.senda.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuerzasalvaje.senda.domain.model.AuthError
import com.fuerzasalvaje.senda.domain.usecase.auth.ResetPasswordUseCase
import com.fuerzasalvaje.senda.domain.usecase.auth.SignInUseCase
import com.fuerzasalvaje.senda.domain.usecase.auth.SignUpUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    /** Live validation is shown only after the user touches a field. */
    val touched: Set<AuthField> = emptySet(),
    val loading: Boolean = false,
    val error: String? = null,
    val resetEmailSent: Boolean = false,
) {
    val nameError: String? get() = if (AuthField.NAME in touched && name.isBlank()) AuthError.EmptyName.message else null
    val emailError: String?
        get() = if (AuthField.EMAIL in touched && !EMAIL.matches(email.trim())) AuthError.InvalidEmail.message else null
    val passwordError: String?
        get() = if (AuthField.PASSWORD in touched && password.length < 6) AuthError.WeakPassword.message else null

    val canSignIn: Boolean get() = EMAIL.matches(email.trim()) && password.isNotEmpty() && !loading
    val canSignUp: Boolean get() = name.isNotBlank() && EMAIL.matches(email.trim()) && password.length >= 6 && !loading

    private companion object {
        val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}

enum class AuthField { NAME, EMAIL, PASSWORD }

/** Shared by the login and sign-up screens. Navigation happens when the session changes. */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val signIn: SignInUseCase,
    private val signUp: SignUpUseCase,
    private val resetPassword: ResetPasswordUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onNameChange(value: String) = _state.update { it.copy(name = value, touched = it.touched + AuthField.NAME, error = null) }
    fun onEmailChange(value: String) = _state.update { it.copy(email = value, touched = it.touched + AuthField.EMAIL, error = null) }
    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, touched = it.touched + AuthField.PASSWORD, error = null) }
    fun togglePasswordVisibility() = _state.update { it.copy(passwordVisible = !it.passwordVisible) }
    fun clearMessages() = _state.update { it.copy(error = null, resetEmailSent = false) }

    fun submitSignIn() = launchAuth { s -> signIn(s.email, s.password).exceptionOrNull() }

    fun submitSignUp() = launchAuth { s -> signUp(s.name, s.email, s.password).exceptionOrNull() }

    fun sendReset(email: String) = launchAuth(onSuccess = { it.copy(resetEmailSent = true) }) { resetPassword(email).exceptionOrNull() }

    private fun launchAuth(
        onSuccess: (AuthUiState) -> AuthUiState = { it },
        action: suspend (AuthUiState) -> Throwable?,
    ) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val error = action(_state.value)
            _state.update {
                if (error == null) onSuccess(it.copy(loading = false))
                else it.copy(loading = false, error = (error as? AuthError ?: AuthError.Unknown).message)
            }
        }
    }
}
