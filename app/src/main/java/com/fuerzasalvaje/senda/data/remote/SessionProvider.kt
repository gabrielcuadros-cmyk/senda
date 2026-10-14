package com.fuerzasalvaje.senda.data.remote

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/** Uid of the signed-in student, used to scope every read and write. */
interface SessionProvider {
    val uid: Flow<String?>
    fun currentUid(): String?
}

@Singleton
class FirebaseSessionProvider @Inject constructor(
    private val auth: FirebaseAuth,
) : SessionProvider {

    override val uid: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override fun currentUid(): String? = auth.currentUser?.uid
}
