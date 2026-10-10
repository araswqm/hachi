package com.araswqm.hachi.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.araswqm.hachi.data.db.entity.PersonaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonaDao {

    @Query("SELECT * FROM personas ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas WHERE isSelected = 1 LIMIT 1")
    fun observeSelected(): Flow<PersonaEntity?>

    @Query("SELECT * FROM personas WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelected(): PersonaEntity?

    @Query("SELECT * FROM personas WHERE id = :id")
    suspend fun getById(id: String): PersonaEntity?

    /** Listenin ilk personası (en eskisi); silme sonrasi varsayilan secimi icin. */
    @Query("SELECT * FROM personas ORDER BY createdAt ASC LIMIT 1")
    suspend fun getFirst(): PersonaEntity?

    @Query("SELECT * FROM personas WHERE id = :id")
    fun observeById(id: String): Flow<PersonaEntity?>

    @Upsert
    suspend fun upsert(persona: PersonaEntity)

    @Query("DELETE FROM personas WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE personas SET isSelected = 0")
    suspend fun clearSelection()

    @Query("UPDATE personas SET isSelected = 1 WHERE id = :id")
    suspend fun select(id: String)
}
