package com.araswqm.hatir.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.araswqm.hatir.MainActivity
import com.araswqm.hatir.R
import com.araswqm.hatir.core.Tuning
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kendiliğinden mesaj bildirimleri.
 *
 * Her karakter icin ayri bir kanal acilir (kullanici karakter bazinda sessize
 * alabilir). Bildirim **yalnizca metindir**; satir ici yanit yoktur. Dokununca
 * ilgili sohbet acilir ([MainActivity] `onNewIntent`).
 */
@Singleton
class Notifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannel(characterId: String, characterName: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            channelId(characterId),
            characterName,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "$characterName sana kendiliğinden yazdığında bildir."
        }
        manager.createNotificationChannel(channel)
    }

    fun showMessage(characterId: String, characterName: String, text: String) {
        if (!canPost()) return
        ensureChannel(characterId, characterName)

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_CHARACTER_ID, characterId)
        }
        val pending = PendingIntent.getActivity(
            context,
            characterId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId(characterId))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(characterName)
            .setContentText(text.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(Tuning.NOTIFICATION_ID_BASE + characterId.hashCode(), notification)
        }
    }

    fun canPost(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun channelId(characterId: String): String = "proactive_$characterId"

    companion object {
        const val EXTRA_CHARACTER_ID = "extra_character_id"
    }
}
