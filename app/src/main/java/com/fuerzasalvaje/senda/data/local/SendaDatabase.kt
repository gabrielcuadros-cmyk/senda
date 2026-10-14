package com.fuerzasalvaje.senda.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fuerzasalvaje.senda.data.local.dao.AIRecommendationDao
import com.fuerzasalvaje.senda.data.local.dao.CourseDao
import com.fuerzasalvaje.senda.data.local.dao.EvaluationDao
import com.fuerzasalvaje.senda.data.local.dao.PomodoroSessionDao
import com.fuerzasalvaje.senda.data.local.dao.QuestionDao
import com.fuerzasalvaje.senda.data.local.dao.SimulacrumDao
import com.fuerzasalvaje.senda.data.local.dao.StressCheckInDao
import com.fuerzasalvaje.senda.data.local.dao.StudyBlockDao
import com.fuerzasalvaje.senda.data.local.entity.AIRecommendationEntity
import com.fuerzasalvaje.senda.data.local.entity.CourseEntity
import com.fuerzasalvaje.senda.data.local.entity.EvaluationEntity
import com.fuerzasalvaje.senda.data.local.entity.PomodoroSessionEntity
import com.fuerzasalvaje.senda.data.local.entity.QuestionEntity
import com.fuerzasalvaje.senda.data.local.entity.SimulacrumEntity
import com.fuerzasalvaje.senda.data.local.entity.StressCheckInEntity
import com.fuerzasalvaje.senda.data.local.entity.StudyBlockEntity

/** Offline cache of the signed-in student's data. Firestore is the source of truth. */
@Database(
    entities = [
        CourseEntity::class,
        EvaluationEntity::class,
        QuestionEntity::class,
        SimulacrumEntity::class,
        AIRecommendationEntity::class,
        PomodoroSessionEntity::class,
        StudyBlockEntity::class,
        StressCheckInEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SendaDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun evaluationDao(): EvaluationDao
    abstract fun questionDao(): QuestionDao
    abstract fun simulacrumDao(): SimulacrumDao
    abstract fun aiRecommendationDao(): AIRecommendationDao
    abstract fun pomodoroSessionDao(): PomodoroSessionDao
    abstract fun studyBlockDao(): StudyBlockDao
    abstract fun stressCheckInDao(): StressCheckInDao

    companion object {
        const val NAME = "senda.db"

        /** v2: questions remember why the student reported them. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions ADD COLUMN reportReason TEXT")
            }
        }
    }
}
