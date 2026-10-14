package com.fuerzasalvaje.senda.domain.repository

import com.fuerzasalvaje.senda.domain.model.AIRecommendation
import com.fuerzasalvaje.senda.domain.model.Availability
import com.fuerzasalvaje.senda.domain.model.Course
import com.fuerzasalvaje.senda.domain.model.Evaluation
import com.fuerzasalvaje.senda.domain.model.PomodoroConfig
import com.fuerzasalvaje.senda.domain.model.PomodoroRun
import com.fuerzasalvaje.senda.domain.model.PomodoroSession
import com.fuerzasalvaje.senda.domain.model.Question
import com.fuerzasalvaje.senda.domain.model.ReminderSetting
import com.fuerzasalvaje.senda.domain.model.Simulacrum
import com.fuerzasalvaje.senda.domain.model.StressCheckIn
import com.fuerzasalvaje.senda.domain.model.StudyBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/**
 * Data owned by the signed-in student. Reads always come from the local cache;
 * writes land locally first and are then synced to the cloud.
 */
interface OwnedRepository<T> {
    fun observeAll(): Flow<List<T>>
    fun observeById(id: String): Flow<T?>
    suspend fun save(item: T): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
}

interface CourseRepository : OwnedRepository<Course>

interface EvaluationRepository : OwnedRepository<Evaluation> {
    fun observeByCourse(courseId: String): Flow<List<Evaluation>>
}

interface QuestionRepository : OwnedRepository<Question> {
    fun observeByCourse(courseId: String): Flow<List<Question>>
}

interface SimulacrumRepository : OwnedRepository<Simulacrum> {
    fun observeByCourse(courseId: String): Flow<List<Simulacrum>>
}

interface AIRecommendationRepository : OwnedRepository<AIRecommendation>

interface PomodoroSessionRepository : OwnedRepository<PomodoroSession>

interface StudyBlockRepository : OwnedRepository<StudyBlock>

/** Daily study availability of the signed-in student (D-9). Emits the default until one is saved. */
interface AvailabilityRepository {
    fun observe(): Flow<Availability>
    suspend fun save(availability: Availability): Result<Unit>
}

/** Reminder and Pomodoro preferences of the signed-in student (D-13). Emit the defaults until saved. */
interface SettingsRepository {
    fun observeReminders(): Flow<ReminderSetting>
    suspend fun saveReminders(setting: ReminderSetting): Result<Unit>
    fun observePomodoro(): Flow<PomodoroConfig>
    suspend fun savePomodoro(config: PomodoroConfig): Result<Unit>
}

/**
 * The single Pomodoro timer of the app (KB-05, D-13). Its state is persisted with timestamps,
 * so it survives the app being closed; it runs as a foreground service while in progress.
 */
interface PomodoroTimer {
    val run: StateFlow<PomodoroRun>
    fun start()
    fun pause()
    /** Stops and goes back to the first focus block, keeping the chosen evaluation. */
    fun stop()
    fun skip()
    /** Credits the next focus minutes to [evaluationId] (or only to [courseId]); ignored while running. */
    fun link(evaluationId: String?, courseId: String?, title: String?)
}

/** State of the background question generation of one course (shown on its Simulacros card). */
enum class PrefetchStatus { PREPARING, RETRYING, FAILED, IDLE }

/**
 * Generates questions in the background, with network and retries, so a mock exam never waits for Gemini.
 * Implemented with WorkManager.
 */
interface QuestionPrefetcher {
    /** Tops the course bank up to its target; [fresh] generates a full new set (new syllabus or PDF). */
    fun refill(courseId: String, fresh: Boolean = false, topics: String? = null)

    /** Adds questions to an attempt that started with fewer than [desired]. */
    fun topUp(courseId: String, simulacrumId: String, desired: Int, topics: String? = null)

    fun observeStatus(): Flow<Map<String, PrefetchStatus>>

    /** Does nothing: for tests and code paths without background work. */
    object None : QuestionPrefetcher {
        override fun refill(courseId: String, fresh: Boolean, topics: String?) = Unit
        override fun topUp(courseId: String, simulacrumId: String, desired: Int, topics: String?) = Unit
        override fun observeStatus(): Flow<Map<String, PrefetchStatus>> = flowOf(emptyMap())
    }
}

/** Periodic signal for values that depend on the current time. */
interface Ticker {
    val ticks: Flow<Unit>

    /** Emits once: for tests and places that only need the current value. */
    object Never : Ticker {
        override val ticks: Flow<Unit> = flowOf(Unit)
    }
}

interface StressCheckInRepository : OwnedRepository<StressCheckIn> {
    fun observeForDate(date: LocalDate): Flow<StressCheckIn?> = observeById(date.toString())
}
