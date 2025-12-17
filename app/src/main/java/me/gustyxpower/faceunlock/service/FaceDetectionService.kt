package me.gustyxpower.faceunlock.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import me.gustyxpower.faceunlock.FaceUnlockApp
import me.gustyxpower.faceunlock.MainActivity
import me.gustyxpower.faceunlock.R
import me.gustyxpower.faceunlock.manager.FaceDataManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceDetectionService : LifecycleService() {
    
    private lateinit var faceDataManager: FaceDataManager
    private lateinit var cameraExecutor: ExecutorService
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var isDetecting = false
    private var consecutiveMatches = 0
    private val requiredMatches = 3
    
    private val handler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        Log.d(TAG, "Face detection timeout")
        stopSelf()
    }
    
    companion object {
        private const val TAG = "FaceDetectionService"
        private const val NOTIFICATION_ID = 1001
        private const val DETECTION_TIMEOUT_MS = 10000L
    }
    
    override fun onCreate() {
        super.onCreate()
        
        faceDataManager = FaceDataManager(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        Log.d(TAG, "Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "Service started")
        
        if (!faceDataManager.isEnabled() || !faceDataManager.hasFaceData()) {
            Log.d(TAG, "Face unlock disabled or no face data")
            stopSelf()
            return START_NOT_STICKY
        }
        
        startForeground(NOTIFICATION_ID, createNotification())
        
        handler.postDelayed(timeoutRunnable, DETECTION_TIMEOUT_MS)
        
        startCamera()
        
        return START_NOT_STICKY
    }
    
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, FaceUnlockApp.CHANNEL_ID)
            .setContentTitle("Face Unlock")
            .setContentText(getString(R.string.overlay_detecting))
            .setSmallIcon(R.drawable.ic_face)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                
                @Suppress("DEPRECATION")
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, FaceAnalyzer())
                    }
                
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this, cameraSelector, imageAnalyzer
                )
                
                isDetecting = true
                Log.d(TAG, "Camera started")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera", e)
                stopSelf()
            }
            
        }, ContextCompat.getMainExecutor(this))
    }
    
    private inner class FaceAnalyzer : ImageAnalysis.Analyzer {
        
        private val faceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setMinFaceSize(0.3f)
                .build()
        )
        
        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            if (!isDetecting) {
                imageProxy.close()
                return
            }
            
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )
                
                faceDetector.process(image)
                    .addOnSuccessListener { faces ->
                        processFaces(faces)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Face detection failed", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
    
    private fun processFaces(faces: List<Face>) {
        if (faces.isEmpty()) {
            consecutiveMatches = 0
            return
        }
        
        val face = faces[0]
        val features = extractFaceFeatures(face)
        
        if (faceDataManager.isFaceMatch(features)) {
            consecutiveMatches++
            Log.d(TAG, "Face match! Count: $consecutiveMatches")
            
            if (consecutiveMatches >= requiredMatches) {
                Log.d(TAG, "Face matched! Unlocking...")
                performUnlock()
            }
        } else {
            consecutiveMatches = 0
        }
    }
    
    private fun extractFaceFeatures(face: Face): FloatArray {
        val features = mutableListOf<Float>()
        
        val box = face.boundingBox
        features.add(box.width().toFloat() / box.height())
        
        face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)?.let {
            features.add(it.position.x)
            features.add(it.position.y)
        } ?: run { features.add(0f); features.add(0f) }
        
        face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)?.let {
            features.add(it.position.x)
            features.add(it.position.y)
        } ?: run { features.add(0f); features.add(0f) }
        
        face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.NOSE_BASE)?.let {
            features.add(it.position.x)
            features.add(it.position.y)
        } ?: run { features.add(0f); features.add(0f) }
        
        face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.MOUTH_LEFT)?.let {
            features.add(it.position.x)
            features.add(it.position.y)
        } ?: run { features.add(0f); features.add(0f) }
        
        face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.MOUTH_RIGHT)?.let {
            features.add(it.position.x)
            features.add(it.position.y)
        } ?: run { features.add(0f); features.add(0f) }
        
        features.add(face.headEulerAngleX)
        features.add(face.headEulerAngleY)
        features.add(face.headEulerAngleZ)
        
        face.getContour(com.google.mlkit.vision.face.FaceContour.FACE)?.let { contour ->
            val points = contour.points
            val step = maxOf(1, points.size / 10)
            for (i in 0 until minOf(10, points.size) step step) {
                features.add(points[i].x)
                features.add(points[i].y)
            }
        }
        
        return normalizeFeatures(features.toFloatArray())
    }
    
    private fun normalizeFeatures(features: FloatArray): FloatArray {
        val min = features.minOrNull() ?: 0f
        val max = features.maxOrNull() ?: 1f
        val range = maxOf(max - min, 0.001f)
        return features.map { (it - min) / range }.toFloatArray()
    }
    
    private fun performUnlock() {
        isDetecting = false
        handler.removeCallbacks(timeoutRunnable)
        
        // Panggil performUnlock() yang akan melakukan swipe up + input kredensial
        FaceUnlockAccessibilityService.instance?.performUnlock()
        
        handler.postDelayed({
            stopSelf()
        }, 2000)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeoutRunnable)
        isDetecting = false
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        Log.d(TAG, "Service destroyed")
    }
}
