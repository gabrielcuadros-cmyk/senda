package com.fuerzasalvaje.senda.data.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.fuerzasalvaje.senda.data.remote.SessionProvider
import com.fuerzasalvaje.senda.domain.usecase.focus.BuildDailySummaryUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock

/** Posts a notification whose texts were prepared when it was scheduled. */
@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: SendaNotifier,
    private val session: SessionProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (session.currentUid() == null) return Result.success()
        val channel = SendaChannel.entries.firstOrNull { it.id == inputData.getString(CHANNEL) } ?: SendaChannel.REMINDERS
        notifier.post(
            id = inputData.getInt(ID, 0),
            channel = channel,
            title = inputData.getString(TITLE).orEmpty(),
            text = inputData.getString(TEXT).orEmpty(),
        )
        return Result.success()
    }

    companion object {
        private const val ID = "id"
        private const val CHANNEL = "channel"
        private const val TITLE = "title"
        private const val TEXT = "text"

        fun input(id: Int, channel: SendaChannel, title: String, text: String): Data =
            workDataOf(ID to id, CHANNEL to channel.id, TITLE to title, TEXT to text)
    }
}

/** D-13: 07:30 summary, built at delivery time so it reflects the latest data. */
@HiltWorker
class DailySummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: SendaNotifier,
    private val session: SessionProvider,
    private val buildSummary: BuildDailySummaryUseCase,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (session.currentUid() == null) return Result.success()
        val summary = buildSummary()
        if (!summary.isEmpty) {
            notifier.post(SUMMARY_ID, SendaChannel.REMINDERS, ReminderTexts.SUMMARY_TITLE, ReminderTexts.summaryText(summary, clock.zone))
        }
        return Result.success()
    }

    private companion object {
        const val SUMMARY_ID = 730
    }
}
