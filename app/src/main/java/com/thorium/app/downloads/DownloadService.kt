package com.thorium.app.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.thorium.app.LauncherActivity
import com.thorium.app.R
import com.thorium.app.ThoriumApplication
import com.thorium.core.model.DownloadItem
import com.thorium.core.model.DownloadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the process alive and shows a notification while downloads are queued or running. The
 * engine itself lives in the application; this service only holds the foreground state and stops
 * itself when there is nothing left to do.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        // Must show a notification right away once started as a foreground service.
        startForeground(NOTIFICATION_ID, build(this, emptyList()))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = (application as ThoriumApplication).downloads
        if (manager == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        watcher?.cancel()
        watcher = scope.launch {
            manager.items.collect { items ->
                val working = items.filter { it.state.isWorking }
                if (working.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(this@DownloadService, working))
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1

        /** Items that need the process to stay alive: not paused and not finished. */
        val DownloadState.isWorking: Boolean get() = this == DownloadState.Queued || isActive

        private fun createChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }

        private fun build(context: Context, working: List<DownloadItem>): Notification {
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, LauncherActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val builder = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(context.getString(R.string.notif_title))
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
            when (working.size) {
                0 -> Unit
                1 -> {
                    val item = working.single()
                    val percent = item.progress?.let { (it * 100).toInt() }
                    builder.setContentText(if (percent != null) "${item.title} · $percent%" else item.title)
                    if (percent != null) builder.setProgress(100, percent, false) else builder.setProgress(0, 0, true)
                }
                else -> {
                    builder.setContentText(context.resources.getQuantityString(R.plurals.notif_many, working.size, working.size))
                    val known = working.filter { (it.sizeBytes ?: 0) > 0 }
                    val total = known.sumOf { it.sizeBytes ?: 0 }
                    if (total > 0) builder.setProgress(100, (known.sumOf { it.bytesDone } * 100 / total).toInt(), false)
                }
            }
            return builder.build()
        }
    }
}
