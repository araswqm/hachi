package com.araswqm.hachi.data.ai.provider

import com.araswqm.hachi.core.Tuning
import com.araswqm.hachi.data.ai.AiEmptyResponseException
import com.araswqm.hachi.data.ai.AiHttpException
import com.araswqm.hachi.data.ai.AiMessage
import com.araswqm.hachi.data.ai.AiNetworkException
import com.araswqm.hachi.data.ai.AiParseException
import com.araswqm.hachi.data.ai.AiResult
import com.araswqm.hachi.data.ai.AiStreamEvent
import com.araswqm.hachi.data.ai.MissingApiKeyException
import com.araswqm.hachi.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/**
 * Coklu saglayici yonlendiricisi (§6).
 *
 * Aktif VE anahtari olan saglayicilar kullanicinin belirledigi sirayla denenir.
 * Zincirde hicbir saglayici yoksa [MissingApiKeyException] atilir.
 *
 * **Otomatik gecis (fallback) yalnizca "bos elde" tetiklenir**: ilk metin parcasi
 * gelmeden once olusan hiz siniri (429), sunucu hatasi (5xx), ag/timeout hatasi,
 * bos veya cozulemeyen yanit. Token geldikten sonra hata olursa zincir
 * denenmez — kismi metin zaten uretilmistir ve cift/karismis yanit riski
 * alinmaz (cagirici hatayi mevcut `Failed` davranisiyla isler).
 *
 * Baglam surekliligi: hangi saglayiciya gecilirse gecilsin cagiricinin urettigi
 * AYNI `List<AiMessage>` (system + karakter karti + hafiza + son mesajlar, sabit
 * sirada) gonderilir; degisen yalnizca tel formatidir.
 */
@Singleton
class LlmRouter @Inject constructor(
    private val providers: Map<ProviderId, AiProvider>,
    private val settings: SettingsRepository,
) {

    /** Akissiz tamamlama; zincir sirasiyla denenir. */
    suspend fun complete(
        role: ModelRole,
        messages: List<AiMessage>,
        temperature: Float,
        maxTokens: Int,
        jsonMode: Boolean = false,
    ): AiResult {
        val chain = settings.providerChain()
        if (chain.isEmpty()) throw MissingApiKeyException()

        var lastError: Throwable? = null
        for (config in chain) {
            val provider = providers[config.id] ?: continue
            val request = AiRequest(
                apiKey = config.apiKey,
                baseUrl = config.baseUrl,
                model = config.modelFor(role),
                messages = messages,
                temperature = temperature,
                maxTokens = maxTokens,
                jsonMode = jsonMode,
            )
            try {
                return provider.complete(request)
            } catch (e: Throwable) {
                if (!isFallbackWorthy(e)) throw e
                lastError = e
            }
        }
        throw lastError ?: MissingApiKeyException()
    }

    /**
     * SSE akisi. [onProviderSwitch] ilk parcadan once bir sonraki saglayiciya
     * gecilirse `(from, to)` ile bir kez cagrilir (sohbet ekrani bunu snackbar'da
     * gosterir). Ilk token geldikten sonra hata zincire tasinmaz.
     */
    fun stream(
        role: ModelRole,
        messages: List<AiMessage>,
        temperature: Float,
        maxTokens: Int,
        onProviderSwitch: ((ProviderId, ProviderId) -> Unit)? = null,
    ): Flow<AiStreamEvent> = callbackFlow {
        val chain = settings.providerChain()
        if (chain.isEmpty()) {
            close(MissingApiKeyException())
            return@callbackFlow
        }

        var lastError: Throwable? = null
        var previous: ProviderId? = null

        for (config in chain) {
            val provider = providers[config.id] ?: continue
            // Onceki saglayici bos elle biraktiysa gecisi simdi bildir.
            if (previous != null) onProviderSwitch?.invoke(previous, config.id)
            previous = config.id

            val request = AiRequest(
                apiKey = config.apiKey,
                baseUrl = config.baseUrl,
                model = config.modelFor(role),
                messages = messages,
                temperature = temperature,
                maxTokens = maxTokens,
            )

            var sawDelta = false
            try {
                provider.stream(request).collect { event ->
                    when (event) {
                        is AiStreamEvent.Delta -> {
                            sawDelta = true
                            trySend(event)
                        }

                        is AiStreamEvent.Usage -> trySend(event)
                        is AiStreamEvent.Done -> {
                            trySend(event)
                            close()
                            return@callbackFlow
                        }
                    }
                }
                // Akis hatasiz bitti ama `Done` gelmediyse yine de kapat.
                close()
                return@callbackFlow
            } catch (e: Throwable) {
                // Ilk token geldiyse veya hata gecise uygun degilse zincir durur.
                if (sawDelta || !isFallbackWorthy(e)) {
                    close(e)
                    return@callbackFlow
                }
                lastError = e
            }
        }
        close(lastError ?: MissingApiKeyException())
    }.buffer(Channel.UNLIMITED)

    /**
     * Bos elde sayilan hatalar. 4xx (400/401/403) kullanici hatasidir; zincir
     * denenmeden durur. [Tuning] esikleri tek yerdedir.
     */
    private fun isFallbackWorthy(e: Throwable): Boolean = when (e) {
        is AiNetworkException,
        is AiEmptyResponseException,
        is AiParseException,
        -> true

        is AiHttpException ->
            e.code == Tuning.AI_FALLBACK_HTTP_TOO_MANY_REQUESTS ||
                e.code >= Tuning.AI_FALLBACK_HTTP_SERVER_ERROR_MIN

        else -> false
    }
}
