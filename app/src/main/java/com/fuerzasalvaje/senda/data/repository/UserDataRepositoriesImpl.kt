package com.fuerzasalvaje.senda.data.repository

import com.fuerzasalvaje.senda.data.local.dao.AIRecommendationDao
import com.fuerzasalvaje.senda.data.local.dao.CourseDao
import com.fuerzasalvaje.senda.data.local.dao.EvaluationDao
import com.fuerzasalvaje.senda.data.local.dao.PdfDigestDao
import com.fuerzasalvaje.senda.data.local.dao.PomodoroSessionDao
import com.fuerzasalvaje.senda.data.local.dao.QuestionDao
import com.fuerzasalvaje.senda.data.local.dao.SimulacrumDao
import com.fuerzasalvaje.senda.data.local.dao.StressCheckInDao
import com.fuerzasalvaje.senda.data.local.dao.StudyBlockDao
import com.fuerzasalvaje.senda.data.local.entity.AIRecommendationEntity
import com.fuerzasalvaje.senda.data.local.entity.CourseEntity
import com.fuerzasalvaje.senda.data.local.entity.EvaluationEntity
import com.fuerzasalvaje.senda.data.local.entity.PdfDigestEntity
import com.fuerzasalvaje.senda.data.local.entity.PomodoroSessionEntity
import com.fuerzasalvaje.senda.data.local.entity.QuestionEntity
import com.fuerzasalvaje.senda.data.local.entity.SimulacrumEntity
import com.fuerzasalvaje.senda.data.local.entity.StressCheckInEntity
import com.fuerzasalvaje.senda.data.local.entity.StudyBlockEntity
import com.fuerzasalvaje.senda.data.mapper.localPdfPath
import com.fuerzasalvaje.senda.data.mapper.toDomain
import com.fuerzasalvaje.senda.data.mapper.toDto
import com.fuerzasalvaje.senda.data.mapper.toEntity
import com.fuerzasalvaje.senda.data.remote.FirestorePaths
import com.fuerzasalvaje.senda.data.remote.SessionProvider
import com.fuerzasalvaje.senda.data.remote.dto.AIRecommendationDto
import com.fuerzasalvaje.senda.data.remote.dto.CourseDto
import com.fuerzasalvaje.senda.data.remote.dto.EvaluationDto
import com.fuerzasalvaje.senda.data.remote.dto.PdfDigestDto
import com.fuerzasalvaje.senda.data.remote.dto.PomodoroSessionDto
import com.fuerzasalvaje.senda.data.remote.dto.QuestionDto
import com.fuerzasalvaje.senda.data.remote.dto.SimulacrumDto
import com.fuerzasalvaje.senda.data.remote.dto.StressCheckInDto
import com.fuerzasalvaje.senda.data.remote.dto.StudyBlockDto
import com.fuerzasalvaje.senda.domain.model.AIRecommendation
import com.fuerzasalvaje.senda.domain.model.Course
import com.fuerzasalvaje.senda.domain.model.Evaluation
import com.fuerzasalvaje.senda.domain.model.PdfDigest
import com.fuerzasalvaje.senda.domain.model.PomodoroSession
import com.fuerzasalvaje.senda.domain.model.Question
import com.fuerzasalvaje.senda.domain.model.Simulacrum
import com.fuerzasalvaje.senda.domain.model.StressCheckIn
import com.fuerzasalvaje.senda.domain.model.StudyBlock
import com.fuerzasalvaje.senda.domain.repository.AIRecommendationRepository
import com.fuerzasalvaje.senda.domain.repository.CourseRepository
import com.fuerzasalvaje.senda.domain.repository.EvaluationRepository
import com.fuerzasalvaje.senda.domain.repository.PdfDigestRepository
import com.fuerzasalvaje.senda.domain.repository.PomodoroSessionRepository
import com.fuerzasalvaje.senda.domain.repository.QuestionRepository
import com.fuerzasalvaje.senda.domain.repository.SimulacrumRepository
import com.fuerzasalvaje.senda.domain.repository.StressCheckInRepository
import com.fuerzasalvaje.senda.domain.repository.StudyBlockRepository
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CourseRepositoryImpl @Inject constructor(
    session: SessionProvider,
    firestore: FirebaseFirestore,
    dao: CourseDao,
) : BaseOwnedRepository<Course, CourseEntity, CourseDto>(
    session, firestore, dao, FirestorePaths.COURSES, CourseDto::class.java,
), CourseRepository {
    override fun idOf(item: Course) = item.id
    override fun entityOf(item: Course, uid: String) = item.toEntity(uid)
    override fun domainOf(entity: CourseEntity) = entity.toDomain()
    override fun dtoOf(item: Course) = item.toDto()
    override fun remoteDomainOf(dto: CourseDto, id: String, local: CourseEntity?) = dto.toDomain(id)
}

@Singleton
class EvaluationRepositoryImpl @Inject constructor(
    session: SessionProvider,
    firestore: FirebaseFirestore,
    private val dao: EvaluationDao,
) : BaseOwnedRepository<Evaluation, EvaluationEntity, EvaluationDto>(
    session, firestore, dao, FirestorePaths.EVALUATIONS, EvaluationDto::class.java,
), EvaluationRepository {
    override fun idOf(item: Evaluation) = item.id
    override fun entityOf(item: Evaluation, uid: String) = item.toEntity(uid)
    override fun domainOf(entity: EvaluationEntity) = entity.toDomain()
    override fun dtoOf(item: Evaluation) = item.toDto()

    // Keeps the PDF that only exists on this device when a remote update arrives (D-11).
    override fun remoteDomainOf(dto: EvaluationDto, id: String, local: EvaluationEntity?) =
        dto.toDomain(id, local?.localPdfPath)

    override fun observeByCourse(courseId: String) = observeList { uid -> dao.observeByCourse(uid, courseId) }
}

@Singleton
class QuestionRepositoryImpl @Inject constructor(
    session: SessionProvider,
    firestore: FirebaseFirestore,
    private val dao: QuestionDao,
) : BaseOwnedRepository<Question, QuestionEntity, QuestionDto>(
    session, firestore, dao, FirestorePaths.QUESTIONS, QuestionDto::class.java,
), QuestionRepository {
    override fun idOf(item: Question) = item.id
    override fun entityOf(item: Question, uid: String) = item.toEntity(uid)
    override fun domainOf(entity: QuestionEntity) = entity.toDomain()
    override fun dtoOf(item: Question) = item.toDto()
    override fun remoteDomainOf(dto: QuestionDto, id: String, local: QuestionEntity?) = dto.toDomain(id)
    override fun observeByCourse(courseId: String) = observeList { uid -> dao.observeByCourse(uid, courseId) }
}

@Singleton
class SimulacrumRepositoryImpl @Inject constructor(
    session: SessionProvider,
    firestore: FirebaseFirestore,
    private val dao: SimulacrumDao,
) : BaseOwnedRepository<Simulacrum, SimulacrumEntity, SimulacrumDto>(
    session, firestore, dao, FirestorePaths.SIMULACRA, SimulacrumDto::class.java,
), SimulacrumRepository {
    override fun idOf(item: Simulacrum) = item.id
    override fun entityOf(item: Simulacrum, uid: String) = item.toEntity(uid)
    override fun domainOf(entity: SimulacrumEntity) = entity.toDomain()
    override fun dtoOf(item: Simulacrum) = item.toDto()
    override fun remoteDomainOf(dto: SimulacrumDto, id: String, local: SimulacrumEntity?) = dto.toDomain(id)
    override fun observeByCourse(courseId: String) = observeList { uid -> dao.observeByCourse(uid, courseId) }
}

@Singleton
class AIRecommendationRepositoryImpl @Inject constructor(
    session: SessionProvider,
    firestore: FirebaseFirestore,
    dao: AIRecommendationDao,
) : BaseOwnedRepository<AIRecommendation, AIRecommendationEntity, AIRecommendationDto>(
    session, firestore, dao, FirestorePaths.AI_RECOMMENDATIONS, AIRecommendationDto::class.java,
), AIRecommendationRepository {
    override fun idOf(item: AIRecommendation) = item.id
    override fun entityOf(item: AIRecommendation, uid: String) = item.toEntity(uid)
    override fun domainOf(entity: AIRecommendationEntity) = entity.toDomain()
    override fun dtoOf(item: AIRecommendation) = item.toDto()
    override fun remoteDomainOf(dto: AIRecommendationDto, id: String, local: AIRecommendationEntity?) =
        dto.toDomain(id)
}

@Singleton
class PomodoroSessionRepositoryImpl @Inject constructor(
    session: SessionProvider,
    firestore: FirebaseFirestore,
    dao: PomodoroSessionDao,
) : BaseOwnedRepository<PomodoroSession, PomodoroSessionEntity, PomodoroSessionDto>(
    session, firestore, dao, FirestorePaths.POMODORO_SESSIONS, PomodoroSessionDto::class.java,
), PomodoroSessionRepository {
    override fun idOf(item: PomodoroSession) = item.id
    override fun entityOf(item: PomodoroSession, uid: String) = item.toEntity(uid)
    override fun domainOf(entity: PomodoroSessionEntity) = entity.toDomain()
    override fun dtoOf(item: PomodoroSession) = item.toDto()
    override fun remoteDomainOf(dto: PomodoroSessionDto, id: String, local: PomodoroSessionEntity?) =
        dto.toDomain(id)
}

@Singleton
class StudyBlockRepositoryImpl @Inject constructor(
    session: SessionProvider,
    firestore: FirebaseFirestore,
    dao: StudyBlockDao,
) : BaseOwnedRepository<StudyBlock, StudyBlockEntity, StudyBlockDto>(
    session, firestore, dao, FirestorePaths.STUDY_BLOCKS, StudyBlockDto::class.java,
), StudyBlockRepository {
    override fun idOf(item: StudyBlock) = item.id
    override fun entityOf(item: StudyBlock, uid: String) = item.toEntity(uid)
    override fun domainOf(entity: StudyBlockEntity) = entity.toDomain()
    override fun dtoOf(item: StudyBlock) = item.toDto()
    override fun remoteDomainOf(dto: StudyBlockDto, id: String, local: StudyBlockEntity?) = dto.toDomain(id)
}

@Singleton
class StressCheckInRepositoryImpl @Inject constructor(
    session: SessionProvider,
    firestore: FirebaseFirestore,
    dao: StressCheckInDao,
) : BaseOwnedRepository<StressCheckIn, StressCheckInEntity, StressCheckInDto>(
    session, firestore, dao, FirestorePaths.STRESS_CHECKINS, StressCheckInDto::class.java,
), StressCheckInRepository {
    override fun idOf(item: StressCheckIn) = item.id
    override fun entityOf(item: StressCheckIn, uid: String) = item.toEntity(uid)
    override fun domainOf(entity: StressCheckInEntity) = entity.toDomain()
    override fun dtoOf(item: StressCheckIn) = item.toDto()
    override fun remoteDomainOf(dto: StressCheckInDto, id: String, local: StressCheckInEntity?) =
        dto.toDomain(id)
}

@Singleton
class PdfDigestRepositoryImpl @Inject constructor(
    session: SessionProvider,
    firestore: FirebaseFirestore,
    private val dao: PdfDigestDao,
) : BaseOwnedRepository<PdfDigest, PdfDigestEntity, PdfDigestDto>(
    session, firestore, dao, FirestorePaths.PDF_DIGESTS, PdfDigestDto::class.java,
), PdfDigestRepository {
    override fun idOf(item: PdfDigest) = item.id
    override fun entityOf(item: PdfDigest, uid: String) = item.toEntity(uid)
    override fun domainOf(entity: PdfDigestEntity) = entity.toDomain()
    override fun dtoOf(item: PdfDigest) = item.toDto()

    // Keeps the text extracted on this device (D-11).
    override fun remoteDomainOf(dto: PdfDigestDto, id: String, local: PdfDigestEntity?) = dto.toDomain(id, local)
    override fun observeByCourse(courseId: String) = observeList { uid -> dao.observeByCourse(uid, courseId) }
}
