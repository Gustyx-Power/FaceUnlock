package me.gustyxpower.faceunlock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ScreenStateReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ScreenStateReceiver"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SCREEN_ON -> Log.d(TAG, "Screen turned ON")
            Intent.ACTION_SCREEN_OFF -> Log.d(TAG, "Screen turned OFF")
            Intent.ACTION_USER_PRESENT -> Log.d(TAG, "User present (unlocked)")
        }
    }
}
