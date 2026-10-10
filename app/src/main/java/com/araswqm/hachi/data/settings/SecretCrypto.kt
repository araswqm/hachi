package com.araswqm.hachi.data.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API anahtari gibi sirlari Android Keystore'daki AES-GCM anahtariyla sifreler.
 *
 * Neden `EncryptedSharedPreferences` degil: `androidx.security:security-crypto`
 * kullanimdan kaldirildi; onun yerine dogrudan Keystore kullaniyoruz.
 *
 * Sifreli cikti bicimi: `base64( ivUzunlugu(1 bayt) || iv || ciphertext )`.
 * Anahtar cihazda kalir, hicbir zaman disari cikmaz ve **yedeklenmez**;
 * bu yuzden yedekten geri donen bir kurulumda [decrypt] null doner ve
 * kullanici anahtari yeniden girer.
 */
@Singleton
class SecretCrypto @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    private fun secretKey(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    /** Bos girdi bos cikti verir (sir yoksa sifreleme de yok). */
    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))

        val packed = ByteArray(1 + iv.size + body.size)
        packed[0] = iv.size.toByte()
        System.arraycopy(iv, 0, packed, 1, iv.size)
        System.arraycopy(body, 0, packed, 1 + iv.size, body.size)
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    /** Cozulemezse (anahtar kaybolmus / veri bozuk) null doner. */
    fun decrypt(encoded: String): String? {
        if (encoded.isEmpty()) return ""
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            val ivSize = packed[0].toInt()
            val iv = packed.copyOfRange(1, 1 + ivSize)
            val body = packed.copyOfRange(1 + ivSize, packed.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrNull()
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "hachi_secret_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
