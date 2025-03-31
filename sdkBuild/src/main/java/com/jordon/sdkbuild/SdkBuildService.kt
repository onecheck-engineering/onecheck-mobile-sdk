package com.jordon.sdkbuild

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class SdkBuildService: Service() {

    private val TAG = "SdkBuildService"
    private val CHANNEL_ID = "onecheck-sdk-test"
    private val NOTI_ID = 1


    private val handler = Handler(Looper.getMainLooper())
    private val logRunnable = object : Runnable {
        override fun run() {
            Log.d(TAG, "Hello, World!")
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTI_ID, notification)
        handler.post(logRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(logRunnable)
    }

    private fun createNotification(): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SdkBuildTest SDK")
            .setContentText("Running in background...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Sdk Build Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }


    override fun onBind(p0: Intent?): IBinder? = null
}