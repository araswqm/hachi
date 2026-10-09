package com.araswqm.hatir.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.araswqm.hatir.data.db.dao.CharacterDao
import com.araswqm.hatir.data.memory.MemorySummarizer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Hafiza ozetleyicisini karakterler icin tetikler.
 *
 * Normalde sohbet akisi her [com.araswqm.hatir.core.Tuning.SUMMARY_EVERY_MESSAGES]
 * mesajda ozetlemeyi kendisi tetikler; bu worker **uygulama arka plana
 * gecerken** (`force = true`) calisir ve birikmis ama esigi asmamis mesajlari
 * da kalici hale getirir. Bekleyen mesaji olmayan karakter icin
 * [MemorySummarizer.summarize] zaten hizlica `false` doner.
 */
@HiltWorker
class SummarizeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val characterDao: CharacterDao,
    private val summarizer: MemorySummarizer,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val force = inputData.getBoolean(KEY_FORCE, true)
        val ids = try {
            characterDao.observeAll().first().map { it.id }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return Result.retry()
        }

        var failed = false
        ids.forEach { id ->
            try {
                summarizer.summarize(id, force = force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Tek karakterin ozetlenememesi digerlerini engellemesin.
                failed = true
            }
        }
        // Yeniden deneme idempotenttir: bekleyen mesaji olmayan karakter icin
        // ozetleyici no-op doner. Bu yuzden yalnizca gercek bir istisna
        // gorulduyse retry; aksi halde basari yeterlidir.
        return if (failed) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_FORCE = "force"
    }
}
