package com.araswqm.hatir.data.repo

import android.content.Context
import com.araswqm.hatir.core.AppFiles
import com.araswqm.hatir.core.IoDispatcher
import com.araswqm.hatir.core.png.CardPng
import com.araswqm.hatir.data.card.CardParser
import com.araswqm.hatir.data.card.CardWriter
import com.araswqm.hatir.data.card.CharacterCard
import com.araswqm.hatir.data.db.dao.CharacterDao
import com.araswqm.hatir.data.db.entity.CharacterEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Karakter ice aktarma sonucu. */
sealed interface ImportResult {
    data class Success(val character: CharacterEntity, val importedAvatar: Boolean) : ImportResult

    /** [reason] dogrudan kullaniciya gosterilebilir (Turkce). */
    data class Failure(val reason: String) : ImportResult
}

/**
 * Karakter CRUD'u + kart ice/disa aktarimi + avatarlarin dosya sistemi.
 *
 * Dosya yollari [AppFiles] uzerinden **goreli** tutulur; veritabaninda
 * `avatars/<id>.png` gibi durur, gercek konum [filesDir]'e gore cozulur.
 *
 * Karakter silindiginde Room `CASCADE` mesaj/followup/kayit satirlarini
 * temizler; geriye kalan dosyalar (avatar + `memory/<id>.md`) burada silinir.
 */
@Singleton
class CharacterRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val characterDao: CharacterDao,
    private val cardParser: CardParser,
    private val cardWriter: CardWriter,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val filesDir: File get() = context.filesDir
    private val cacheDir: File get() = context.cacheDir

    // ------------------------------------------------------------------
    // Okuma
    // ------------------------------------------------------------------

    fun observeAll(): Flow<List<CharacterEntity>> = characterDao.observeAll()

    fun observeById(id: String): Flow<CharacterEntity?> = characterDao.observeById(id)

    suspend fun getById(id: String): CharacterEntity? = withContext(io) { characterDao.getById(id) }

    suspend fun count(): Int = withContext(io) { characterDao.count() }

    // ------------------------------------------------------------------
    // Yazma
    // ------------------------------------------------------------------

    /** Yeni ya da duzenlenmis karakteri kaydeder. */
    suspend fun save(character: CharacterEntity) = withContext(io) {
        characterDao.upsert(character.copy(updatedAt = System.currentTimeMillis()))
    }

    /** Elle olusturulan bos karakter; duzenleyici ekrani bunu doldurur. */
    fun newCharacter(): CharacterEntity {
        val now = System.currentTimeMillis()
        return CharacterEntity(
            id = AppFiles.newId(),
            name = "",
            createdAt = now,
            updatedAt = now,
        )
    }

    /**
     * Avatar PNG'sini [AppFiles.avatarRelPath]'e yazar ve karakteri gunceller.
     * Bos/gecersiz bayt dizisi avatar'i **silmez**, yalnizca yok sayilir.
     */
    suspend fun setAvatar(characterId: String, png: ByteArray?) = withContext(io) {
        val target = avatarFile(characterId)
        if (png == null || png.isEmpty()) return@withContext
        target.parentFile?.mkdirs()
        target.writeBytes(png)
        characterDao.getById(characterId)?.let {
            characterDao.update(it.copy(avatarPath = AppFiles.avatarRelPath(characterId)))
        }
    }

    /** Avatar dosyasini siler ve alani temizler. */
    suspend fun clearAvatar(characterId: String) = withContext(io) {
        avatarFile(characterId).delete()
        characterDao.getById(characterId)?.let {
            characterDao.update(it.copy(avatarPath = null))
        }
    }

    fun avatarFile(characterId: String): File = File(filesDir, AppFiles.avatarRelPath(characterId))

    /** Avatar gercekten duruyorsa dosyayi, aksi halde null. */
    fun avatarFileOrNull(characterId: String): File? = avatarFile(characterId).takeIf { it.isFile }

    // ------------------------------------------------------------------
    // Ice aktarma
    // ------------------------------------------------------------------

    /** JSON kart metninden karakter olusturur. Avatar disaridan verilebilir. */
    suspend fun importCardJson(jsonText: String, avatarPng: ByteArray? = null): ImportResult {
        val card = cardParser.parse(jsonText)
            ?: return ImportResult.Failure("Kart okunamadi: gecerli JSON degil veya 'name' alani bos.")
        return createFromCard(card, avatarPng)
    }

    /**
     * PNG dosyasindan karakter olusturur. Avatar olarak **orijinal PNG**
     * kullanilir (yeniden sikistirilmaz).
     */
    suspend fun importCardPng(pngBytes: ByteArray, fallbackName: String? = null): ImportResult {
        val cardJson = CardPng.readCardJson(pngBytes)
            ?: return ImportResult.Failure(
                "PNG icinde karakter karti bulunamadi ('ccv3' veya 'chara' chunk'i yok).",
            )
        val card = cardParser.parse(cardJson)
            ?: return ImportResult.Failure("PNG icindeki kart okunamadi: JSON bozuk veya 'name' bos.")
        val named = if (card.name.isBlank() && !fallbackName.isNullOrBlank()) {
            card.copy(name = fallbackName)
        } else {
            card
        }
        return createFromCard(named, pngBytes)
    }

    /**
     * Kart metnini ayristirir **ama kaydetmez**. Duzenleyicinin "ice aktar"
     * dugmesi bunu kullanir: alanlar forma doldurulur, karakter ancak kullanici
     * Kaydet'e bastiginda olusur (boylece yarim/istege bagli satir olusmaz).
     */
    fun parseCardJson(jsonText: String): CharacterCard? = cardParser.parse(jsonText)

    /** PNG baytlarindan karti ayristirir **ama kaydetmez** (bkz. [parseCardJson]). */
    fun parseCardPng(pngBytes: ByteArray): CharacterCard? =
        CardPng.readCardJson(pngBytes)?.let { cardParser.parse(it) }

    private suspend fun createFromCard(card: CharacterCard, avatarPng: ByteArray?): ImportResult =
        withContext(io) {
            val id = AppFiles.newId()
            val now = System.currentTimeMillis()
            val avatarBytes = avatarPng?.takeIf { it.isNotEmpty() }

            val entity = if (avatarBytes != null) {
                val file = File(filesDir, AppFiles.avatarRelPath(id))
                file.parentFile?.mkdirs()
                file.writeBytes(avatarBytes)
                card.toEntity(id, AppFiles.avatarRelPath(id), now)
            } else {
                card.toEntity(id, avatarPath = null, now = now)
            }

            characterDao.insert(entity)
            ImportResult.Success(entity, avatarBytes != null)
        }

    // ------------------------------------------------------------------
    // Disa aktarma
    // ------------------------------------------------------------------

    /** Karakteri V2 JSON metni olarak dondurur; karakter yoksa null. */
    suspend fun exportCardJson(characterId: String): String? = withContext(io) {
        characterDao.getById(characterId)?.let { cardWriter.toV2Json(it) }
    }

    /**
     * Karakteri PNG kart olarak dondurur: avatar PNG'sinin sonuna `chara`
     * chunk'i eklenir. Avatar yoksa null (PNG kart uretilemez).
     */
    suspend fun exportCardPng(characterId: String): ByteArray? = withContext(io) {
        val character = characterDao.getById(characterId) ?: return@withContext null
        val avatar = avatarFileOrNull(characterId) ?: return@withContext null
        CardPng.writeCardPng(avatar.readBytes(), cardWriter.toV2Json(character))
    }

    /**
     * Paylasim/indirme icin kart dosyasini `cache/exports/` altina yazar.
     * PNG mumkunse PNG, degilse JSON. Yazilan dosyayi dondurur.
     */
    suspend fun writeExportFile(characterId: String): File? = withContext(io) {
        val character = characterDao.getById(characterId) ?: return@withContext null
        val exports = File(cacheDir, AppFiles.EXPORTS_DIR).apply { mkdirs() }
        val baseName = AppFiles.exportFileName(character.name.ifBlank { "karakter" }, characterId)

        val png = exportCardPng(characterId)
        if (png != null) {
            val target = File(exports, baseName)
            target.writeBytes(png)
            return@withContext target
        }

        val jsonText = cardWriter.toV2Json(character)
        val target = File(exports, baseName.removeSuffix(".png") + ".json")
        target.writeText(jsonText)
        target
    }

    // ------------------------------------------------------------------
    // Silme
    // ------------------------------------------------------------------

    /**
     * Karakteri ve ona bagli **tum** veriyi siler: DB satirlari (CASCADE ile
     * mesaj/followup/kayit) + avatar dosyasi + `memory/<id>.md`.
     */
    suspend fun deleteCharacter(characterId: String) = withContext(io) {
        characterDao.deleteById(characterId)
        avatarFile(characterId).delete()
        File(filesDir, AppFiles.memoryRelPath(characterId)).delete()
        Unit
    }
}
