package com.fuerzasalvaje.senda.data.repository

import android.util.Log
import com.fuerzasalvaje.senda.data.remote.SessionProvider
import com.fuerzasalvaje.senda.data.remote.userDoc
import com.fuerzasalvaje.senda.domain.model.AuthError
import com.fuerzasalvaje.senda.domain.model.Availability
import com.fuerzasalvaje.senda.domain.repository.AvailabilityRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the availability as two fields of users/{uid}. Firestore's offline cache serves reads
 * without connection and queues the write, like the rest of the profile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreAvailabilityRepository @Inject constructor(
    private val session: SessionProvider,
    private val firestore: FirebaseFirestore,
) : AvailabilityRepository {

    override fun observe(): Flow<Availability> = session.uid.flatMapLatest { uid ->
        if (uid == null) flowOf(Availability()) else callbackFlow {
            val registration = firestore.userDoc(uid).addSnapshotListener { snapshot, error ->
                if (error != null) Log.w(TAG, "Availability listener failed: ${error.message}")
                val start = snapshot?.getString(START)?.toLocalTimeOrNull()
                val end = snapshot?.getString(END)?.toLocalTimeOrNull()
                trySend(if (start != null && end != null) Availability(start, end).sanitized() else Availability())
            }
            awaitClose { registration.remove() }
        }
    }.distinctUntilChanged()

    override suspend fun save(availability: Availability): Result<Unit> {
        val uid = session.currentUid() ?: return Result.failure(AuthError.NotSignedIn)
        firestore.userDoc(uid)
            .set(mapOf(START to availability.start.toString(), END to availability.end.toString()), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "Availability save failed: ${it.message}") }
        return Result.success(Unit)
    }

    private fun String.toLocalTimeOrNull(): LocalTime? = runCatching { LocalTime.parse(this) }.getOrNull()

    private companion object {
        const val TAG = "SendaAvailability"
        const val START = "availabilityStart"
        const val END = "availabilityEnd"
    }
}
