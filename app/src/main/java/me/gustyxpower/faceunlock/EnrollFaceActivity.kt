package me.gustyxpower.faceunlock

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import me.gustyxpower.faceunlock.databinding.ActivityEnrollFaceBinding
import me.gustyxpower.faceunlock.manager.FaceDataManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class EnrollFaceActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityEnrollFaceBinding
    private lateinit var faceDataManager: FaceDataManager
    private lateinit var cameraExecutor: ExecutorService
    
    private var isProcessing = false
    private var capturedFaces = mutableListOf<FloatArray>()
    private var captureCount = 0
    private val requiredCaptures = 5
    
    companion object {
        private const val TAG = "EnrollFaceActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEnrollFaceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        faceDataManager = FaceDataManager(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        setupUI()
        startCamera()
    }
    
    private fun setupUI() {
        binding.apply {
            progressIndicator.max = requiredCaptures
            progressIndicator.progress = 0
            
            tvInstruction.text = getString(R.string.enroll_instruction)
            tvStatus.text = getString(R.string.enroll_status_no_face)
            
            btnCapture.setOnClickListener {
                if (!isProcessing) {
                    captureCount = 0
                    capturedFaces.clear()
                    isProcessing = true
                    progressIndicator.progress = 0
                    tvStatus.text = getString(R.string.enroll_status_capturing)
                }
            }
            
            btnCancel.setOnClickListener {
                finish()
            }
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }
            
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, FaceAnalyzer())
                }
            
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
            
        }, ContextCompat.getMainExecutor(this))
    }
    
    private inner class FaceAnalyzer : ImageAnalysis.Analyzer {
        
        private val faceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.3f)
                .build()
        )
        
        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
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
        runOnUiThread {
            if (faces.isEmpty()) {
                binding.tvStatus.text = getString(R.string.enroll_status_no_face)
                binding.faceOverlay.setFaceRect(null)
                return@runOnUiThread
            }
            
            val face = faces[0]
            binding.faceOverlay.setFaceRect(face.boundingBox)
            
            if (!isProcessing) {
                binding.tvStatus.text = getString(R.string.enroll_status_ready)
                return@runOnUiThread
            }
            
            // Check face quality
            val leftEyeOpen = face.leftEyeOpenProbability ?: 0f
            val rightEyeOpen = face.rightEyeOpenProbability ?: 0f
            
            if (leftEyeOpen < 0.5f || rightEyeOpen < 0.5f) {
                binding.tvStatus.text = getString(R.string.enroll_status_open_eyes)
                return@runOnUiThread
            }
            
            // Extract face features
            val features = extractFaceFeatures(face)
            capturedFaces.add(features)
            captureCount++
            
            binding.progressIndicator.progress = captureCount
            binding.tvStatus.text = getString(R.string.enroll_status_move_head)
            
            if (captureCount >= requiredCaptures) {
                val averageFeatures = averageFaceFeatures(capturedFaces)
                faceDataManager.saveFaceData(averageFeatures)
                
                isProcessing = false
                Toast.makeText(this, getString(R.string.enroll_success), Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
    
    private fun extractFaceFeatures(face: Face): FloatArray {
        val features = mutableListOf<Float>()
        
        val box = face.boundingBox
        features.add(box.width().toFloat() / box.height())
        
        face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)?.let {
            features.add(it.position.x)
            features.add(it.position.y)
        } ?: run {
            features.add(0f)
            features.add(0f)
        }
        
        face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)?.let {
            features.add(it.position.x)
            features.add(it.position.y)
        } ?: run {
            features.add(0f)
            features.add(0f)
        }
        
        face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.NOSE_BASE)?.let {
            features.add(it.position.x)
            features.add(it.position.y)
        } ?: run {
            features.add(0f)
            features.add(0f)
        }
        
        face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.MOUTH_LEFT)?.let {
            features.add(it.position.x)
            features.add(it.position.y)
        } ?: run {
            features.add(0f)
            features.add(0f)
        }
        
        face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.MOUTH_RIGHT)?.let {
            features.add(it.position.x)
            features.add(it.position.y)
        } ?: run {
            features.add(0f)
            features.add(0f)
        }
        
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
    
    private fun averageFaceFeatures(facesList: List<FloatArray>): FloatArray {
        if (facesList.isEmpty()) return floatArrayOf()
        
        val size = facesList[0].size
        val average = FloatArray(size)
        
        for (i in 0 until size) {
            average[i] = facesList.map { it[i] }.average().toFloat()
        }
        
        return average
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
