package com.araswqm.hachi.data.repo

import android.content.Context
import com.araswqm.hachi.core.AppFiles
import com.araswqm.hachi.core.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Sohbet hafiza dosyasi: `files/memory/<chatId>.md`.
 *
 * §8: hafiza artik sohbet bazlidir; anahtar karakter degil sohbet kimligidir.
 * Dosyanin ICERIGI (bolumler, iliski durumu blogu) bu sinifta uretilmez; yalnizca
 * okuma/yazma/dosya erisimi vardir. Ozetleyici bu sinifin uzerine kurulur.
 */
@Singleton
class MemoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun file(chatId: String): File = File(context.filesDir, AppFiles.memoryRelPath(chatId))

    suspend fun exists(chatId: String): Boolean = withContext(io) { file(chatId).isFile }

    /** Dosya yoksa bos metin doner. */
    suspend fun read(chatId: String): String = withContext(io) {
        val target = file(chatId)
        if (!target.isFile) return@withContext ""
        runCatching { target.readText() }.getOrDefault("")
    }

    suspend fun write(chatId: String, content: String) = withContext(io) {
        val target = file(chatId)
        target.parentFile?.mkdirs()
        target.writeText(content)
    }

    /** Kullanici duzenlemesini kaydeder; tamamen bos metin dosyayi siler. */
    suspend fun save(chatId: String, content: String) = withContext(io) {
        val target = file(chatId)
        if (content.isBlank()) {
            target.delete()
        } else {
            target.parentFile?.mkdirs()
            target.writeText(content)
        }
    }

    suspend fun delete(chatId: String) = withContext(io) {
        file(chatId).delete()
        Unit
    }
}
