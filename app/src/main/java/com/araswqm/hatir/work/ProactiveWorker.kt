package com.araswqm.hatir.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.araswqm.hatir.data.proactive.ProactiveRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Periyodik kendiliğinden mesaj kontrolu.
 *
 * Yerel "kapi" (gate) tamamen cihazda calisir; yalnizca kapiyi gecen
 * karakterler icin tek bir LLM karar cagrisi yapilir. Tek karaktere ait
 * hatalar [ProactiveRepository.runCheck] icinde yutulur ve log'a yazilir;
 * buraya yalnizca butunsel bir sorun (or. veritabani erisimi) duserse
 * `retry()` doner. Iptal daima yeniden firlatilir.
 */
@HiltWorker
class ProactiveWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val proactive: ProactiveRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        proactive.runCheck()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.retry()
    }
}
