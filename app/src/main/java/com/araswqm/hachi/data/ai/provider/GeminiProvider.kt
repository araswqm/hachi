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
 * Google Gemini API (`POST {baseUrl}/v1beta/models/{model}:generateContent`).
 *
 * Uc nokta yolu modeli icerir. Kimlik `?key=` sorgu parametresiyle gonderilir.
 * `system` mesajlari `systemInstruction` altinda toplanir; konusma turlari
 * `contents[].parts[].text` bicimindedir ve rol adlari `user` / `model`'dir.
 * Akis icin `:streamGenerateContent?alt=sse` kullanilir.
 */
class GeminiProvider(
    private val httpClient: OkHttpClient,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AiProvider {

    override val id: ProviderId = ProviderId.GEMINI

    override suspend fun complete(request: AiRequest): AiResult = withContext(io) {
        val response = try {
            httpClient.newCall(buildRequest(request, stream = false)).execute()
        } catch (e: IOException) {
            throw AiNetworkException("Bağlantı kurulamadı: ${e.message}", e)
        }
        response.use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw httpError(resp.code, raw)
            val root = parseObject(raw)
            val text = extractText(root)
            if (text.isBlank()) throw AiEmptyResponseException()
            AiResult(text, parseUsage(root["usageMetadata"]))
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
        thread(name = "sse-gemini") {
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

    private fun ProducerScope<AiStreamEvent>.readStream(response: Response) {
        val source = response.body?.source() ?: run {
            close(AiParseException("Yanıt gövdesi boş."))
            return
        }
        var sawAny = false
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.isEmpty() || !line.startsWith(DATA_PREFIX)) continue
            val obj = runCatching { json.parseToJsonElement(line.removePrefix(DATA_PREFIX).trim()) }
                .getOrNull() as? JsonObject ?: continue
            sawAny = true
            val text = extractText(obj)
            if (text.isNotEmpty()) trySend(AiStreamEvent.Delta(text))
            parseUsage(obj["usageMetadata"])?.let { trySend(AiStreamEvent.Usage(it)) }
        }
        if (sawAny) trySend(AiStreamEvent.Done)
    }

    private fun buildRequest(request: AiRequest, stream: Boolean): Request {
        val verb = if (stream) "streamGenerateContent" else "generateContent"
        val url = StringBuilder(request.baseUrl.trimEnd('/'))
            .append("/v1beta/models/")
            .append(request.model)
            .append(':')
            .append(verb)
            .append("?key=")
            .append(request.apiKey)
        if (stream) url.append("&alt=sse")
        return Request.Builder()
            .url(url.toString())
            .header("Accept", if (stream) ACCEPT_SSE else ACCEPT_JSON)
            .post(buildBody(request).toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun buildBody(request: AiRequest): String {
        val systems = request.messages.filter { it.role == AiMessage.ROLE_SYSTEM }
            .joinToString("\n\n") { it.content }
        val contents = request.messages.filter { it.role != AiMessage.ROLE_SYSTEM }

        val payload = buildJsonObject {
            if (systems.isNotBlank()) {
                put(
                    "systemInstruction",
                    buildJsonObject {
                        put(
                            "parts",
                            buildJsonArray { add(buildJsonObject { put("text", JsonPrimitive(systems)) }) },
                        )
                    },
                )
            }
            put(
                "contents",
                buildJsonArray {
                    contents.forEach { m ->
                        add(
                            buildJsonObject {
                                put(
                                    "role",
                                    JsonPrimitive(if (m.role == AiMessage.ROLE_ASSISTANT) "model" else "user"),
                                )
                                put(
                                    "parts",
                                    buildJsonArray { add(buildJsonObject { put("text", JsonPrimitive(m.content)) }) },
                                )
                            },
                        )
                    }
                },
            )
            put(
                "generationConfig",
                buildJsonObject {
                    put("temperature", JsonPrimitive(request.temperature))
                    put("maxOutputTokens", JsonPrimitive(request.maxTokens))
                    if (request.jsonMode) put("responseMimeType", JsonPrimitive("application/json"))
                },
            )
        }
        return payload.toString()
    }

    private fun extractText(root: JsonObject): String {
        val candidate = (root["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return ""
        val content = candidate["content"] as? JsonObject ?: return ""
        val parts = content["parts"] as? JsonArray ?: return ""
        return parts.mapNotNull { (it as? JsonObject)?.get("text") as? JsonPrimitive }
            .filter { it.isString }
            .joinToString("") { it.content }
    }

    private fun parseObject(raw: String): JsonObject =
        runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
            ?: throw AiParseException("Yanıt JSON değil: ${raw.take(Tuning.AI_ERROR_RAW_MAX_CHARS)}")

    private fun httpError(code: Int, raw: String): AiHttpException {
        val detail = runCatching {
            val root = json.parseToJsonElement(raw) as? JsonObject
            (root?.get("error") as? JsonObject)?.let { err ->
                (err["message"] as? JsonPrimitive)?.content
            }
        }.getOrNull()
        return AiHttpException(code, detail ?: raw.ifBlank { "bilinmeyen hata" })
    }

    private fun parseUsage(element: kotlinx.serialization.json.JsonElement?): AiUsage? {
        val obj = element as? JsonObject ?: return null
        fun int(key: String): Int = (obj[key] as? JsonPrimitive)?.intOrNull ?: 0
        return AiUsage(
            promptTokens = int("promptTokenCount"),
            completionTokens = int("candidatesTokenCount"),
        )
    }

    private companion object {
        const val DATA_PREFIX = "data:"
        const val ACCEPT_SSE = "text/event-stream"
        const val ACCEPT_JSON = "application/json"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
