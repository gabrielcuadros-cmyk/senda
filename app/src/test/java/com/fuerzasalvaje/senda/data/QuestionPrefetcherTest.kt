package com.fuerzasalvaje.senda.data

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.fuerzasalvaje.senda.data.notifications.WorkManagerQuestionPrefetcher
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

/** Point 2: one unique refill job per course, and KEEP, so the same course is never queued twice. */
class QuestionPrefetcherTest {

    private val workManager = mockk<WorkManager>(relaxed = true)

    @Test
    fun `refills use one unique job per course with KEEP`() {
        val requests = mutableListOf<OneTimeWorkRequest>()
        val prefetcher = WorkManagerQuestionPrefetcher { workManager }

        prefetcher.refill("c1")
        prefetcher.refill("c1")
        prefetcher.refill("c2")

        verify(exactly = 2) { workManager.enqueueUniqueWork("prefetch-c1", ExistingWorkPolicy.KEEP, capture(requests)) }
        verify(exactly = 1) { workManager.enqueueUniqueWork("prefetch-c2", ExistingWorkPolicy.KEEP, any<OneTimeWorkRequest>()) }
        assertEquals(listOf("c1", "c1"), requests.map { it.workSpec.input.getString("courseId") })
    }
}
