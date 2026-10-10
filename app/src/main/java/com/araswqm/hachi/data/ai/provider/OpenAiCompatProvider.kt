package com.araswqm.hachi.data.ai.provider

import com.araswqm.hachi.core.IoDispatcher
import com.araswqm.hachi.core.Tuning
import com.araswqm.hachi.data.ai.AiEmptyResponseException
import com.araswqm.hachi.data.ai.AiHttpException
import com.araswqm.hachi.data.ai.AiMessage
import com.araswqm.hachi.data.ai.AiNetworkException
import com.araswqm.hachi.data.ai.AiParseException
import com.araswqm.hachi.data.ai.AiResult
import com.araswqm.hachi.data.ai.AiStreamEvent
import com.araswqm.hachi.data.ai.AiUsage
import java.io.IOException
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * OpenAI-uyumlu saglayicilar: DeepSeek, OpenAI ve Mistral ayni tel formatini
 * kullanir (`POST {baseUrl}/chat/completions`, `Authorization: Bearer`).
 *
 * Tek fark DeepSeek'e ozgu `thinking: {type: "disabled"}` alanidir; bu yuzden
 * alt sinif `thinkingDisabledField` degerini true yapar. Digerleri govdeye
 * eklemez.
 *
 * Istek govdesi [AiRequest]'ten kurulur; anahtar/taban adres cagirici
 * (LlmRouter) tarafindan cozulmustur, saglayici ayarlari bilmez.
 */
open class OpenAiCompatProvider(
    override val id: ProviderId,
    protected val httpClient: OkHttpClient,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
    /** Yalnizca DeepSeek true; dusunme modu alani govdeye eklenir. */
    private val thinkingDisabledField: Boolean = false,
) : AiProvider {

    override suspend fun complete(request: AiRequest): AiResult = withContext(io) {
        val httpRequest = buildRequest(request, stream = false)
        val response = try {
            httpClient.newCall(httpRequest).execute()
        } catch (e: IOException) {
            throw AiNetworkException("Bağlantı kurulamadı: ${e.message}", e)
        }
        response.use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw httpError(resp.code, raw)
            val root = parseObject(raw)
            val text = extractMessageText(root)
            if (text.isBlank()) throw AiEmptyResponseException()
            AiResult(text, parseUsage(root["usage"]))
        }
    }

    override fun stream(request: AiRequest): Flow<AiStreamEvent> = callbackFlow {
        val httpRequest = try {
            buildRequest(request, stream = true)
        } catch (e: Throwable) {
            close(e)
            return@callbackFlow
        }

        val call = httpClient.newCall(httpRequest)
        thread(name = "sse-${id.name.lowercase()}") {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        close(httpError(response.code, response.body?.string().orEmpty()))
                        return@use
                    }
                    readStream(response)
                }
                close()
            } catch (e: Throwable) {
                close(e)
            }
        }
        awaitClose { call.cancel() }
    }.buffer(Channel.UNLIMITED)

    // ------------------------------------------------------------------
    // SSE cozumleme
    // ------------------------------------------------------------------

    private fun ProducerScope<AiStreamEvent>.readStream(response: Response) {
        val source = response.body?.source() ?: run {
            close(AiParseException("Yanıt gövdesi boş."))
            return
        }
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.isEmpty() || !line.startsWith(DATA_PREFIX)) continue
            val payload = line.removePrefix(DATA_PREFIX).trim()
            if (payload == SSE_DONE) {
                trySend(AiStreamEvent.Done)
                break
            }
            val obj = runCatching { json.parseToJsonElement(payload) }.getOrNull() as? JsonObject
                ?: continue
            val delta = extractDeltaText(obj)
            if (delta.isNotEmpty()) trySend(AiStreamEvent.Delta(delta))
            parseUsage(obj["usage"])?.let { trySend(AiStreamEvent.Usage(it)) }
        }
    }

    // ------------------------------------------------------------------
    // Istek kurulumu
    // ------------------------------------------------------------------

    private fun buildRequest(request: AiRequest, stream: Boolean): Request {
        val body = buildBody(request, stream)
        return Request.Builder()
            .url("${request.baseUrl.trimEnd('/')}$ENDPOINT")
            .header("Authorization", "Bearer ${request.apiKey}")
            .header("Accept", if (stream) ACCEPT_SSE else ACCEPT_JSON)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun buildBody(request: AiRequest, stream: Boolean): String {
        val payload = buildJsonObject {
            put("model", JsonPrimitive(request.model))
            put(
                "messages",
                buildJsonArray {
                    request.messages.forEach { m: AiMessage ->
                        add(
                            buildJsonObject {
                                put("role", JsonPrimitive(m.role))
                                put("content", JsonPrimitive(m.content))
                            },
                        )
                    }
                },
            )
            put("stream", JsonPrimitive(stream))
            put("temperature", JsonPrimitive(request.temperature))
            put("max_tokens", JsonPrimitive(request.maxTokens))
            if (thinkingDisabledField) {
                // Dusunme modu tum cagrilarda kapali (yalniz DeepSeek).
                put("thinking", buildJsonObject { put("type", JsonPrimitive(THINKING_DISABLED)) })
            }
            if (stream) {
                put(
                    "stream_options",
                    buildJsonObject { put("include_usage", JsonPrimitive(true)) },
                )
            }
            if (request.jsonMode) {
                put("response_format", buildJsonObject { put("type", JsonPrimitive("json_object")) })
            }
        }
        return payload.toString()
    }

    // ------------------------------------------------------------------
    // Kucuk yardimcilar
    // ------------------------------------------------------------------

    private fun parseObject(raw: String): JsonObject =
        runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
            ?: throw AiParseException("Yanıt JSON değil: ${raw.take(Tuning.AI_ERROR_RAW_MAX_CHARS)}")

    private fun httpError(code: Int, raw: String): AiHttpException {
        val detail = runCatching {
            val root = json.parseToJsonElement(raw) as? JsonObject
            (root?.get("error") as? JsonObject)?.let { err ->
                (err["message"] as? JsonPrimitive)?.stringContent()
            }
        }.getOrNull()
        return AiHttpException(code, detail ?: raw.ifBlank { "bilinmeyen hata" })
    }

    private fun extractDeltaText(obj: JsonObject): String {
        val choice = (obj["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return ""
        val delta = (choice["delta"] as? JsonObject)?.get("content")
        val fromDelta = (delta as? JsonPrimitive)?.stringContent()
        if (!fromDelta.isNullOrEmpty()) return fromDelta
        val message = (choice["message"] as? JsonObject)?.get("content")
        return (message as? JsonPrimitive)?.stringContent().orEmpty()
    }

    private fun extractMessageText(obj: JsonObject): String {
        val choice = (obj["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return ""
        val message = choice["message"] as? JsonObject ?: return ""
        return (message["content"] as? JsonPrimitive)?.stringContent().orEmpty()
    }

    private fun parseUsage(element: JsonElement?): AiUsage? {
        val obj = element as? JsonObject ?: return null
        fun int(key: String): Int = (obj[key] as? JsonPrimitive)?.intOrNull ?: 0
        return AiUsage(
            promptTokens = int("prompt_tokens"),
            completionTokens = int("completion_tokens"),
            cacheHitTokens = int("prompt_cache_hit_tokens"),
            cacheMissTokens = int("prompt_cache_miss_tokens"),
        )
    }

    /** Yalnizca JSON string alanlari metin sayilir; sayi/bool yok sayilir. */
    private fun JsonPrimitive.stringContent(): String? = if (isString) content else null

    private companion object {
        const val ENDPOINT = "/chat/completions"
        const val DATA_PREFIX = "data:"
        const val SSE_DONE = "[DONE]"
        const val THINKING_DISABLED = "disabled"
        const val ACCEPT_SSE = "text/event-stream"
        const val ACCEPT_JSON = "application/json"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
