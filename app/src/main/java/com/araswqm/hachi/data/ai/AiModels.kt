package com.araswqm.hachi.data.ai

import com.araswqm.hachi.core.Tuning

/**
 * Sohbet, ozetleme ve karar cagrilarinda kullanilan tek bir mesaj.
 * Rol adlari DeepSeek (OpenAI uyumlu) API'siyle birebir aynidir.
 */
data class AiMessage(val role: String, val content: String) {
    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"

        fun system(content: String) = AiMessage(ROLE_SYSTEM, content)
        fun user(content: String) = AiMessage(ROLE_USER, content)
        fun assistant(content: String) = AiMessage(ROLE_ASSISTANT, content)
    }
}

/**
 * `usage` blogu. DeepSeek otomatik onbellek (context caching) kullandigi icin
 * isabet/ıska token'lari ayrica raporlanir; bunlar yalnizca hata ayiklama
 * ekraninda gosterilir.
 */
data class AiUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val cacheHitTokens: Int = 0,
    val cacheMissTokens: Int = 0,
)

/** Akis olmayan (tek seferlik) bir cagrinin sonucu. */
data class AiResult(val text: String, val usage: AiUsage?)

/** SSE akisinda uretilen olaylar. */
sealed interface AiStreamEvent {
    /** Yeni gelen metin parcasi (kumulatif DEGIL, yalnizca artim). */
    data class Delta(val text: String) : AiStreamEvent

    /** Akis sonundaki `usage` blogu (stream_options.include_usage ile gelir). */
    data class Usage(val usage: AiUsage) : AiStreamEvent

    /** Akis tamamlandi. */
    data object Done : AiStreamEvent
}

/** AI cagrilari icin ortak hata tabani. */
open class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** API anahtari ayarlanmamis; arayuz "Ayarlar'a git" onerir. */
class MissingApiKeyException : AiException("DeepSeek API anahtarı ayarlanmamış.")

/** Sunucu 2xx disi bir kod dondu. [detail] sunucunun hata mesajidir. */
class AiHttpException(val code: Int, val detail: String) :
    AiException("DeepSeek HTTP $code: ${detail.take(Tuning.AI_ERROR_DETAIL_MAX_CHARS)}")

/** Baglanti kurulamadi / okuma zaman asimina ugradi. */
class AiNetworkException(message: String, cause: Throwable?) : AiException(message, cause)

/** Yanit beklenen JSON bicimine uymuyor. */
class AiParseException(message: String, cause: Throwable? = null) : AiException(message, cause)

/** Istek basarili ama gorunur metin uretilmedi. */
class AiEmptyResponseException : AiException("Model boş yanıt döndü.")
