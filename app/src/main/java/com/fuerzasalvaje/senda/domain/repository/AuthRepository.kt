package com.fuerzasalvaje.senda.domain.repository

import com.fuerzasalvaje.senda.domain.model.User
import kotlinx.coroutines.flow.Flow

/** Email and password session. Failures are reported as [com.fuerzasalvaje.senda.domain.model.AuthError]. */
interface AuthRepository {
    suspend fun signUp(name: String, email: String, password: String): Result<User>
    suspend fun signIn(email: String, password: String): Result<User>
    /** Ends the session and wipes the local cache. */
    suspend fun signOut()
    suspend fun sendPasswordReset(email: String): Result<Unit>
    /** Emits the signed-in user, or null when there is no session. */
    fun observeSession(): Flow<User?>
}
