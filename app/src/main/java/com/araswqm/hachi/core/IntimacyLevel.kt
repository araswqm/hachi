package com.araswqm.hachi.core

/**
 * Yakinlik seviyesi — **dil bagimsiz**.
 *
 * Onceki surumde seviye dogrudan Turkce bir metindi ("Yakın" vb.); hem
 * karsilastirma hem de gosterim o metne bagliydi. §5 (coklu dil) ile birlikte
 * bu kirilganlik kaldirildi:
 *
 *  - **Karsilastirma** [ordinal] sirasi ile yapilir (bkz. `Tuning.PROACTIVE_MIN_LEVEL`).
 *  - **Kalici metin** ([label]) her zaman Ingilizcedir; hafiza ozeti ve karar
 *    istemi (prompt) bunu kullanir (§2.2: hafiza daima Ingilizce).
 *  - **Arayuz etiketi** `@StringRes` ile yerellestirilir (bkz. `levelRes`).
 */
enum class IntimacyLevel(val label: String) {
    STRANGER("Stranger"),
    ACQUAINTANCE("Acquaintance"),
    FRIEND("Friend"),
    CLOSE("Close"),
}
