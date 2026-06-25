package com.floatercapture.util

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.floatercapture.FloaterApp

object NotificationHelper {

    private const val DOWNLOAD_NOTIFICATION_ID = 2001
    private const val SERVICE_NOTIFICATION_ID = 2002

    fun createServiceNotification(context: Context): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, FloaterApp.CHANNEL_SERVICE)
            .setContentTitle("FloaterCapture 运行中")
            .setContentText("正在监听屏幕内容")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun createDownloadProgressNotification(
        context: Context,
        fileName: String,
        progress: Int
    ): Notification {
        return NotificationCompat.Builder(context, FloaterApp.CHANNEL_DOWNLOAD)
            .setContentTitle("正在下载")
            .setContentText(fileName)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress.coerceIn(0, 100), false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    fun createDownloadCompleteNotification(
        context: Context,
        fileName: String
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, FloaterApp.CHANNEL_DOWNLOAD)
            .setContentTitle("下载完成")
            .setContentText(fileName)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun showDownloadProgress(context: Context, fileName: String, progress: Int) {
        val notification = createDownloadProgressNotification(context, fileName, progress)
        NotificationManagerCompat.from(context).notify(DOWNLOAD_NOTIFICATION_ID, notification)
    }

    fun showDownloadComplete(context: Context, fileName: String) {
        val notification = createDownloadCompleteNotification(context, fileName)
        NotificationManagerCompat.from(context).notify(DOWNLOAD_NOTIFICATION_ID, notification)
    }

    fun showServiceNotification(context: Context) {
        val notification = createServiceNotification(context)
        NotificationManagerCompat.from(context).notify(SERVICE_NOTIFICATION_ID, notification)
    }

    fun cancelDownloadNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(DOWNLOAD_NOTIFICATION_ID)
    }

    fun cancelServiceNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(SERVICE_NOTIFICATION_ID)
    }
}
