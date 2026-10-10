package com.araswqm.hachi.data.repo

import android.content.Context
import com.araswqm.hachi.core.AppFiles
import com.araswqm.hachi.core.IoDispatcher
import com.araswqm.hachi.data.db.dao.CharacterDao
import com.araswqm.hachi.data.db.dao.PersonaDao
import com.araswqm.hachi.data.db.entity.CharacterEntity
import com.araswqm.hachi.data.db.entity.PersonaEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Kullanicinin kendi karakteri (persona).
 *
 * Birden fazla persona tutulabilir; biri "varsayilan" (secili) olur. Her sohbet
 * istege bagli olarak kendi personasini secebilir (§3): [resolve] once
 * `character.personaId`'ye bakar, yoksa varsayilana duser.
 */
@Singleton
class PersonaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val personaDao: PersonaDao,
    private val characterDao: CharacterDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun observeSelected(): Flow<PersonaEntity?> = personaDao.observeSelected()

    fun observeAll(): Flow<List<PersonaEntity>> = personaDao.observeAll()

    fun observeById(id: String): Flow<PersonaEntity?> = personaDao.observeById(id)

    /** Secili persona; hic yoksa `null`. Satir olusturmaz ([getOrCreateSelected]'ten farki). */
    suspend fun getSelected(): PersonaEntity? = withContext(io) { personaDao.getSelected() }

    suspend fun getById(id: String): PersonaEntity? = withContext(io) { personaDao.getById(id) }

    /** Secili persona yoksa olusturur ve doner. */
    suspend fun getOrCreateSelected(): PersonaEntity = withContext(io) {
        personaDao.getSelected() ?: PersonaEntity(
            id = AppFiles.newId(),
            isSelected = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        ).also { personaDao.upsert(it) }
    }

    /**
     * Yeni bos persona olusturur ve doner. Varsayilan secim degismez — tablo
     * tamamen bossa yeni persona dogrudan varsayilan olur (§3).
     */
    suspend fun create(): PersonaEntity = withContext(io) {
        val now = System.currentTimeMillis()
        PersonaEntity(
            id = AppFiles.newId(),
            isSelected = personaDao.getSelected() == null,
            createdAt = now,
            updatedAt = now,
        ).also { personaDao.upsert(it) }
    }

    /**
     * Bir sohbette kullanilacak persona (§3): once `character.personaId`, yoksa
     * varsayilan (secili) persona. Ikisi de yoksa `null`.
     */
    suspend fun resolve(character: CharacterEntity?): PersonaEntity? = withContext(io) {
        character?.personaId?.let { personaDao.getById(it) } ?: personaDao.getSelected()
    }

    suspend fun save(persona: PersonaEntity) = withContext(io) {
        val now = System.currentTimeMillis()
        personaDao.upsert(persona.copy(updatedAt = now))
    }

    /** Varsayilan personayi degistirir: ayni anda tek secili kalir. */
    suspend fun setSelected(id: String) = withContext(io) {
        personaDao.clearSelection()
        personaDao.select(id)
    }

    /** Ayarlar ekranindan ad/hakkinda guncellemesi (varsayilan persona). */
    suspend fun updateProfile(name: String, about: String) = withContext(io) {
        val current = personaDao.getSelected() ?: PersonaEntity(
            id = AppFiles.newId(),
            isSelected = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        personaDao.upsert(
            current.copy(
                name = name.trim(),
                about = about.trim(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Personayi siler: ona bagli sohbetler varsayilana dondurulur, avatar
     * dosyasi silinir, seciliydiyse kalan ilk persona secilir.
     */
    suspend fun delete(id: String) = withContext(io) {
        characterDao.clearPersona(id)
        personaDao.deleteById(id)
        avatarFile(id).delete()
        if (personaDao.getSelected() == null) {
            personaDao.getFirst()?.let { personaDao.select(it.id) }
        }
    }

    // ------------------------------------------------------------------
    // Avatar
    // ------------------------------------------------------------------

    fun avatarFile(personaId: String): File = File(context.filesDir, AppFiles.personaAvatarRelPath(personaId))

    fun avatarFileOrNull(personaId: String): File? = avatarFile(personaId).takeIf { it.isFile }

    /** Persona resmini yazar; editorde henuz kaydedilmemis resim de buraya gelir. */
    suspend fun setAvatar(personaId: String, png: ByteArray) = withContext(io) {
        val file = avatarFile(personaId)
        file.parentFile?.mkdirs()
        file.writeBytes(png)
        val now = System.currentTimeMillis()
        personaDao.getById(personaId)?.let {
            personaDao.upsert(it.copy(avatarPath = AppFiles.personaAvatarRelPath(personaId), updatedAt = now))
        }
    }

    suspend fun clearAvatar(personaId: String) = withContext(io) {
        avatarFile(personaId).delete()
        personaDao.getById(personaId)?.let {
            personaDao.upsert(it.copy(avatarPath = null, updatedAt = System.currentTimeMillis()))
        }
    }
}
