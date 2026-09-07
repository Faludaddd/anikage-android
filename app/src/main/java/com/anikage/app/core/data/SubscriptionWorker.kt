package com.anikage.app.core.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anikage.app.MainActivity
import com.anikage.app.core.data.db.SubscriptionEntity
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogCategory
import java.util.concurrent.TimeUnit

/**
 * REAL subscription engine (user directive #12): periodically checks every
 * subscribed anime against the live Anikage backend (the same
 * /api/media/anime/{slug} endpoint the site's own pages use) and posts a
 * local notification when a new episode is available.
 *
 * Detection: an episode is "out" when the info payload's totalEpisodes (or
 * nextAiringEpisode that has since aired) is HIGHER than the highest value
 * we already notified about. Deduplication is [SubscriptionEntity.lastNotifiedEpisode]
 * — a given episode number is notified at most once, ever.
 */
class SubscriptionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val repo = AnikageRepository.get(context)
        val subs = runCatching { repo.allSubscriptions() }.getOrDefault(emptyList())
        if (subs.isEmpty()) return Result.success()
        AppLogger.d(LogCategory.DATA, "Subscription check: ${subs.size} anime")

        var notified = 0
        for (sub in subs) {
            val slug = sub.slug ?: continue
            // The site's own info endpoint — real episode counts.
            val info = repo.animeInfoBySlug(slug) ?: continue // network failure: retry next cycle

            val released = info.totalEpisodes
                ?: info.nextAiringEpisode?.let { next ->
                    // nextAiringEpisode counts the UPCOMING episode; it's out
                    // when its airing time has passed.
                    if ((next.airingAt ?: 0L) * 1000 <= System.currentTimeMillis()) next.episode else null
                }
                ?: continue
            val nextAiring = info.nextAiringEpisode?.episode
            val status = info.status

            if (released > sub.lastNotifiedEpisode) {
                val title = sub.titleEnglish ?: sub.titleRomaji ?: "New episode"
                postNewEpisodeNotification(context, sub.animeId, title, released, sub.posterUrl)
                repo.markSubscriptionNotified(sub.animeId, released, released, nextAiring, status)
                notified++
                AppLogger.i(
                    LogCategory.DATA,
                    "Subscription notification: $title episode $released " +
                        "(was ${sub.lastNotifiedEpisode}, known ${sub.lastKnownEpisodes})",
                )
            } else {
                repo.updateSubscriptionCheck(sub.animeId, released, nextAiring, status)
            }
        }
        AppLogger.d(LogCategory.DATA, "Subscription check done: $notified new episode(s)")
        return Result.success()
    }

    private fun postNewEpisodeNotification(
        context: Context,
        animeId: Int,
        title: String,
        episode: Int,
        posterUrl: String?,
    ) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Permission not granted (Android 13+): the subscription page's
            // "new episode" indicator still shows the release — honest, not lost.
            AppLogger.d(LogCategory.DATA, "Notification permission missing — in-app indicator only")
            return
        }
        val deepLink = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("anikage.open.animeId", animeId)
            putExtra("anikage.open.episode", episode)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            animeId,
            deepLink,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText("Episode $episode is out — watch it now on Anikage")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Episode $episode is out — watch it now on Anikage"))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(animeId, notification) }
    }

    companion object {
        private const val CHANNEL_ID = "anikage_subscriptions"
        private const val WORK_NAME = "anikage_subscription_check"

        /** Register the notification channel + schedule the periodic check. */
        fun ensure(context: Context) {
            ensureChannel(context)
            val request = PeriodicWorkRequestBuilder<SubscriptionWorker>(6, TimeUnit.HOURS)
                .setInitialDelay(10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "New episodes",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = "Notifies when a subscribed anime releases a new episode."
                    },
                )
            }
        }
    }
}
