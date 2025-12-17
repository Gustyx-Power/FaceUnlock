package me.gustyxpower.faceunlock.view

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import me.gustyxpower.faceunlock.R

/**
 * Pill-shaped overlay yang muncul saat face detection di lock screen
 * Dibuat programatically untuk menghindari theme issues
 */
class FaceDetectionOverlay(private val context: Context) {
    
    companion object {
        private const val TAG = "FaceDetectionOverlay"
    }
    
    enum class State {
        DETECTING,
        DETECTED,
        UNLOCKING
    }
    
    private var overlayView: LinearLayout? = null
    private var tvStatus: TextView? = null
    private var ivFaceIcon: ImageView? = null
    private var progressBar: ProgressBar? = null
    private var windowManager: WindowManager? = null
    private var isShowing = false
    
    private val handler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null
    
    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Log.d(TAG, "FaceDetectionOverlay initialized")
    }
    
    fun show(state: State = State.DETECTING) {
        Log.d(TAG, "show() called with state: $state, isShowing: $isShowing")
        
        // Check permission first
        if (!Settings.canDrawOverlays(context)) {
            Log.e(TAG, "Cannot draw overlays - permission not granted")
            return
        }
        
        handler.post {
            try {
                if (overlayView == null) {
                    createOverlayProgrammatically()
                }
                
                updateState(state)
                
                if (!isShowing && overlayView != null) {
                    Log.d(TAG, "Adding overlay to window...")
                    val params = createLayoutParams()
                    windowManager?.addView(overlayView, params)
                    isShowing = true
                    Log.d(TAG, "Overlay added to window")
                    animateIn()
                    Log.d(TAG, "Overlay shown successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show overlay: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    fun updateState(state: State) {
        handler.post {
            try {
                when (state) {
                    State.DETECTING -> {
                        tvStatus?.text = context.getString(R.string.overlay_detecting)
                        progressBar?.visibility = View.VISIBLE
                        ivFaceIcon?.setColorFilter(Color.WHITE)
                    }
                    State.DETECTED -> {
                        tvStatus?.text = context.getString(R.string.overlay_detected)
                        progressBar?.visibility = View.GONE
                        ivFaceIcon?.setColorFilter(Color.parseColor("#4CAF50"))
                        ivFaceIcon?.let { animatePulse(it) }
                    }
                    State.UNLOCKING -> {
                        tvStatus?.text = context.getString(R.string.overlay_unlocking)
                        progressBar?.visibility = View.VISIBLE
                        ivFaceIcon?.setColorFilter(Color.parseColor("#4CAF50"))
                    }
                }
                Log.d(TAG, "State updated to: $state")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update state", e)
            }
        }
    }
    
    fun hide(delay: Long = 0) {
        Log.d(TAG, "hide() called with delay: $delay, isShowing: $isShowing")
        
        hideRunnable?.let { handler.removeCallbacks(it) }
        
        if (!isShowing || overlayView == null) {
            Log.d(TAG, "Not showing or view is null, skip hide")
            return
        }
        
        hideRunnable = Runnable {
            handler.post {
                if (isShowing && overlayView != null) {
                    try {
                        windowManager?.removeView(overlayView)
                        Log.d(TAG, "Overlay removed successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to remove overlay: ${e.message}")
                    }
                    overlayView = null
                    isShowing = false
                }
            }
        }
        
        if (delay > 0) {
            handler.postDelayed(hideRunnable!!, delay)
        } else {
            hideRunnable?.run()
        }
    }
    
    fun destroy() {
        Log.d(TAG, "destroy() called")
        hideRunnable?.let { handler.removeCallbacks(it) }
        if (isShowing && overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to destroy overlay: ${e.message}")
            }
        }
        overlayView = null
        isShowing = false
    }
    
    private fun createOverlayProgrammatically() {
        Log.d(TAG, "Creating overlay programmatically...")
        
        val density = context.resources.displayMetrics.density
        
        // Container (pill shape)
        overlayView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (16 * density).toInt(),
                (12 * density).toInt(),
                (16 * density).toInt(),
                (12 * density).toInt()
            )
            
            // Pill background
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24 * density
                setColor(Color.parseColor("#E6000000"))  // 90% black
            }
            
            elevation = 8 * density
        }
        
        // Face Icon
        ivFaceIcon = ImageView(context).apply {
            val size = (24 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            setImageResource(R.drawable.ic_face_scanning)
            setColorFilter(Color.WHITE)
        }
        overlayView?.addView(ivFaceIcon)
        
        // Status Text
        tvStatus = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (10 * density).toInt()
            }
            text = context.getString(R.string.overlay_detecting)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        overlayView?.addView(tvStatus)
        
        // Progress Bar
        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
            val size = (18 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = (10 * density).toInt()
            }
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        }
        overlayView?.addView(progressBar)
        
        // Start with invisible
        overlayView?.alpha = 0f
        overlayView?.scaleX = 0.8f
        overlayView?.scaleY = 0.8f
        
        Log.d(TAG, "Overlay created successfully, view: $overlayView")
    }
    
    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 150  // Offset from top
        }
    }
    
    private fun animateIn() {
        overlayView?.let { view ->
            val fadeIn = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
            val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 0.8f, 1f)
            val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 0.8f, 1f)
            val translateY = ObjectAnimator.ofFloat(view, "translationY", -50f, 0f)
            
            AnimatorSet().apply {
                playTogether(fadeIn, scaleX, scaleY, translateY)
                duration = 300
                interpolator = OvershootInterpolator(1.2f)
                start()
            }
        }
    }

    private fun animatePulse(view: View) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.3f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.3f, 1f)
        
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }
}
