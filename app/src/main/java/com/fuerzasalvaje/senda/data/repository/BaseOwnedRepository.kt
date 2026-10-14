package com.fuerzasalvaje.senda.data.repository

import android.util.Log
import com.fuerzasalvaje.senda.data.local.dao.OwnedDao
import com.fuerzasalvaje.senda.data.remote.SessionProvider
import com.fuerzasalvaje.senda.data.remote.userCollection
import com.fuerzasalvaje.senda.domain.model.AuthError
import com.fuerzasalvaje.senda.domain.repository.OwnedRepository
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.coroutines.cancellation.CancellationException

/** A repository whose remote changes are pushed into Room by the SyncManager. */
interface SyncableRepository {
    val collection: String
    suspend fun applyRemote(uid: String, snapshot: QuerySnapshot)
}

/**
 * Offline-first repository for one users/{uid}/{collection}:
 * reads come from Room; writes go to Room first and then to Firestore.
 *
 * Firestore writes are not awaited: the SDK queues them while offline and the task only
 * completes after the server confirms, which would block the caller without a connection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class BaseOwnedRepository<T : Any, E : Any, D : Any>(
    private val session: SessionProvider,
    private val firestore: FirebaseFirestore,
    private val dao: OwnedDao<E>,
    final override val collection: String,
    private val dtoClass: Class<D>,
) : OwnedRepository<T>, SyncableRepository {

    protected abstract fun idOf(item: T): String
    protected abstract fun entityOf(item: T, uid: String): E
    protected abstract fun domainOf(entity: E): T
    protected abstract fun dtoOf(item: T): D
    protected abstract fun remoteDomainOf(dto: D, id: String, local: E?): T

    override fun observeAll(): Flow<List<T>> = observeList { uid -> dao.observeAll(uid) }

    override fun observeById(id: String): Flow<T?> = session.uid.flatMapLatest { uid ->
        if (uid == null) flowOf(null) else dao.observeById(uid, id).map { it?.let(::domainOf) }
    }

    override suspend fun save(item: T): Result<Unit> = runSafely { uid ->
        dao.upsert(entityOf(item, uid))
        firestore.userCollection(uid, collection).document(idOf(item)).set(dtoOf(item))
            .addOnFailureListener { logRemoteFailure("save", it) }
    }

    override suspend fun delete(id: String): Result<Unit> = runSafely { uid ->
        dao.deleteById(uid, id)
        firestore.userCollection(uid, collection).document(id).delete()
            .addOnFailureListener { logRemoteFailure("delete", it) }
    }

    override suspend fun applyRemote(uid: String, snapshot: QuerySnapshot) {
        for (change in snapshot.documentChanges) {
            val document = change.document
            if (change.type == DocumentChange.Type.REMOVED) {
                dao.deleteById(uid, document.id)
                continue
            }
            val item = parse(uid, document) ?: continue
            dao.upsert(entityOf(item, uid))
        }
    }

    /** Helper for per-type queries that must follow the current session. */
    protected fun observeList(query: (uid: String) -> Flow<List<E>>): Flow<List<T>> =
        session.uid.flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList()) else query(uid).map { rows -> rows.map(::domainOf) }
        }

    private suspend fun parse(uid: String, document: DocumentSnapshot): T? = try {
        val dto = document.toObject(dtoClass) ?: return null
        remoteDomainOf(dto, document.id, dao.getById(uid, document.id))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Invalid remote data is skipped instead of crashing the sync.
        Log.w(TAG, "Skipping invalid $collection/${document.id}: ${e.message}")
        null
    }

    private suspend fun runSafely(block: suspend (uid: String) -> Unit): Result<Unit> {
        val uid = session.currentUid() ?: return Result.failure(AuthError.NotSignedIn)
        return try {
            block(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun logRemoteFailure(operation: String, error: Exception) {
        Log.w(TAG, "Firestore $operation failed in $collection: ${error.message}")
    }

    private companion object {
        const val TAG = "SendaRepository"
    }
}
