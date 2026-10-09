package com.araswqm.hatir.ui.common

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * `cache/exports/` altindaki bir dosyayi FileProvider uzerinden paylasir.
 *
 * Dosya `filesDir` disinda oldugu icin `file_paths.xml` icindeki
 * `<cache-path name="exports" .../>` kuraliyla eslesir. Yetki, cagiran
 * uygulamaya yalnizca bu tek URI icin okuma hakki verir.
 */
fun shareFile(context: Context, file: File, mimeType: String) {
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull() ?: return

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooser = Intent.createChooser(intent, null).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(chooser) }
}
