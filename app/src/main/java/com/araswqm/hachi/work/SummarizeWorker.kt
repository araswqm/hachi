package com.araswqm.hachi.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.araswqm.hachi.data.db.dao.ChatDao
import com.araswqm.hachi.data.memory.MemorySummarizer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Hafiza ozetleyicisini sohbetler icin tetikler.
 *
 * Normalde sohbet akisi her [com.araswqm.hachi.core.Tuning.SUMMARY_EVERY_MESSAGES]
 * mesajda ozetlemeyi kendisi tetikler; bu worker **uygulama arka plana
 * gecerken** (`force = true`) calisir ve birikmis ama esigi asmamis mesajlari
 * da kalici hale getirir. §8 sonrasi hafiza **sohbet bazli** oldugu icin tum
 * sohbetler dolasilir; bekleyen mesaji olmayan sohbet icin
 * [MemorySummarizer.summarize] zaten hizlica `false` doner.
 */
@HiltWorker
class SummarizeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val chatDao: ChatDao,
    private val summarizer: MemorySummarizer,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val force = inputData.getBoolean(KEY_FORCE, true)
        val chats = try {
            chatDao.getAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return Result.retry()
        }

        var failed = false
        chats.forEach { chat ->
            try {
                summarizer.summarize(chat.id, chat.characterId, force = force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Tek sohbetin ozetlenememesi digerlerini engellemesin.
                failed = true
            }
        }
        // Yeniden deneme idempotenttir: bekleyen mesaji olmayan sohbet icin
        // ozetleyici no-op doner. Bu yuzden yalnizca gercek bir istisna
        // gorulduyse retry; aksi halde basari yeterlidir.
        return if (failed) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_FORCE = "force"
    }
}
