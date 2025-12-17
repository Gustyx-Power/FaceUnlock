package me.gustyxpower.faceunlock.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private var faceRect: Rect? = null
    private var isFaceDetected = false
    
    // Paint untuk overlay gelap di luar lingkaran
    private val overlayPaint = Paint().apply {
        color = Color.parseColor("#99000000")
        style = Paint.Style.FILL
    }
    
    // Paint untuk lingkaran guide
    private val guidePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    
    // Paint untuk lingkaran saat wajah terdeteksi
    private val detectedPaint = Paint().apply {
        color = Color.parseColor("#4CAF50") // Green
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }
    
    // Paint untuk animasi saat capturing
    private val animPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }
    
    fun setFaceRect(rect: Rect?) {
        faceRect = rect
        isFaceDetected = rect != null
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f
        val centerY = height * 0.38f  // Sedikit ke atas dari tengah
        val ovalWidth = width * 0.65f
        val ovalHeight = ovalWidth * 1.3f  // Oval vertikal untuk wajah
        
        // Draw semi-transparent overlay dengan hole di tengah
        val path = Path()
        path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        
        val ovalRect = RectF(
            centerX - ovalWidth / 2,
            centerY - ovalHeight / 2,
            centerX + ovalWidth / 2,
            centerY + ovalHeight / 2
        )
        path.addOval(ovalRect, Path.Direction.CCW)
        path.fillType = Path.FillType.EVEN_ODD
        
        canvas.drawPath(path, overlayPaint)
        
        // Draw guide oval
        if (isFaceDetected) {
            // Wajah terdeteksi - gambar hijau
            canvas.drawOval(ovalRect, detectedPaint)
            
            // Gambar corner accents
            drawCornerAccents(canvas, ovalRect, detectedPaint.color)
        } else {
            // Tidak ada wajah - gambar putih
            canvas.drawOval(ovalRect, guidePaint)
        }
    }
    
    private fun drawCornerAccents(canvas: Canvas, rect: RectF, color: Int) {
        val accentPaint = Paint().apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }
        
        val accentLength = 30f
        val padding = 20f
        
        // Top left
        canvas.drawLine(
            rect.left + padding, rect.top + padding + accentLength,
            rect.left + padding, rect.top + padding,
            accentPaint
        )
        canvas.drawLine(
            rect.left + padding, rect.top + padding,
            rect.left + padding + accentLength, rect.top + padding,
            accentPaint
        )
        
        // Top right
        canvas.drawLine(
            rect.right - padding - accentLength, rect.top + padding,
            rect.right - padding, rect.top + padding,
            accentPaint
        )
        canvas.drawLine(
            rect.right - padding, rect.top + padding,
            rect.right - padding, rect.top + padding + accentLength,
            accentPaint
        )
        
        // Bottom left
        canvas.drawLine(
            rect.left + padding, rect.bottom - padding - accentLength,
            rect.left + padding, rect.bottom - padding,
            accentPaint
        )
        canvas.drawLine(
            rect.left + padding, rect.bottom - padding,
            rect.left + padding + accentLength, rect.bottom - padding,
            accentPaint
        )
        
        // Bottom right
        canvas.drawLine(
            rect.right - padding - accentLength, rect.bottom - padding,
            rect.right - padding, rect.bottom - padding,
            accentPaint
        )
        canvas.drawLine(
            rect.right - padding, rect.bottom - padding - accentLength,
            rect.right - padding, rect.bottom - padding,
            accentPaint
        )
    }
}
