package com.araswqm.hachi.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.araswqm.hachi.data.db.dao.CharacterDao
import com.araswqm.hachi.data.db.dao.FollowupDao
import com.araswqm.hachi.data.db.dao.MemoryStateDao
import com.araswqm.hachi.data.db.dao.MessageDao
import com.araswqm.hachi.data.db.dao.PersonaDao
import com.araswqm.hachi.data.db.dao.ProactiveLogDao
import com.araswqm.hachi.data.db.entity.CharacterEntity
import com.araswqm.hachi.data.db.entity.FollowupEntity
import com.araswqm.hachi.data.db.entity.MemoryStateEntity
import com.araswqm.hachi.data.db.entity.MessageEntity
import com.araswqm.hachi.data.db.entity.PersonaEntity
import com.araswqm.hachi.data.db.entity.ProactiveLogEntity

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
abstract class HachiDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryStateDao(): MemoryStateDao
    abstract fun personaDao(): PersonaDao
    abstract fun followupDao(): FollowupDao
    abstract fun proactiveLogDao(): ProactiveLogDao

    companion object {
        const val NAME = "hachi.db"
    }
}
