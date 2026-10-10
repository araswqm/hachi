package com.araswqm.hachi.data.ai.provider

import androidx.annotation.StringRes
import com.araswqm.hachi.R
import com.araswqm.hachi.core.Tuning

/**
 * Desteklenen LLM saglayicilari (§6).
 *
 * [displayNameRes] yerellestirilmis addir; ayarlar ekrani ve "X yanit vermedi,
 * Y'ye gecildi" bildirimi bunu kullanir.
 *
 * Varsayilan taban adresler ve modeller [Tuning] icinde tutulur (siyahi sayi
 * kurali). Kullanici bunlari Ayarlar'dan degistirebilir.
 */
enum class ProviderId(
    @StringRes val displayNameRes: Int,
    val defaultBaseUrl: String,
) {
    DEEPSEEK(R.string.provider_deepseek, Tuning.DEFAULT_BASE_URL),
    OPENAI(R.string.provider_openai, Tuning.DEFAULT_BASE_URL_OPENAI),
    ANTHROPIC(R.string.provider_anthropic, Tuning.DEFAULT_BASE_URL_ANTHROPIC),
    GEMINI(R.string.provider_gemini, Tuning.DEFAULT_BASE_URL_GEMINI),
    MISTRAL(R.string.provider_mistral, Tuning.DEFAULT_BASE_URL_MISTRAL),
    ;

    companion object {
        /** Zincirin baslangic sirasi; kullanici degistirebilir. */
        val DEFAULT_ORDER: List<ProviderId> = entries.toList()

        /** Bozuk/eski ayar sessizce varsayilana duser. */
        fun fromNameOrNull(name: String): ProviderId? =
            entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
    }
}
