package com.araswqm.hachi.core.png

/**
 * PNG bozuk, kirpilmis veya beklenen yapida degil. Mesaj her zaman insan
 * okunurdur; dogrudan kullaniciya gosterilebilir.
 */
class PngFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
