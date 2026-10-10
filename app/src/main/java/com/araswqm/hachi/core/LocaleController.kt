package com.araswqm.hachi.core

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

/**
 * Uygulama dili denetleyicisi (§5).
 *
 * minSdk 33 oldugu icin AppCompat'e gerek yok: framework
 * `LocaleManager.setApplicationLocales` kullanilir. Bos `LocaleList` = "sistem
 * dilini izle". Dil degistiginde Android Activity'yi yeniden kurar; bu istenen
 * davranistir (manifest'te `configChanges`'e `locale` EKLENMEZ).
 */
object LocaleController {

    /** Verilen dil etiketini uygular; null/bos ise sistem diline doner. */
    fun apply(context: Context, tag: String?) {
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        val locales = if (tag.isNullOrBlank()) {
            LocaleList.getEmptyLocaleList()
        } else {
            LocaleList.forLanguageTags(tag)
        }
        if (manager.applicationLocales != locales) {
            manager.applicationLocales = locales
        }
    }
}
