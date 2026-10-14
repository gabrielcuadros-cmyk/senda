package com.fuerzasalvaje.senda.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.fuerzasalvaje.senda.data.local.entity.AIRecommendationEntity
import com.fuerzasalvaje.senda.data.local.entity.CourseEntity
import com.fuerzasalvaje.senda.data.local.entity.EvaluationEntity
import com.fuerzasalvaje.senda.data.local.entity.PdfDigestEntity
import com.fuerzasalvaje.senda.data.local.entity.PomodoroSessionEntity
import com.fuerzasalvaje.senda.data.local.entity.QuestionEntity
import com.fuerzasalvaje.senda.data.local.entity.SimulacrumEntity
import com.fuerzasalvaje.senda.data.local.entity.StressCheckInEntity
import com.fuerzasalvaje.senda.data.local.entity.StudyBlockEntity
import kotlinx.coroutines.flow.Flow

/** Operations every per-student table supports. Queries are declared by each DAO. */
interface OwnedDao<E> {
    @Upsert
    suspend fun upsert(entity: E)

    fun observeAll(ownerUid: String): Flow<List<E>>
    fun observeById(ownerUid: String, id: String): Flow<E?>
    suspend fun getById(ownerUid: String, id: String): E?
    suspend fun deleteById(ownerUid: String, id: String)
}

@Dao
interface CourseDao : OwnedDao<CourseEntity> {
    @Query("SELECT * FROM courses WHERE ownerUid = :ownerUid ORDER BY name COLLATE NOCASE")
    override fun observeAll(ownerUid: String): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE ownerUid = :ownerUid AND id = :id")
    override fun observeById(ownerUid: String, id: String): Flow<CourseEntity?>

    @Query("SELECT * FROM courses WHERE ownerUid = :ownerUid AND id = :id")
    override suspend fun getById(ownerUid: String, id: String): CourseEntity?

    @Query("DELETE FROM courses WHERE ownerUid = :ownerUid AND id = :id")
    override suspend fun deleteById(ownerUid: String, id: String)
}

@Dao
interface EvaluationDao : OwnedDao<EvaluationEntity> {
    @Query("SELECT * FROM evaluations WHERE ownerUid = :ownerUid ORDER BY startAt")
    override fun observeAll(ownerUid: String): Flow<List<EvaluationEntity>>

    @Query("SELECT * FROM evaluations WHERE ownerUid = :ownerUid AND courseId = :courseId ORDER BY startAt")
    fun observeByCourse(ownerUid: String, courseId: String): Flow<List<EvaluationEntity>>

    @Query("SELECT * FROM evaluations WHERE ownerUid = :ownerUid AND id = :id")
    override fun observeById(ownerUid: String, id: String): Flow<EvaluationEntity?>

    @Query("SELECT * FROM evaluations WHERE ownerUid = :ownerUid AND id = :id")
    override suspend fun getById(ownerUid: String, id: String): EvaluationEntity?

    @Query("DELETE FROM evaluations WHERE ownerUid = :ownerUid AND id = :id")
    override suspend fun deleteById(ownerUid: String, id: String)
}

@Dao
interface QuestionDao : OwnedDao<QuestionEntity> {
    @Query("SELECT * FROM questions WHERE ownerUid = :ownerUid ORDER BY createdAt")
    override fun observeAll(ownerUid: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE ownerUid = :ownerUid AND courseId = :courseId ORDER BY createdAt")
    fun observeByCourse(ownerUid: String, courseId: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE ownerUid = :ownerUid AND id = :id")
    override fun observeById(ownerUid: String, id: String): Flow<QuestionEntity?>

    @Query("SELECT * FROM questions WHERE ownerUid = :ownerUid AND id = :id")
    override suspend fun getById(ownerUid: String, id: String): QuestionEntity?

    @Query("DELETE FROM questions WHERE ownerUid = :ownerUid AND id = :id")
    override suspend fun deleteById(ownerUid: String, id: String)
}

@Dao
interface SimulacrumDao : OwnedDao<SimulacrumEntity> {
    @Query("SELECT * FROM simulacra WHERE ownerUid = :ownerUid ORDER BY startedAt DESC")
    override fun observeAll(ownerUid: String): Flow<List<SimulacrumEntity>>

    @Query("SELECT * FROM simulacra WHERE ownerUid = :ownerUid AND courseId = :courseId ORDER BY startedAt DESC")
    fun observeByCourse(ownerUid: String, courseId: String): Flow<List<SimulacrumEntity>>

    @Query("SELECT * FROM simulacra WHERE ownerUid = :ownerUid AND id = :id")
    override fun observeById(ownerUid: String, id: String): Flow<SimulacrumEntity?>

    @Query("SELECT * FROM simulacra WHERE ownerUid = :ownerUid AND id = :id")
    override suspend fun getById(ownerUid: String, id: String): SimulacrumEntity?

    @Query("DELETE FROM simulacra WHERE ownerUid = :ownerUid AND id = :id")
    override suspend fun deleteById(ownerUid: String, id: String)
}

@Dao
interface AIRecommendationDao : OwnedDao<AIRecommendationEntity> {
    @Query("SELECT * FROM ai_recommendations WHERE ownerUid = :ownerUid ORDER BY createdAt DESC")
    override fun observeAll(ownerUid: String): Flow<List<AIRecommendationEntity>>

    @Query("SELECT * FROM ai_recommendations WHERE ownerUid = :ownerUid AND id = :id")
    override fun observeById(ownerUid: String, id: String): Flow<AIRecommendationEntity?>

    @Query("SELECT * FROM ai_recommendations WHERE ownerUid = :ownerUid AND id = :id")
    override suspend fun getById(ownerUid: String, id: String): AIRecommendationEntity?

    @Query("DELETE FROM ai_recommendations WHERE ownerUid = :ownerUid AND id = :id")
    override suspend fun deleteById(ownerUid: String, id: String)
}

@Dao
interface PomodoroSessionDao : OwnedDao<PomodoroSessionEntity> {
    @Query("SELECT * FROM pomodoro_sessions WHERE ownerUid = :ownerUid ORDER BY startedAt DESC")
    override fun observeAll(ownerUid: String): Flow<List<PomodoroSessionEntity>>

    @Query("SELECT * FROM pomodoro_sessions WHERE ownerUid = :ownerUid AND id = :id")
    override fun observeById(ownerUid: String, id: String): Flow<PomodoroSessionEntity?>

    @Query("SELECT * FROM pomodoro_sessions WHERE ownerUid = :ownerUid AND id = :id")
    override suspend fun getById(ownerUid: String, id: String): PomodoroSessionEntity?

    @Query("DELETE FROM pomodoro_sessions WHERE ownerUid = :ownerUid AND id = :id")
    override suspend fun deleteById(ownerUid: String, id: String)
}

@Dao
interface StudyBlockDao : OwnedDao<StudyBlockEntity> {
    @Query("SELECT * FROM study_blocks WHERE ownerUid = :ownerUid ORDER BY startAt")
    override fun observeAll(ownerUid: String): Flow<List<StudyBlockEntity>>

    @Query("SELECT * FROM study_blocks WHERE ownerUid = :ownerUid AND id = :id")
    override fun observeById(ownerUid: String, id: String): Flow<StudyBlockEntity?>

    @Query("SELECT * FROM study_blocks WHERE ownerUid = :ownerUid AND id = :id")
    override suspend fun getById(ownerUid: String, id: String): StudyBlockEntity?

    @Query("DELETE FROM study_blocks WHERE ownerUid = :ownerUid AND id = :id")
    override suspend fun deleteById(ownerUid: String, id: String)
}

@Dao
interface StressCheckInDao : OwnedDao<StressCheckInEntity> {
    @Query("SELECT * FROM stress_checkins WHERE ownerUid = :ownerUid ORDER BY id DESC")
    override fun observeAll(ownerUid: String): Flow<List<StressCheckInEntity>>

    @Query("SELECT * FROM stress_checkins WHERE ownerUid = :ownerUid AND id = :id")
    override fun observeById(ownerUid: String, id: String): Flow<StressCheckInEntity?>

    @Query("SELECT * FROM stress_checkins WHERE ownerUid = :ownerUid AND id = :id")
    override suspend fun getById(ownerUid: String, id: String): StressCheckInEntity?

    @Query("DELETE FROM stress_checkins WHERE ownerUid = :ownerUid AND id = :id")
    override suspend fun deleteById(ownerUid: String, id: String)
}

@Dao
interface PdfDigestDao : OwnedDao<PdfDigestEntity> {
    @Query("SELECT * FROM pdf_digests WHERE ownerUid = :ownerUid")
    override fun observeAll(ownerUid: String): Flow<List<PdfDigestEntity>>

    @Query("SELECT * FROM pdf_digests WHERE ownerUid = :ownerUid AND courseId = :courseId")
    fun observeByCourse(ownerUid: String, courseId: String): Flow<List<PdfDigestEntity>>

    @Query("SELECT * FROM pdf_digests WHERE ownerUid = :ownerUid AND id = :id")
    override fun observeById(ownerUid: String, id: String): Flow<PdfDigestEntity?>

    @Query("SELECT * FROM pdf_digests WHERE ownerUid = :ownerUid AND id = :id")
    override suspend fun getById(ownerUid: String, id: String): PdfDigestEntity?

    @Query("DELETE FROM pdf_digests WHERE ownerUid = :ownerUid AND id = :id")
    override suspend fun deleteById(ownerUid: String, id: String)
}
