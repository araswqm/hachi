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
 * Anthropic Messages API (`POST {baseUrl}/v1/messages`).
 *
 * OpenAI'dan uc fark: `system` ayri bir ust-seviye alandir (mesaj dizisinde
 * degil), `max_tokens` **zorunludur**, kimlik `x-api-key` + `anthropic-version`
 * basligiyla gonderilir. `temperature` 0-1 araligina kirpilir.
 *
 * `response_format` desteklenmez; JSON cagrilarinda (ozetleyici) bicim zaten
 * system prompt'unda tarif edildigi icin ek bir alan gonderilmez.
 */
class AnthropicProvider(
    private val httpClient: OkHttpClient,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AiProvider {

    override val id: ProviderId = ProviderId.ANTHROPIC

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
            val text = (root["content"] as? JsonArray)
                ?.mapNotNull { block ->
                    val obj = block as? JsonObject ?: return@mapNotNull null
                    if ((obj["type"] as? JsonPrimitive)?.content != "text") return@mapNotNull null
                    (obj["text"] as? JsonPrimitive)?.stringContent()
                }
                ?.joinToString("")
                .orEmpty()
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
        thread(name = "sse-anthropic") {
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
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.isEmpty() || !line.startsWith(DATA_PREFIX)) continue
            val obj = runCatching { json.parseToJsonElement(line.removePrefix(DATA_PREFIX).trim()) }
                .getOrNull() as? JsonObject ?: continue
            when ((obj["type"] as? JsonPrimitive)?.content) {
                "content_block_delta" -> {
                    val delta = obj["delta"] as? JsonObject
                    val text = (delta?.get("text") as? JsonPrimitive)?.stringContent()
                    if (!text.isNullOrEmpty()) trySend(AiStreamEvent.Delta(text))
                }

                "message_delta" -> parseUsage(obj["usage"])?.let { trySend(AiStreamEvent.Usage(it)) }

                "message_stop" -> {
                    trySend(AiStreamEvent.Done)
                    break
                }
            }
        }
    }

    private fun buildRequest(request: AiRequest, stream: Boolean): Request {
        val body = buildBody(request, stream)
        return Request.Builder()
            .url("${request.baseUrl.trimEnd('/')}$ENDPOINT")
            .header("x-api-key", request.apiKey)
            .header("anthropic-version", Tuning.ANTHROPIC_API_VERSION)
            .header("Accept", if (stream) ACCEPT_SSE else ACCEPT_JSON)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun buildBody(request: AiRequest, stream: Boolean): String {
        val systems = request.messages.filter { it.role == AiMessage.ROLE_SYSTEM }
            .joinToString("\n\n") { it.content }
        val turns = request.messages.filter { it.role != AiMessage.ROLE_SYSTEM }
            .map { it.role to it.content }
            .mergeConsecutive()

        val payload = buildJsonObject {
            put("model", JsonPrimitive(request.model))
            // max_tokens Anthropic'te zorunlu; ust sinir tel formatinin geregi.
            put("max_tokens", JsonPrimitive(request.maxTokens.coerceAtMost(Tuning.ANTHROPIC_MAX_TOKENS_CEILING)))
            put("temperature", JsonPrimitive(request.temperature.coerceIn(0f, 1f)))
            if (systems.isNotBlank()) put("system", JsonPrimitive(systems))
            put(
                "messages",
                buildJsonArray {
                    turns.forEach { (role, content) ->
                        add(
                            buildJsonObject {
                                put("role", JsonPrimitive(role))
                                put("content", JsonPrimitive(content))
                            },
                        )
                    }
                },
            )
            put("stream", JsonPrimitive(stream))
        }
        return payload.toString()
    }

    /** Ardisik ayni roldeki mesajlari birlestirir (Anthropic sirali tur bekler). */
    private fun List<Pair<String, String>>.mergeConsecutive(): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        for (item in this) {
            val last = out.lastOrNull()
            if (last != null && last.first == item.first) {
                out[out.lastIndex] = last.first to (last.second + "\n\n" + item.second)
            } else {
                out.add(item)
            }
        }
        return out
    }

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

    private fun parseUsage(element: kotlinx.serialization.json.JsonElement?): AiUsage? {
        val obj = element as? JsonObject ?: return null
        fun int(key: String): Int = (obj[key] as? JsonPrimitive)?.intOrNull ?: 0
        return AiUsage(promptTokens = int("input_tokens"), completionTokens = int("output_tokens"))
    }

    private fun JsonPrimitive.stringContent(): String? = if (isString) content else null

    private companion object {
        const val ENDPOINT = "/v1/messages"
        const val DATA_PREFIX = "data:"
        const val ACCEPT_SSE = "text/event-stream"
        const val ACCEPT_JSON = "application/json"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
