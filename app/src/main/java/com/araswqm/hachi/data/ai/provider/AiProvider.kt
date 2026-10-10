package com.araswqm.hachi.data.ai.provider

import com.araswqm.hachi.core.Tuning
import com.araswqm.hachi.data.ai.AiMessage
import com.araswqm.hachi.data.ai.AiResult
import com.araswqm.hachi.data.ai.AiStreamEvent
import kotlinx.coroutines.flow.Flow

/**
 * Bir cagrinin amaci. Model secimi saglayici-bazlidir: sohbet, ozet ve karar
 * icin ayri modeller tanimlanabilir ([ProviderId.defaultModel]).
 */
enum class ModelRole {
    CHAT,
    SUMMARY,
    DECISION,
}

/** Bir saglayicinin cagri icin ihtiyac duydugu her sey; cozulmus haldedir. */
data class AiRequest(
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val messages: List<AiMessage>,
    val temperature: Float,
    val maxTokens: Int,
    val jsonMode: Boolean = false,
)

/**
 * LLM saglayici soyutlamasi (§6).
 *
 * Tel formatlari farkli oldugu icin her saglayici kendi istegini kurar; ust
 * katman (LlmRouter, ChatRepository, MemorySummarizer) yalnizca bu arayuzu bilir.
 * Mesaj listesi (`List<AiMessage>`) saglayicidan bagimsizdir ve sabit sirada
 * uretilir — zincir hangi saglayiciya gecerse gecsin ayni baglam gonderilir.
 */
interface AiProvider {

    val id: ProviderId

    /** Bos yanitsiz (json_mode) cagri. */
    suspend fun complete(request: AiRequest): AiResult

    /** SSE akisi. `Done` olayi en sonda gelir. */
    fun stream(request: AiRequest): Flow<AiStreamEvent>
}

/** Rol -> saglayicinin varsayilan modeli. */
fun ProviderId.defaultModel(role: ModelRole): String = when (this) {
    ProviderId.DEEPSEEK -> Tuning.DEFAULT_MODEL_CHAT
    ProviderId.OPENAI -> when (role) {
        ModelRole.CHAT -> Tuning.DEFAULT_MODEL_OPENAI_CHAT
        ModelRole.SUMMARY, ModelRole.DECISION -> Tuning.DEFAULT_MODEL_OPENAI_SMALL
    }

    ProviderId.ANTHROPIC -> when (role) {
        ModelRole.CHAT -> Tuning.DEFAULT_MODEL_ANTHROPIC_CHAT
        ModelRole.SUMMARY, ModelRole.DECISION -> Tuning.DEFAULT_MODEL_ANTHROPIC_SMALL
    }

    ProviderId.GEMINI -> Tuning.DEFAULT_MODEL_GEMINI
    ProviderId.MISTRAL -> when (role) {
        ModelRole.CHAT -> Tuning.DEFAULT_MODEL_MISTRAL_CHAT
        ModelRole.SUMMARY, ModelRole.DECISION -> Tuning.DEFAULT_MODEL_MISTRAL_SMALL
    }
}
