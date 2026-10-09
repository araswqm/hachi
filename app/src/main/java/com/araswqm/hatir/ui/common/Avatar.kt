package com.araswqm.hatir.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import java.io.File

/**
 * Yuvarlak karakter avatarı.
 *
 * Coil 3'te `java.io.File` için bir mapper yok; bu yüzden model olarak
 * `file://` şemalı bir metin veriyoruz (StringMapper -> FileUriFetcher).
 * Dosya yok veya çözülemezse arkadaki baş harf rozeti görünür kalır.
 *
 * [cacheKey] aynı yoldaki dosya değiştiğinde (editörde avatar değişimi)
 * önbelleği geçersiz kılmak için kullanılır.
 *
 * [bytes] doluysa (editörde henüz kaydedilmemiş yeni avatar) dosya yerine
 * doğrudan bellekten çözülen görüntü çizilir.
 */
@Composable
fun CharacterAvatar(
    file: File?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    cacheKey: String? = null,
    bytes: ByteArray? = null,
) {
    val decoded = remember(bytes) {
        bytes?.let { raw ->
            runCatching { BitmapFactory.decodeByteArray(raw, 0, raw.size) }.getOrNull()
        }
    }

    Box(
        modifier = modifier.size(size).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        InitialBadge(name = name, size = size)

        when {
            decoded != null -> Image(
                bitmap = decoded.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            file != null && file.isFile -> AvatarFromFile(
                file = file,
                contentDescription = contentDescription,
                cacheKey = cacheKey,
            )
        }
    }
}

@Composable
private fun AvatarFromFile(
    file: File,
    contentDescription: String?,
    cacheKey: String?,
) {
    val context = LocalPlatformContext.current
    val path = file.absolutePath
    val request = remember(path, cacheKey) {
        ImageRequest.Builder(context)
            .data("file://$path")
            .apply {
                if (cacheKey != null) {
                    memoryCacheKey("$path#$cacheKey")
                    diskCacheKey("$path#$cacheKey")
                }
            }
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

/** Avatar yokken gösterilen, adın ilk harfini taşıyan renkli daire. */
@Composable
private fun InitialBadge(name: String, size: Dp) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val initial = name.trim().take(1).uppercase()
        if (initial.isNotEmpty()) {
            Text(
                text = initial,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.42f).coerceAtLeast(10f).sp,
            )
        }
    }
}
