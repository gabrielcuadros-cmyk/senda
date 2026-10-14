package com.fuerzasalvaje.senda

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.fuerzasalvaje.senda.data.notifications.ReminderScheduler
import com.fuerzasalvaje.senda.data.notifications.SendaNotifier
import com.fuerzasalvaje.senda.data.sync.SyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SendaApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var notifier: SendaNotifier

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        syncManager.start()
        reminderScheduler.start()
    }

    // Lets WorkManager build @HiltWorker workers; the default initializer is removed in the manifest.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
