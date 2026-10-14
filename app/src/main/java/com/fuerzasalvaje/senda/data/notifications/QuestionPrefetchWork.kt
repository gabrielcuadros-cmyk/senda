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
 * Generates questions with Gemini in the background (HU-15): refills a course bank, or adds the questions an
 * attempt is still missing. Needs network and retries with exponential backoff until it succeeds.
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
        val job = PrefetchJob(
            courseId = courseId,
            fresh = inputData.getBoolean(FRESH, false),
            simulacrumId = inputData.getString(SIMULACRUM_ID),
            desired = inputData.getInt(DESIRED, 0),
            topics = inputData.getString(TOPICS),
        )
        return when (runPrefetch(job)) {
            PrefetchOutcome.SUCCESS -> Result.success()
            PrefetchOutcome.RETRY -> Result.retry()
            PrefetchOutcome.FAIL -> Result.failure()
        }
    }

    companion object {
        const val COURSE_ID = "courseId"
        const val FRESH = "fresh"
        const val SIMULACRUM_ID = "simulacrumId"
        const val DESIRED = "desired"
        const val TOPICS = "topics"
    }
}

@Singleton
class WorkManagerQuestionPrefetcher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : QuestionPrefetcher {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** Appended after any job of the same course still running, so two refills never overlap. */
    override fun refill(courseId: String, fresh: Boolean, topics: String?) {
        val data = workDataOf(PrefetchQuestionsWorker.COURSE_ID to courseId, PrefetchQuestionsWorker.FRESH to fresh, PrefetchQuestionsWorker.TOPICS to topics?.take(MAX_TOPICS))
        workManager.enqueueUniqueWork("prefetch-$courseId", ExistingWorkPolicy.APPEND_OR_REPLACE, request(courseId, data))
    }

    /** Separate from the refill chain: the student is waiting for these questions now. */
    override fun topUp(courseId: String, simulacrumId: String, desired: Int, topics: String?) {
        val data = workDataOf(
            PrefetchQuestionsWorker.COURSE_ID to courseId,
            PrefetchQuestionsWorker.SIMULACRUM_ID to simulacrumId,
            PrefetchQuestionsWorker.DESIRED to desired,
            PrefetchQuestionsWorker.TOPICS to topics?.take(MAX_TOPICS),
        )
        workManager.enqueueUniqueWork("topup-$simulacrumId", ExistingWorkPolicy.REPLACE, request(courseId, data))
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

    private companion object {
        const val TAG = "question-prefetch"
        const val COURSE_TAG = "course:"
        const val BACKOFF_SECONDS = 30L
        /** WorkManager input data is limited to 10 KB. */
        const val MAX_TOPICS = 4_000
    }
}
