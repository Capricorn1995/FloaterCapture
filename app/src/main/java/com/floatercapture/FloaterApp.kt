package com.floatercapture

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class FloaterApp : Application() {

    companion object {
        const val CHANNEL_DOWNLOAD = "channel_download"
        const val CHANNEL_SERVICE = "channel_service"

        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            val downloadChannel = NotificationChannel(
                CHANNEL_DOWNLOAD,
                "下载通知",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示媒体下载进度和结果"
            }

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "服务通知",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "显示前台服务运行状态"
            }

            notificationManager.createNotificationChannels(
                listOf(downloadChannel, serviceChannel)
            )
        }
    }
}
