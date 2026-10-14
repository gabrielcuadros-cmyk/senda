package com.fuerzasalvaje.senda.data.repository

import android.util.Log
import com.fuerzasalvaje.senda.data.local.LocalDataCleaner
import com.fuerzasalvaje.senda.data.mapper.toDomain
import com.fuerzasalvaje.senda.data.mapper.toDto
import com.fuerzasalvaje.senda.data.remote.SessionProvider
import com.fuerzasalvaje.senda.data.remote.dto.UserDto
import com.fuerzasalvaje.senda.data.remote.userDoc
import com.fuerzasalvaje.senda.domain.model.AuthError
import com.fuerzasalvaje.senda.domain.model.Plan
import com.fuerzasalvaje.senda.domain.model.User
import com.fuerzasalvaje.senda.domain.repository.AuthRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val session: SessionProvider,
    private val localDataCleaner: LocalDataCleaner,
    private val clock: Clock,
) : AuthRepository {

    override suspend fun signUp(name: String, email: String, password: String): Result<User> = authCall {
        val firebaseUser = requireNotNull(auth.createUserWithEmailAndPassword(email, password).await().user)
        firebaseUser.updateProfile(userProfileChangeRequest { displayName = name }).await()

        val user = User(uid = firebaseUser.uid, name = name, email = email, plan = Plan.LAUNCH, createdAt = clock.instant())
        // The account already exists at this point: a slow or failed profile write must not fail sign-up.
        val written = withTimeoutOrNull(PROFILE_WRITE_TIMEOUT_MS) {
            runCatching { firestore.userDoc(user.uid).set(user.toDto()).await() }
                .onFailure { Log.w(TAG, "Could not create user profile: ${it.message}") }
        }
        if (written == null) Log.w(TAG, "User profile write is still pending")
        user
    }

    override suspend fun signIn(email: String, password: String): Result<User> = authCall {
        requireNotNull(auth.signInWithEmailAndPassword(email, password).await().user).toBasicUser()
    }

    override suspend fun signOut() {
        auth.signOut()
        localDataCleaner.clear()
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = authCall<Unit> {
        auth.sendPasswordResetEmail(email).await()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSession(): Flow<User?> = session.uid.flatMapLatest { uid ->
        if (uid == null) flowOf(null) else observeProfile(uid)
    }.distinctUntilChanged()

    /** Profile from users/{uid}; falls back to the Firebase account while the document is missing. */
    private fun observeProfile(uid: String): Flow<User?> = callbackFlow {
        val registration = firestore.userDoc(uid).addSnapshotListener { snapshot, error ->
            val fallback = auth.currentUser?.takeIf { it.uid == uid }?.toBasicUser()
            if (error != null) {
                Log.w(TAG, "Profile listener failed: ${error.message}")
                trySend(fallback)
                return@addSnapshotListener
            }
            val profile = snapshot?.takeIf { it.exists() }?.toObject(UserDto::class.java)?.toDomain(uid)
            if (profile != null && profile.plan != Plan.LAUNCH) {
                // Launch promotion: accounts created before it move to Premium on their next session.
                snapshot.reference.update(PLAN_FIELD, Plan.LAUNCH.name)
                    .addOnFailureListener { Log.w(TAG, "Could not migrate plan: ${it.message}") }
            }
            trySend(profile?.copy(plan = Plan.LAUNCH) ?: fallback)
        }
        awaitClose { registration.remove() }
    }

    private fun FirebaseUser.toBasicUser() = User(
        uid = uid,
        name = displayName.orEmpty(),
        email = email.orEmpty(),
    )

    private suspend fun <T> authCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.toAuthError())
    }

    private companion object {
        const val TAG = "SendaAuth"
        const val PROFILE_WRITE_TIMEOUT_MS = 10_000L
        const val PLAN_FIELD = "plan"
    }
}

/** Translates Firebase failures into the Spanish errors of the domain. */
internal fun Exception.toAuthError(): AuthError = when (this) {
    is AuthError -> this
    is FirebaseNetworkException -> AuthError.Network
    is FirebaseTooManyRequestsException -> AuthError.TooManyRequests
    is FirebaseAuthWeakPasswordException -> AuthError.WeakPassword
    is FirebaseAuthUserCollisionException -> AuthError.EmailAlreadyInUse
    is FirebaseAuthInvalidUserException ->
        if (errorCode == "ERROR_USER_DISABLED") AuthError.UserDisabled else AuthError.UserNotFound
    is FirebaseAuthInvalidCredentialsException ->
        if (errorCode == "ERROR_INVALID_EMAIL") AuthError.InvalidEmail else AuthError.InvalidCredentials
    is FirebaseAuthException -> when (errorCode) {
        "ERROR_INVALID_EMAIL" -> AuthError.InvalidEmail
        "ERROR_WEAK_PASSWORD" -> AuthError.WeakPassword
        "ERROR_USER_NOT_FOUND" -> AuthError.UserNotFound
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> AuthError.InvalidCredentials
        "ERROR_EMAIL_ALREADY_IN_USE" -> AuthError.EmailAlreadyInUse
        "ERROR_USER_DISABLED" -> AuthError.UserDisabled
        "ERROR_TOO_MANY_REQUESTS" -> AuthError.TooManyRequests
        "ERROR_NETWORK_REQUEST_FAILED" -> AuthError.Network
        else -> AuthError.Unknown
    }
    else -> AuthError.Unknown
}
