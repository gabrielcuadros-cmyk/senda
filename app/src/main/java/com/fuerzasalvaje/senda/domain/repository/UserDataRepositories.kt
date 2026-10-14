package com.fuerzasalvaje.senda.domain.repository

import com.fuerzasalvaje.senda.domain.model.AIRecommendation
import com.fuerzasalvaje.senda.domain.model.Availability
import com.fuerzasalvaje.senda.domain.model.Course
import com.fuerzasalvaje.senda.domain.model.Evaluation
import com.fuerzasalvaje.senda.domain.model.PomodoroConfig
import com.fuerzasalvaje.senda.domain.model.PomodoroSession
import com.fuerzasalvaje.senda.domain.model.Question
import com.fuerzasalvaje.senda.domain.model.ReminderSetting
import com.fuerzasalvaje.senda.domain.model.Simulacrum
import com.fuerzasalvaje.senda.domain.model.StressCheckIn
import com.fuerzasalvaje.senda.domain.model.StudyBlock
import kotlinx.coroutines.flow.Flow
import java.time.Instant
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

/** Notifies the end of the running Pomodoro step even when the app is in the background. */
interface PomodoroAlarm {
    fun schedule(at: Instant, title: String, message: String)
    fun cancel()
}

interface StressCheckInRepository : OwnedRepository<StressCheckIn> {
    fun observeForDate(date: LocalDate): Flow<StressCheckIn?> = observeById(date.toString())
}
