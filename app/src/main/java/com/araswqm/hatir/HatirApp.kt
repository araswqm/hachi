package com.araswqm.hatir

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.araswqm.hatir.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Uygulama girisi.
 *
 * WorkManager entegrasyonu: `@HiltWorker` siniflari Hilt ile uretilmek zorunda
 * oldugu icin WorkManager'in varsayilan baslaticisi manifest'te devre disi
 * birakildi (`androidx.work.WorkManagerInitializer` -> tools:node="remove") ve
 * yerine buradaki [workManagerConfiguration] kullaniliyor. Bu yapilmazsa
 * worker'lar calisma aninda `NoSuchMethodException` ile coker.
 *
 * [onCreate] periyodik kendiliğinden mesaj kontrolunu (idempotent) kurar;
 * bir kerelik kapanis ozetlemesi [com.araswqm.hatir.MainActivity.onStop]'ta
 * tetiklenir.
 */
@HiltAndroidApp
class HatirApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var workScheduler: WorkScheduler

    override fun onCreate() {
        super.onCreate()
        workScheduler.ensureProactivePeriodic()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.WARN)
            .build()
}
