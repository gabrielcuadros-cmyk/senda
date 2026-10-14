package com.fuerzasalvaje.senda.data.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.fuerzasalvaje.senda.data.remote.SessionProvider
import com.fuerzasalvaje.senda.domain.model.NotificationTarget
import com.fuerzasalvaje.senda.domain.usecase.focus.BuildDailySummaryUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock

/** Posts a notification whose texts and target screen were prepared when it was scheduled. */
@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: SendaNotifier,
    private val session: SessionProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (session.currentUid() == null) return Result.success()
        val channel = SendaChannel.entries.firstOrNull { it.id == inputData.getString(CHANNEL) } ?: SendaChannel.EVALUATIONS
        notifier.post(
            id = inputData.getInt(ID, 0),
            channel = channel,
            title = inputData.getString(TITLE).orEmpty(),
            text = inputData.getString(TEXT).orEmpty(),
            target = NotificationTarget.decode(inputData.getString(TARGET)) ?: NotificationTarget.Home,
        )
        return Result.success()
    }

    companion object {
        private const val ID = "id"
        private const val CHANNEL = "channel"
        private const val TITLE = "title"
        private const val TEXT = "text"
        private const val TARGET = "target"

        fun input(id: Int, channel: SendaChannel, title: String, text: String, target: NotificationTarget): Data =
            workDataOf(ID to id, CHANNEL to channel.id, TITLE to title, TEXT to text, TARGET to target.encode())
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
            notifier.post(
                SUMMARY_ID, SendaChannel.EVALUATIONS, ReminderTexts.SUMMARY_TITLE,
                ReminderTexts.summaryText(summary, clock.zone), NotificationTarget.Home,
            )
        }
        return Result.success()
    }

    private companion object {
        const val SUMMARY_ID = 730
    }
}
