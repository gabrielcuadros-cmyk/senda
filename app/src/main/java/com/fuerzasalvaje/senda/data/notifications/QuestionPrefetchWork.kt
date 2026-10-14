package com.fuerzasalvaje.senda.data.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.fuerzasalvaje.senda.data.remote.SessionProvider
import com.fuerzasalvaje.senda.domain.repository.PrefetchStatus
import com.fuerzasalvaje.senda.domain.repository.QuestionPrefetcher
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.PrefetchJob
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.PrefetchOutcome
import com.fuerzasalvaje.senda.domain.usecase.simulacrum.RunPrefetchUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Refills a course question bank with Gemini in the background (HU-15), only when it is needed (see
 * RunPrefetchUseCase). Needs network and retries with exponential backoff.
 */
@HiltWorker
class PrefetchQuestionsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val session: SessionProvider,
    private val runPrefetch: RunPrefetchUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (session.currentUid() == null) return Result.failure()
        val courseId = inputData.getString(COURSE_ID) ?: return Result.failure()
        val job = PrefetchJob(courseId = courseId, topics = inputData.getString(TOPICS))
        return when (runPrefetch(job)) {
            PrefetchOutcome.SUCCESS -> Result.success()
            PrefetchOutcome.RETRY -> Result.retry()
            PrefetchOutcome.FAIL -> Result.failure()
        }
    }

    companion object {
        const val COURSE_ID = "courseId"
        const val TOPICS = "topics"
    }
}

@Singleton
class WorkManagerQuestionPrefetcher(private val workManagerOf: () -> WorkManager) : QuestionPrefetcher {

    @Inject constructor(@ApplicationContext context: Context) : this({ WorkManager.getInstance(context) })

    private val workManager: WorkManager get() = workManagerOf()

    /** One unique job per course with KEEP: while one is queued or running, new requests are dropped. */
    override fun refill(courseId: String, topics: String?) {
        val data = workDataOf(PrefetchQuestionsWorker.COURSE_ID to courseId, PrefetchQuestionsWorker.TOPICS to topics?.take(MAX_TOPICS))
        workManager.enqueueUniqueWork(uniqueName(courseId), ExistingWorkPolicy.KEEP, request(courseId, data))
    }

    override fun observeStatus(): Flow<Map<String, PrefetchStatus>> =
        workManager.getWorkInfosByTagFlow(TAG).map { infos ->
            infos.groupBy { info -> info.tags.firstOrNull { it.startsWith(COURSE_TAG) }?.removePrefix(COURSE_TAG) }
                .filterKeys { it != null }
                .mapKeys { it.key!! }
                .mapValues { (_, list) -> status(list) }
        }

    private fun status(infos: List<WorkInfo>): PrefetchStatus {
        val active = infos.filter { !it.state.isFinished }
        return when {
            active.any { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0 } -> PrefetchStatus.RETRYING
            active.isNotEmpty() -> PrefetchStatus.PREPARING
            // Finished work is kept for a while: a failure only counts when nothing succeeded since.
            infos.any { it.state == WorkInfo.State.FAILED } && infos.none { it.state == WorkInfo.State.SUCCEEDED } ->
                PrefetchStatus.FAILED
            else -> PrefetchStatus.IDLE
        }
    }

    private fun request(courseId: String, data: androidx.work.Data) =
        OneTimeWorkRequestBuilder<PrefetchQuestionsWorker>()
            .setInputData(data)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG)
            .addTag("$COURSE_TAG$courseId")
            .addTag(ReminderScheduler.TAG_ALL)
            .build()

    companion object {
        fun uniqueName(courseId: String) = "prefetch-$courseId"

        private const val TAG = "question-prefetch"
        private const val COURSE_TAG = "course:"
        private const val BACKOFF_SECONDS = 30L
        /** WorkManager input data is limited to 10 KB. */
        private const val MAX_TOPICS = 4_000
    }
}
