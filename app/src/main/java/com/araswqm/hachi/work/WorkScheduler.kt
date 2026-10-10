package com.araswqm.hachi.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.araswqm.hachi.core.Tuning
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arka plan islerinin (WorkManager) kurulumu.
 *
 * - **Periyodik kapı kontrolu**: yalnizca cihazda calisan ucuz on eleme;
 *   ag baglantisi gerektiren LLM karari yalnizca kapiyi gecenler icin yapilir.
 *   `KEEP` ile ayni is birden fazla kez kuyruga alinmaz.
 * - **Kapanis ozetlemesi**: uygulama arka plana gecerken birikmis mesajlari
 *   kalici hafizaya yazar. Ustel geri cekilme ile ag sorunlarina dayaniklidir.
 */
@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** Periyodik kapı kontrolunu (idempotent) kurar. */
    fun ensureProactivePeriodic() {
        val request = PeriodicWorkRequestBuilder<ProactiveWorker>(
            Tuning.PROACTIVE_CHECK_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        ).build()

        workManager.enqueueUniquePeriodicWork(
            PROACTIVE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Kapanis ozetlemesini kuyruga alir. [force] true ise mesaj sayisi esigi
     * yok sayilir. Ayni ad altinda bekleyen bir is varsa yenisini eklemez.
     */
    fun scheduleSummarize(force: Boolean = true) {
        val request = OneTimeWorkRequestBuilder<SummarizeWorker>()
            .setInputData(workDataOf(SummarizeWorker.KEY_FORCE to force))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                Tuning.ONSTOP_SUMMARY_BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
            .build()

        workManager.enqueueUniqueWork(
            SUMMARIZE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val PROACTIVE_WORK_NAME = "hachi_proactive_periodic"
        const val SUMMARIZE_WORK_NAME = "hachi_summarize_onstop"
    }
}
