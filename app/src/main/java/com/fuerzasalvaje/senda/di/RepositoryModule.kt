package com.fuerzasalvaje.senda.di

import com.fuerzasalvaje.senda.data.local.LocalDocumentStorage
import com.fuerzasalvaje.senda.data.focus.PomodoroController
import com.fuerzasalvaje.senda.data.notifications.WorkManagerQuestionPrefetcher
import com.fuerzasalvaje.senda.data.remote.FirebaseSessionProvider
import com.fuerzasalvaje.senda.data.remote.SessionProvider
import com.fuerzasalvaje.senda.data.repository.AIRecommendationRepositoryImpl
import com.fuerzasalvaje.senda.data.repository.AuthRepositoryImpl
import com.fuerzasalvaje.senda.data.repository.CourseRepositoryImpl
import com.fuerzasalvaje.senda.data.repository.EvaluationRepositoryImpl
import com.fuerzasalvaje.senda.data.repository.FirestoreAvailabilityRepository
import com.fuerzasalvaje.senda.data.repository.FirestoreSettingsRepository
import com.fuerzasalvaje.senda.data.repository.PomodoroSessionRepositoryImpl
import com.fuerzasalvaje.senda.data.repository.QuestionRepositoryImpl
import com.fuerzasalvaje.senda.data.repository.SimulacrumRepositoryImpl
import com.fuerzasalvaje.senda.data.repository.StressCheckInRepositoryImpl
import com.fuerzasalvaje.senda.data.repository.StudyBlockRepositoryImpl
import com.fuerzasalvaje.senda.data.repository.SyncableRepository
import com.fuerzasalvaje.senda.data.sync.MinuteTicker
import com.fuerzasalvaje.senda.domain.repository.AIRecommendationRepository
import com.fuerzasalvaje.senda.domain.repository.AuthRepository
import com.fuerzasalvaje.senda.domain.repository.AvailabilityRepository
import com.fuerzasalvaje.senda.domain.repository.CourseRepository
import com.fuerzasalvaje.senda.domain.repository.DocumentStorage
import com.fuerzasalvaje.senda.domain.repository.EvaluationRepository
import com.fuerzasalvaje.senda.domain.repository.PomodoroTimer
import com.fuerzasalvaje.senda.domain.repository.PomodoroSessionRepository
import com.fuerzasalvaje.senda.domain.repository.QuestionPrefetcher
import com.fuerzasalvaje.senda.domain.repository.QuestionRepository
import com.fuerzasalvaje.senda.domain.repository.SettingsRepository
import com.fuerzasalvaje.senda.domain.repository.SimulacrumRepository
import com.fuerzasalvaje.senda.domain.repository.StressCheckInRepository
import com.fuerzasalvaje.senda.domain.repository.StudyBlockRepository
import com.fuerzasalvaje.senda.domain.repository.Ticker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

/** Scope that lives as long as the app process (sync listeners). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds abstract fun bindSessionProvider(impl: FirebaseSessionProvider): SessionProvider
    @Binds abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
    @Binds abstract fun bindDocumentStorage(impl: LocalDocumentStorage): DocumentStorage
    @Binds abstract fun bindAvailabilityRepository(impl: FirestoreAvailabilityRepository): AvailabilityRepository
    @Binds abstract fun bindSettingsRepository(impl: FirestoreSettingsRepository): SettingsRepository
    @Binds abstract fun bindPomodoroTimer(impl: PomodoroController): PomodoroTimer
    @Binds abstract fun bindTicker(impl: MinuteTicker): Ticker
    @Binds abstract fun bindQuestionPrefetcher(impl: WorkManagerQuestionPrefetcher): QuestionPrefetcher

    @Binds abstract fun bindCourseRepository(impl: CourseRepositoryImpl): CourseRepository
    @Binds abstract fun bindEvaluationRepository(impl: EvaluationRepositoryImpl): EvaluationRepository
    @Binds abstract fun bindQuestionRepository(impl: QuestionRepositoryImpl): QuestionRepository
    @Binds abstract fun bindSimulacrumRepository(impl: SimulacrumRepositoryImpl): SimulacrumRepository
    @Binds abstract fun bindAIRecommendationRepository(impl: AIRecommendationRepositoryImpl): AIRecommendationRepository
    @Binds abstract fun bindPomodoroSessionRepository(impl: PomodoroSessionRepositoryImpl): PomodoroSessionRepository
    @Binds abstract fun bindStudyBlockRepository(impl: StudyBlockRepositoryImpl): StudyBlockRepository
    @Binds abstract fun bindStressCheckInRepository(impl: StressCheckInRepositoryImpl): StressCheckInRepository

    // Every collection the SyncManager mirrors into Room.
    @Binds @IntoSet abstract fun syncCourses(impl: CourseRepositoryImpl): SyncableRepository
    @Binds @IntoSet abstract fun syncEvaluations(impl: EvaluationRepositoryImpl): SyncableRepository
    @Binds @IntoSet abstract fun syncQuestions(impl: QuestionRepositoryImpl): SyncableRepository
    @Binds @IntoSet abstract fun syncSimulacra(impl: SimulacrumRepositoryImpl): SyncableRepository
    @Binds @IntoSet abstract fun syncAIRecommendations(impl: AIRecommendationRepositoryImpl): SyncableRepository
    @Binds @IntoSet abstract fun syncPomodoroSessions(impl: PomodoroSessionRepositoryImpl): SyncableRepository
    @Binds @IntoSet abstract fun syncStudyBlocks(impl: StudyBlockRepositoryImpl): SyncableRepository
    @Binds @IntoSet abstract fun syncStressCheckIns(impl: StressCheckInRepositoryImpl): SyncableRepository

    companion object {
        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.systemDefaultZone()

        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
