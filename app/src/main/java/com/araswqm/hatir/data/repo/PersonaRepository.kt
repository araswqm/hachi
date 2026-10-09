package com.araswqm.hatir.data.repo

import com.araswqm.hatir.core.AppFiles
import com.araswqm.hatir.core.IoDispatcher
import com.araswqm.hatir.data.db.dao.PersonaDao
import com.araswqm.hatir.data.db.entity.PersonaEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Kullanicinin kendi karakteri (persona).
 *
 * Tek bir secili persona tutuluyor; ilk erisimde bos bir kayit olusturulur,
 * boylece ayarlar ekrani her zaman uzerine yazabilecegi bir satir bulur.
 */
@Singleton
class PersonaRepository @Inject constructor(
    private val personaDao: PersonaDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun observeSelected(): Flow<PersonaEntity?> = personaDao.observeSelected()

    fun observeAll(): Flow<List<PersonaEntity>> = personaDao.observeAll()

    /** Secili persona; hic yoksa `null`. Satir olusturmaz ([getOrCreateSelected]'ten farki). */
    suspend fun getSelected(): PersonaEntity? = withContext(io) { personaDao.getSelected() }

    /** Secili persona yoksa olusturur ve doner. */
    suspend fun getOrCreateSelected(): PersonaEntity = withContext(io) {
        personaDao.getSelected() ?: PersonaEntity(
            id = AppFiles.newId(),
            isSelected = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        ).also { personaDao.upsert(it) }
    }

    suspend fun save(persona: PersonaEntity) = withContext(io) {
        val now = System.currentTimeMillis()
        personaDao.upsert(persona.copy(updatedAt = now))
    }

    /** Ayarlar ekranindan ad/hakkinda guncellemesi. */
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
}
