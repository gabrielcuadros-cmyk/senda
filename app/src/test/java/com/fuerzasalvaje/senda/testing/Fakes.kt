package com.fuerzasalvaje.senda.testing

import com.fuerzasalvaje.senda.domain.model.AIRecommendation
import com.fuerzasalvaje.senda.domain.model.Availability
import com.fuerzasalvaje.senda.domain.model.Course
import com.fuerzasalvaje.senda.domain.model.GeneratedQuestion
import com.fuerzasalvaje.senda.domain.model.StressCheckIn
import com.fuerzasalvaje.senda.domain.model.StudyBlock
import com.fuerzasalvaje.senda.domain.model.StudyTipsRequest
import com.fuerzasalvaje.senda.domain.model.User
import com.fuerzasalvaje.senda.domain.repository.AIRecommendationRepository
import com.fuerzasalvaje.senda.domain.repository.AuthRepository
import com.fuerzasalvaje.senda.domain.repository.AvailabilityRepository
import com.fuerzasalvaje.senda.domain.repository.GeminiService
import com.fuerzasalvaje.senda.domain.repository.StressCheckInRepository
import com.fuerzasalvaje.senda.domain.repository.StudyBlockRepository
import com.fuerzasalvaje.senda.domain.model.Evaluation
import com.fuerzasalvaje.senda.domain.model.Exam
import com.fuerzasalvaje.senda.domain.model.ExamType
import com.fuerzasalvaje.senda.domain.model.Importance
import com.fuerzasalvaje.senda.domain.repository.CourseRepository
import com.fuerzasalvaje.senda.domain.repository.DocumentStorage
import com.fuerzasalvaje.senda.domain.repository.EvaluationRepository
import com.fuerzasalvaje.senda.domain.repository.OwnedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Monday 12 October 2026, 08:00 in Lima. */
val TEST_ZONE: ZoneId = ZoneId.of("America/Lima")
val TEST_NOW: Instant = Instant.parse("2026-10-12T13:00:00Z")
val testClock: Clock = Clock.fixed(TEST_NOW, TEST_ZONE)

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

open class FakeOwnedRepository<T>(private val idOf: (T) -> String) : OwnedRepository<T> {
    val items = MutableStateFlow<Map<String, T>>(emptyMap())
    val saved = mutableListOf<T>()

    override fun observeAll(): Flow<List<T>> = items.map { it.values.toList() }
    override fun observeById(id: String): Flow<T?> = items.map { it[id] }
    override suspend fun save(item: T): Result<Unit> {
        saved += item
        items.update { it + (idOf(item) to item) }
        return Result.success(Unit)
    }
    override suspend fun delete(id: String): Result<Unit> {
        items.update { it - id }
        return Result.success(Unit)
    }

    fun seed(vararg values: T) = items.update { current -> current + values.associateBy(idOf) }
}

class FakeCourseRepository : FakeOwnedRepository<Course>({ it.id }), CourseRepository

class FakeEvaluationRepository : FakeOwnedRepository<Evaluation>({ it.id }), EvaluationRepository {
    override fun observeByCourse(courseId: String): Flow<List<Evaluation>> =
        items.map { all -> all.values.filter { it.courseId == courseId } }
}

class FakeStudyBlockRepository : FakeOwnedRepository<StudyBlock>({ it.id }), StudyBlockRepository

class FakeStressRepository : FakeOwnedRepository<StressCheckIn>({ it.id }), StressCheckInRepository

class FakeRecommendationRepository : FakeOwnedRepository<AIRecommendation>({ it.id }), AIRecommendationRepository

class FakeAvailabilityRepository(initial: Availability = Availability()) : AvailabilityRepository {
    val current = MutableStateFlow(initial)
    override fun observe(): Flow<Availability> = current
    override suspend fun save(availability: Availability): Result<Unit> {
        current.value = availability
        return Result.success(Unit)
    }
}

class FakeAuthRepository(user: User? = User("u1", "Ana Torres", "ana@tecsup.edu.pe")) : AuthRepository {
    val session = MutableStateFlow(user)
    override suspend fun signUp(name: String, email: String, password: String) = Result.success(session.value!!)
    override suspend fun signIn(email: String, password: String) = Result.success(session.value!!)
    override suspend fun signOut() { session.value = null }
    override suspend fun sendPasswordReset(email: String) = Result.success(Unit)
    override fun observeSession(): Flow<User?> = session
}

/** Returns [tipsResult]; records the last request so tests can check what was sent. */
class FakeGeminiService(var tipsResult: Result<String> = Result.success("- Estudia **Física** primero")) : GeminiService {
    var lastTipsRequest: StudyTipsRequest? = null
    override suspend fun explainMistake(
        courseName: String, question: String, options: List<String>, correctAnswer: String, studentAnswer: String?,
    ) = Result.success("Explicación")
    override suspend fun studyTips(request: StudyTipsRequest): Result<String> {
        lastTipsRequest = request
        return tipsResult
    }
    override suspend fun generateQuestions(courseName: String, syllabus: String, count: Int) =
        Result.success(emptyList<GeneratedQuestion>())
}

class FakeDocumentStorage : DocumentStorage {
    val deleted = mutableListOf<String>()
    var nextPath = "/files/pdfs/copy.pdf"

    override suspend fun importPdf(sourceUri: String): Result<String> = Result.success(nextPath)
    override suspend fun delete(path: String) { deleted += path }
}

fun course(id: String = "c1", name: String = "Física", importance: Importance = Importance.MEDIUM) =
    Course(id = id, name = name, importance = importance, colorArgb = 0xFFB5D8FF, createdAt = TEST_NOW)

/** Exam starting [inHours] after TEST_NOW. */
fun exam(
    id: String,
    inHours: Long,
    durationHours: Long? = null,
    courseId: String = "c1",
    weight: Double = 20.0,
    difficulty: Int = 3,
) = Exam(
    id = id, courseId = courseId, title = id, weightPercent = weight, difficulty = difficulty, createdAt = TEST_NOW,
    startAt = TEST_NOW + Duration.ofHours(inHours),
    endAt = durationHours?.let { TEST_NOW + Duration.ofHours(inHours + it) },
    examType = ExamType.PARTIAL,
)
