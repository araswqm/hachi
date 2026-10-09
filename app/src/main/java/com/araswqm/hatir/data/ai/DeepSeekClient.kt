package com.araswqm.hatir.data.ai

import com.araswqm.hatir.core.IoDispatcher
import com.araswqm.hatir.core.Tuning
import com.araswqm.hatir.data.settings.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * DeepSeek `POST {baseUrl}/chat/completions` cagrilari.
 *
 * Iki kullanim bicimi var:
 *  - [complete]: tek seferlik, akissiz yanit. Ozetleyici, karar ve JSON modu
 *    cagrilari bunu kullanir.
 *  - [stream]: SSE ile parca parca metin. Sohbet ekrani bunu kullanir.
 *
 * Istek govdesinde **dusunme modu her zaman kapalidir**
 * (`thinking.type = "disabled"`); kapaliyken `top_p` sabit 1.0 oldugu icin
 * gonderilmez. Ornekleme yalnizca karakter basina `temperature` + `max_tokens`
 * ile yapilir.
 *
 * Model, anahtar ve taban adres [SettingsRepository]'den okunur; hicbiri
 * kaynak kodda ya da build'de sabit degildir.
 */
@Singleton
class DeepSeekClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val settings: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** Akissiz tamamlama. Yanit metni ve varsa token kullanimi doner. */
    suspend fun complete(
        model: String,
        messages: List<AiMessage>,
        temperature: Float,
        maxTokens: Int,
        jsonMode: Boolean = false,
    ): AiResult = withContext(io) {
        val request = buildRequest(model, messages, temperature, maxTokens, stream = false, jsonMode = jsonMode)
        val response = try {
            httpClient.newCall(request).execute()
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

    /**
     * SSE akisi. Iptal edildiginde (ornegin "Durdur") HTTP cagrisi hemen
     * kesilir ve `call.cancel()` ile okuyucu is parcacigi sonlanir.
     *
     * `.buffer(Channel.UNLIMITED)` cagrisi `trySend`'in hicbir zaman
     * bloklamamasini/dusurmemesini garanti eder: uretici okuma is parcacigi,
     * tuketici ise UI'dir.
     */
    fun stream(
        model: String,
        messages: List<AiMessage>,
        temperature: Float,
        maxTokens: Int,
    ): Flow<AiStreamEvent> = callbackFlow {
        val request = try {
            buildRequest(model, messages, temperature, maxTokens, stream = true, jsonMode = false)
        } catch (e: Throwable) {
            close(e)
            return@callbackFlow
        }

        val call = httpClient.newCall(request)
        thread(name = "deepseek-sse") {
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

    /** `data: {...}` satirlarini okur; `[DONE]` ile biter. */
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

    private suspend fun buildRequest(
        model: String,
        messages: List<AiMessage>,
        temperature: Float,
        maxTokens: Int,
        stream: Boolean,
        jsonMode: Boolean,
    ): Request {
        val key = settings.currentApiKey()?.takeIf { it.isNotBlank() }
            ?: throw MissingApiKeyException()
        val baseUrl = settings.currentSettings().baseUrl.trimEnd('/')
        val body = buildBody(model, messages, temperature, maxTokens, stream, jsonMode)
        return Request.Builder()
            .url("$baseUrl$ENDPOINT")
            .header("Authorization", "Bearer $key")
            .header("Accept", if (stream) ACCEPT_SSE else ACCEPT_JSON)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun buildBody(
        model: String,
        messages: List<AiMessage>,
        temperature: Float,
        maxTokens: Int,
        stream: Boolean,
        jsonMode: Boolean,
    ): String {
        val payload = buildJsonObject {
            put("model", JsonPrimitive(model))
            put(
                "messages",
                buildJsonArray {
                    messages.forEach { m ->
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
            put("temperature", JsonPrimitive(temperature))
            put("max_tokens", JsonPrimitive(maxTokens))
            // Dusunme modu tum cagrilarda kapali. Kapaliyken top_p sabit 1.0'dir.
            put("thinking", buildJsonObject { put("type", JsonPrimitive(THINKING_DISABLED)) })
            if (stream) {
                put(
                    "stream_options",
                    buildJsonObject { put("include_usage", JsonPrimitive(true)) },
                )
            }
            if (jsonMode) {
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
