package com.fuerzasalvaje.senda.domain.model

/** Authentication failures with the Spanish message shown to the student. */
sealed class AuthError(message: String) : Exception(message) {
    data object EmptyName : AuthError("Ingresa tu nombre.")
    data object InvalidEmail : AuthError("El correo no es válido.")
    data object EmptyPassword : AuthError("Ingresa tu contraseña.")
    data object WeakPassword : AuthError("La contraseña es muy débil. Usa al menos 6 caracteres.")
    data object UserNotFound : AuthError("No existe una cuenta con ese correo.")
    data object InvalidCredentials : AuthError("Correo o contraseña incorrectos.")
    data object EmailAlreadyInUse : AuthError("Ya existe una cuenta con ese correo.")
    data object UserDisabled : AuthError("Esta cuenta está deshabilitada.")
    data object TooManyRequests : AuthError("Demasiados intentos. Espera unos minutos e inténtalo de nuevo.")
    data object Network : AuthError("Sin conexión. Revisa tu internet e inténtalo de nuevo.")
    data object NotSignedIn : AuthError("No hay una sesión activa.")
    data object Unknown : AuthError("Ocurrió un error inesperado. Inténtalo de nuevo.")
}
