package me.gustyxpower.faceunlock

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class FaceUnlockApp : Application() {
    
    companion object {
        const val CHANNEL_ID = "face_unlock_channel"
        const val CHANNEL_NAME = "Face Unlock Service"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Face Unlock background service"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
