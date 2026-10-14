package com.fuerzasalvaje.senda.data.repository

import android.util.Log
import com.fuerzasalvaje.senda.data.remote.SessionProvider
import com.fuerzasalvaje.senda.data.remote.userDoc
import com.fuerzasalvaje.senda.domain.model.AuthError
import com.fuerzasalvaje.senda.domain.model.PomodoroConfig
import com.fuerzasalvaje.senda.domain.model.ReminderSetting
import com.fuerzasalvaje.senda.domain.repository.SettingsRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the preferences as two maps of users/{uid}, like the availability. Firestore's offline cache
 * serves reads without connection and queues the writes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreSettingsRepository @Inject constructor(
    private val session: SessionProvider,
    private val firestore: FirebaseFirestore,
) : SettingsRepository {

    override fun observeReminders(): Flow<ReminderSetting> = observeField(ReminderSetting(), ::readReminders)

    override suspend fun saveReminders(setting: ReminderSetting): Result<Unit> = write(
        REMINDERS,
        mapOf(
            ENABLED to setting.enabled,
            OFFSETS to setting.evaluationOffsets.map { it.toMinutes() },
            DAILY_SUMMARY to setting.dailySummaryEnabled,
            REST to setting.restReminderEnabled,
            EXAM_ROUTINE to setting.examRoutineEnabled,
        ),
    )

    override fun observePomodoro(): Flow<PomodoroConfig> = observeField(PomodoroConfig(), ::readPomodoro)

    override suspend fun savePomodoro(config: PomodoroConfig): Result<Unit> {
        val valid = config.sanitized()
        return write(
            POMODORO,
            mapOf(
                FOCUS to valid.focusMinutes,
                SHORT_BREAK to valid.shortBreakMinutes,
                LONG_BREAK to valid.longBreakMinutes,
                CYCLES to valid.cyclesBeforeLongBreak,
            ),
        )
    }

    private fun <T> observeField(default: T, read: (DocumentSnapshot) -> T?): Flow<T> =
        session.uid.flatMapLatest { uid ->
            if (uid == null) flowOf(default) else callbackFlow {
                val registration = firestore.userDoc(uid).addSnapshotListener { snapshot, error ->
                    if (error != null) Log.w(TAG, "Settings listener failed: ${error.message}")
                    trySend(snapshot?.let(read) ?: default)
                }
                awaitClose { registration.remove() }
            }
        }.distinctUntilChanged()

    private fun write(field: String, value: Map<String, Any>): Result<Unit> {
        val uid = session.currentUid() ?: return Result.failure(AuthError.NotSignedIn)
        firestore.userDoc(uid)
            .set(mapOf(field to value), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "Settings save failed: ${it.message}") }
        return Result.success(Unit)
    }

    private fun readReminders(snapshot: DocumentSnapshot): ReminderSetting? {
        val map = snapshot.get(REMINDERS) as? Map<*, *> ?: return null
        val default = ReminderSetting()
        return ReminderSetting(
            enabled = map[ENABLED] as? Boolean ?: default.enabled,
            evaluationOffsets = (map[OFFSETS] as? List<*>)
                ?.mapNotNull { (it as? Number)?.toLong()?.takeIf { minutes -> minutes > 0 }?.let(Duration::ofMinutes) }
                ?: default.evaluationOffsets,
            dailySummaryEnabled = map[DAILY_SUMMARY] as? Boolean ?: default.dailySummaryEnabled,
            restReminderEnabled = map[REST] as? Boolean ?: default.restReminderEnabled,
            examRoutineEnabled = map[EXAM_ROUTINE] as? Boolean ?: default.examRoutineEnabled,
        )
    }

    private fun readPomodoro(snapshot: DocumentSnapshot): PomodoroConfig? {
        val map = snapshot.get(POMODORO) as? Map<*, *> ?: return null
        val default = PomodoroConfig()
        fun int(key: String, fallback: Int) = (map[key] as? Number)?.toInt() ?: fallback
        return PomodoroConfig(
            focusMinutes = int(FOCUS, default.focusMinutes),
            shortBreakMinutes = int(SHORT_BREAK, default.shortBreakMinutes),
            longBreakMinutes = int(LONG_BREAK, default.longBreakMinutes),
            cyclesBeforeLongBreak = int(CYCLES, default.cyclesBeforeLongBreak),
        ).sanitized()
    }

    private companion object {
        const val TAG = "SendaSettings"
        const val REMINDERS = "reminders"
        const val ENABLED = "enabled"
        const val OFFSETS = "offsetsMinutes"
        const val DAILY_SUMMARY = "dailySummary"
        const val REST = "restReminder"
        const val EXAM_ROUTINE = "examRoutine"
        const val POMODORO = "pomodoro"
        const val FOCUS = "focusMinutes"
        const val SHORT_BREAK = "shortBreakMinutes"
        const val LONG_BREAK = "longBreakMinutes"
        const val CYCLES = "cyclesBeforeLongBreak"
    }
}
