package com.araswqm.hatir.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.araswqm.hatir.data.db.dao.CharacterDao
import com.araswqm.hatir.data.db.dao.FollowupDao
import com.araswqm.hatir.data.db.dao.MemoryStateDao
import com.araswqm.hatir.data.db.dao.MessageDao
import com.araswqm.hatir.data.db.dao.PersonaDao
import com.araswqm.hatir.data.db.dao.ProactiveLogDao
import com.araswqm.hatir.data.db.entity.CharacterEntity
import com.araswqm.hatir.data.db.entity.FollowupEntity
import com.araswqm.hatir.data.db.entity.MemoryStateEntity
import com.araswqm.hatir.data.db.entity.MessageEntity
import com.araswqm.hatir.data.db.entity.PersonaEntity
import com.araswqm.hatir.data.db.entity.ProactiveLogEntity

/**
 * Uygulamanin tek veritabani.
 *
 * Karakter silindiginde mesaj/takip/gunluk/hafiza-durumu satirlarini elle
 * silmek gerekmez: hepsinde `onDelete = CASCADE` tanimli ve Room acilista
 * `PRAGMA foreign_keys = ON` calistirir (ek guvence icin DI'daki callback'te
 * de acikca ayarlanir).
 *
 * `exportSchema = true` + KSP `room.schemaLocation` sayesinde her surum
 * `app/schemas/` altina JSON sema yazar (migration testleri icin).
 */
@Database(
    entities = [
        CharacterEntity::class,
        MessageEntity::class,
        MemoryStateEntity::class,
        PersonaEntity::class,
        FollowupEntity::class,
        ProactiveLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class HatirDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryStateDao(): MemoryStateDao
    abstract fun personaDao(): PersonaDao
    abstract fun followupDao(): FollowupDao
    abstract fun proactiveLogDao(): ProactiveLogDao

    companion object {
        const val NAME = "hatir.db"
    }
}
