package com.fuerzasalvaje.senda.di

import android.content.Context
import androidx.room.Room
import com.fuerzasalvaje.senda.data.local.SendaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SendaDatabase =
        Room.databaseBuilder(context, SendaDatabase::class.java, SendaDatabase.NAME)
            .addMigrations(SendaDatabase.MIGRATION_1_2)
            .build()

    @Provides fun provideCourseDao(db: SendaDatabase) = db.courseDao()
    @Provides fun provideEvaluationDao(db: SendaDatabase) = db.evaluationDao()
    @Provides fun provideQuestionDao(db: SendaDatabase) = db.questionDao()
    @Provides fun provideSimulacrumDao(db: SendaDatabase) = db.simulacrumDao()
    @Provides fun provideAIRecommendationDao(db: SendaDatabase) = db.aiRecommendationDao()
    @Provides fun providePomodoroSessionDao(db: SendaDatabase) = db.pomodoroSessionDao()
    @Provides fun provideStudyBlockDao(db: SendaDatabase) = db.studyBlockDao()
    @Provides fun provideStressCheckInDao(db: SendaDatabase) = db.stressCheckInDao()
    @Provides fun providePdfDigestDao(db: SendaDatabase) = db.pdfDigestDao()
}
