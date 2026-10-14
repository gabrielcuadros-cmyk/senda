package com.fuerzasalvaje.senda.data.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.fuerzasalvaje.senda.data.remote.SessionProvider
import com.fuerzasalvaje.senda.domain.repository.PdfDigestScheduler
import com.fuerzasalvaje.senda.domain.usecase.pdf.PdfDigestOutcome
import com.fuerzasalvaje.senda.domain.usecase.pdf.RunPdfDigestUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads an exam or lab PDF and gets its study summary from Gemini, once, in the background. No network constraint:
 * the text is extracted on the device right away; without connection the Gemini call fails and is retried with
 * exponential backoff.
 */
@HiltWorker
class PdfDigestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val session: SessionProvider,
    private val runDigest: RunPdfDigestUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (session.currentUid() == null) return Result.failure()
        val evaluationId = inputData.getString(EVALUATION_ID) ?: return Result.failure()
        return when (runDigest(evaluationId)) {
            PdfDigestOutcome.SUCCESS -> Result.success()
            PdfDigestOutcome.RETRY -> Result.retry()
            PdfDigestOutcome.FAIL -> Result.failure()
        }
    }

    companion object {
        const val EVALUATION_ID = "evaluationId"
    }
}

@Singleton
class WorkManagerPdfDigestScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PdfDigestScheduler {

    /** One job per evaluation: a new PDF replaces the job of the previous one; otherwise a running job is kept. */
    override fun schedule(evaluationId: String, restart: Boolean) {
        val request = OneTimeWorkRequestBuilder<PdfDigestWorker>()
            .setInputData(workDataOf(PdfDigestWorker.EVALUATION_ID to evaluationId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG)
            .addTag(ReminderScheduler.TAG_ALL)
            .build()
        val policy = if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork("pdf-digest-$evaluationId", policy, request)
    }

    private companion object {
        const val TAG = "pdf-digest"
        const val BACKOFF_SECONDS = 30L
    }
}
