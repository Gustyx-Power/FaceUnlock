package me.gustyxpower.faceunlock.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlin.math.sqrt

class FaceDataManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "face_unlock_prefs", Context.MODE_PRIVATE
    )
    
    companion object {
        private const val TAG = "FaceDataManager"
        private const val KEY_FACE_DATA = "face_data"
        private const val KEY_ENABLED = "enabled"
        private const val MATCH_THRESHOLD = 0.75f
    }
    
    fun saveFaceData(features: FloatArray) {
        val data = features.joinToString(",")
        prefs.edit().putString(KEY_FACE_DATA, data).apply()
        Log.d(TAG, "Face data saved: ${features.size} features")
    }
    
    fun getFaceData(): FloatArray? {
        val data = prefs.getString(KEY_FACE_DATA, null) ?: return null
        return try {
            data.split(",").map { it.toFloat() }.toFloatArray()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing face data", e)
            null
        }
    }
    
    fun hasFaceData(): Boolean {
        return prefs.getString(KEY_FACE_DATA, null) != null
    }
    
    fun deleteFaceData() {
        prefs.edit().remove(KEY_FACE_DATA).apply()
        Log.d(TAG, "Face data deleted")
    }
    
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
    
    fun isEnabled(): Boolean {
        return prefs.getBoolean(KEY_ENABLED, false)
    }
    
    fun compareFaces(features1: FloatArray, features2: FloatArray): Float {
        if (features1.size != features2.size) {
            Log.w(TAG, "Feature size mismatch: ${features1.size} vs ${features2.size}")
            return 0f
        }
        
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in features1.indices) {
            dotProduct += features1[i] * features2[i]
            norm1 += features1[i] * features1[i]
            norm2 += features2[i] * features2[i]
        }
        
        val magnitude = sqrt(norm1) * sqrt(norm2)
        if (magnitude == 0f) return 0f
        
        val similarity = dotProduct / magnitude
        Log.d(TAG, "Face similarity: $similarity")
        return similarity
    }
    
    fun isFaceMatch(detectedFeatures: FloatArray): Boolean {
        val enrolledData = getFaceData() ?: return false
        val similarity = compareFaces(enrolledData, detectedFeatures)
        val isMatch = similarity >= MATCH_THRESHOLD
        Log.d(TAG, "Face match: $isMatch (similarity: $similarity, threshold: $MATCH_THRESHOLD)")
        return isMatch
    }
    
    fun getMatchConfidence(detectedFeatures: FloatArray): Int {
        val enrolledData = getFaceData() ?: return 0
        val similarity = compareFaces(enrolledData, detectedFeatures)
        return (similarity * 100).toInt().coerceIn(0, 100)
    }
}
