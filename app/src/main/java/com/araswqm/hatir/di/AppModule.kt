package com.araswqm.hatir.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.araswqm.hatir.core.Tuning
import com.araswqm.hatir.data.db.HatirDatabase
import com.araswqm.hatir.data.db.dao.CharacterDao
import com.araswqm.hatir.data.db.dao.FollowupDao
import com.araswqm.hatir.data.db.dao.MemoryStateDao
import com.araswqm.hatir.data.db.dao.MessageDao
import com.araswqm.hatir.data.db.dao.PersonaDao
import com.araswqm.hatir.data.db.dao.ProactiveLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Kart JSON'lari ve veritabani icindeki JSON kolonlari icin ortak ayar.
     * `ignoreUnknownKeys`, V2/V3 kartlarinda tanimadigimiz alanlari sessizce atlar.
     * `explicitNulls = false` ile disa aktarilan kartlar gereksiz null alanlarla dolmaz.
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Tuning.HTTP_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(Tuning.HTTP_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(Tuning.HTTP_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Tek veritabani ornegi. `foreign_keys = ON` Room tarafindan da ayarlanir;
     * CASCADE silmenin kritik oldugu bu uygulamada ek guvence olarak burada da
     * acikca belirtiyoruz (yonerge idempotent).
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HatirDatabase =
        Room.databaseBuilder(context, HatirDatabase::class.java, HatirDatabase.NAME)
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                },
            )
            .build()

    @Provides
    fun provideCharacterDao(db: HatirDatabase): CharacterDao = db.characterDao()

    @Provides
    fun provideMessageDao(db: HatirDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideMemoryStateDao(db: HatirDatabase): MemoryStateDao = db.memoryStateDao()

    @Provides
    fun providePersonaDao(db: HatirDatabase): PersonaDao = db.personaDao()

    @Provides
    fun provideFollowupDao(db: HatirDatabase): FollowupDao = db.followupDao()

    @Provides
    fun provideProactiveLogDao(db: HatirDatabase): ProactiveLogDao = db.proactiveLogDao()
}
