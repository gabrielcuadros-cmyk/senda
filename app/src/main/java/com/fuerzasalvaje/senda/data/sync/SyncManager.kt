package com.fuerzasalvaje.senda.data.sync

import android.util.Log
import com.fuerzasalvaje.senda.data.local.LocalDataCleaner
import com.fuerzasalvaje.senda.data.remote.SessionProvider
import com.fuerzasalvaje.senda.data.remote.userCollection
import com.fuerzasalvaje.senda.data.repository.SyncableRepository
import com.fuerzasalvaje.senda.di.ApplicationScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors users/{uid}/... into Room while a student is signed in.
 * Listeners follow the session: they are replaced on account change and removed on sign-out,
 * when the local cache is also wiped.
 */
@Singleton
class SyncManager @Inject constructor(
    private val session: SessionProvider,
    private val firestore: FirebaseFirestore,
    private val repositories: Set<@JvmSuppressWildcards SyncableRepository>,
    private val localDataCleaner: LocalDataCleaner,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var previousUid: String? = null
            session.uid.collectLatest { uid ->
                if (uid == null || (previousUid != null && previousUid != uid)) localDataCleaner.clear()
                previousUid = uid
                if (uid != null) syncAll(uid)
            }
        }
    }

    private suspend fun syncAll(uid: String) = coroutineScope {
        repositories.forEach { repository ->
            launch {
                snapshots(uid, repository.collection).collect { repository.applyRemote(uid, it) }
            }
        }
    }

    private fun snapshots(uid: String, collection: String): Flow<QuerySnapshot> = callbackFlow {
        val registration = firestore.userCollection(uid, collection).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Sync of $collection stopped: ${error.message}")
                close()
                return@addSnapshotListener
            }
            if (snapshot != null) trySend(snapshot)
        }
        awaitClose { registration.remove() }
    }

    private companion object {
        const val TAG = "SendaSync"
    }
}
