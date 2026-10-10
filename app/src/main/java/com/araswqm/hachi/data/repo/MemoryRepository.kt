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
 * Karakterin hafiza dosyasi: `files/memory/<characterId>.md`.
 *
 * Dosyanin ICERIGI (bolumler, iliski durumu blogu) bu asamada uretilmiyor;
 * Stage 1'de yalnizca okuma/yazma/dosya erisimi var. Ozetleyici Stage 3'te
 * bu sinifin uzerine kurulur.
 */
@Singleton
class MemoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun file(characterId: String): File = File(context.filesDir, AppFiles.memoryRelPath(characterId))

    suspend fun exists(characterId: String): Boolean = withContext(io) { file(characterId).isFile }

    /** Dosya yoksa bos metin doner. */
    suspend fun read(characterId: String): String = withContext(io) {
        val target = file(characterId)
        if (!target.isFile) return@withContext ""
        runCatching { target.readText() }.getOrDefault("")
    }

    suspend fun write(characterId: String, content: String) = withContext(io) {
        val target = file(characterId)
        target.parentFile?.mkdirs()
        target.writeText(content)
    }

    /** Kullanici duzenlemesini kaydeder; tamamen bos metin dosyayi siler. */
    suspend fun save(characterId: String, content: String) = withContext(io) {
        val target = file(characterId)
        if (content.isBlank()) {
            target.delete()
        } else {
            target.parentFile?.mkdirs()
            target.writeText(content)
        }
    }

    suspend fun delete(characterId: String) = withContext(io) {
        file(characterId).delete()
        Unit
    }
}
