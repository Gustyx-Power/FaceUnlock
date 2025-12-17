package me.gustyxpower.faceunlock.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt

/**
 * Custom view untuk menggambar pattern lock 3x3
 * Pattern akan direpresentasikan sebagai string angka 1-9
 * Grid:
 * 1  2  3
 * 4  5  6
 * 7  8  9
 */
class PatternView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    // Paint objects
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E")
        style = Paint.Style.FILL
    }
    
    private val selectedDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.FILL
    }
    
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    
    private val currentLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#64B5F6")
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }
    
    private val dotNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#757575")
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }
    
    // Dot properties
    private val dotRadius = 30f
    private val selectedDotRadius = 40f
    private val hitRadius = 80f
    
    // Pattern tracking
    private val selectedDots = mutableListOf<Int>()
    private var isDrawing = false
    private var currentX = 0f
    private var currentY = 0f
    
    // Dot positions (calculated in onSizeChanged)
    private val dotPositions = Array(9) { floatArrayOf(0f, 0f) }
    
    // Listener
    var onPatternCompleteListener: ((String) -> Unit)? = null
    var onPatternChangedListener: ((String) -> Unit)? = null
    
    // Mode
    var showNumbers = true
        set(value) {
            field = value
            invalidate()
        }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateDotPositions()
    }
    
    private fun calculateDotPositions() {
        val size = minOf(width, height).toFloat()
        val padding = size * 0.15f
        val spacing = (size - 2 * padding) / 2
        val startX = (width - size) / 2 + padding
        val startY = (height - size) / 2 + padding
        
        for (i in 0 until 9) {
            val row = i / 3
            val col = i % 3
            dotPositions[i][0] = startX + col * spacing
            dotPositions[i][1] = startY + row * spacing
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw connecting lines
        if (selectedDots.size > 1) {
            val path = Path()
            val firstDot = selectedDots[0] - 1
            path.moveTo(dotPositions[firstDot][0], dotPositions[firstDot][1])
            
            for (i in 1 until selectedDots.size) {
                val dotIndex = selectedDots[i] - 1
                path.lineTo(dotPositions[dotIndex][0], dotPositions[dotIndex][1])
            }
            
            canvas.drawPath(path, linePaint)
        }
        
        // Draw line to current touch position
        if (isDrawing && selectedDots.isNotEmpty()) {
            val lastDot = selectedDots.last() - 1
            canvas.drawLine(
                dotPositions[lastDot][0],
                dotPositions[lastDot][1],
                currentX,
                currentY,
                currentLinePaint
            )
        }
        
        // Draw dots
        for (i in 0 until 9) {
            val x = dotPositions[i][0]
            val y = dotPositions[i][1]
            val dotNumber = i + 1
            
            val isSelected = selectedDots.contains(dotNumber)
            val paint = if (isSelected) selectedDotPaint else dotPaint
            val radius = if (isSelected) selectedDotRadius else dotRadius
            
            // Draw outer ring for selected dots
            if (isSelected) {
                val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#BBDEFB")
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                }
                canvas.drawCircle(x, y, radius + 10f, ringPaint)
            }
            
            canvas.drawCircle(x, y, radius, paint)
            
            // Draw number
            if (showNumbers) {
                dotNumberPaint.color = if (isSelected) Color.WHITE else Color.parseColor("#757575")
                canvas.drawText(
                    dotNumber.toString(),
                    x,
                    y + dotNumberPaint.textSize / 3,
                    dotNumberPaint
                )
            }
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                clearPattern()
                isDrawing = true
                handleTouch(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    currentX = event.x
                    currentY = event.y
                    handleTouch(event.x, event.y)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                isDrawing = false
                if (selectedDots.isNotEmpty()) {
                    val pattern = getPatternString()
                    onPatternCompleteListener?.invoke(pattern)
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    private fun handleTouch(x: Float, y: Float) {
        for (i in 0 until 9) {
            val dotX = dotPositions[i][0]
            val dotY = dotPositions[i][1]
            val distance = sqrt((x - dotX) * (x - dotX) + (y - dotY) * (y - dotY))
            
            val dotNumber = i + 1
            if (distance <= hitRadius && !selectedDots.contains(dotNumber)) {
                // Check if we need to add intermediate dots
                if (selectedDots.isNotEmpty()) {
                    val lastDot = selectedDots.last()
                    addIntermediateDots(lastDot, dotNumber)
                }
                
                selectedDots.add(dotNumber)
                onPatternChangedListener?.invoke(getPatternString())
                invalidate()
                break
            }
        }
    }
    
    /**
     * Add any dots that are in between two dots
     * This handles cases like going from 1 to 3 (should include 2)
     */
    private fun addIntermediateDots(from: Int, to: Int) {
        val intermediate = getIntermediateDot(from, to)
        if (intermediate != null && !selectedDots.contains(intermediate)) {
            selectedDots.add(intermediate)
        }
    }
    
    private fun getIntermediateDot(from: Int, to: Int): Int? {
        // Map of dots that have intermediate dots between them
        val intermediates = mapOf(
            Pair(1, 3) to 2, Pair(3, 1) to 2,
            Pair(1, 7) to 4, Pair(7, 1) to 4,
            Pair(1, 9) to 5, Pair(9, 1) to 5,
            Pair(2, 8) to 5, Pair(8, 2) to 5,
            Pair(3, 7) to 5, Pair(7, 3) to 5,
            Pair(3, 9) to 6, Pair(9, 3) to 6,
            Pair(4, 6) to 5, Pair(6, 4) to 5,
            Pair(7, 9) to 8, Pair(9, 7) to 8
        )
        return intermediates[Pair(from, to)]
    }
    
    fun clearPattern() {
        selectedDots.clear()
        isDrawing = false
        invalidate()
    }
    
    fun getPatternString(): String {
        return selectedDots.joinToString("")
    }
    
    fun setPattern(pattern: String) {
        clearPattern()
        pattern.forEach { char ->
            val dotNumber = char.digitToIntOrNull()
            if (dotNumber != null && dotNumber in 1..9 && !selectedDots.contains(dotNumber)) {
                selectedDots.add(dotNumber)
            }
        }
        invalidate()
    }
    
    fun getPatternLength(): Int = selectedDots.size
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = minOf(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec),
            600
        )
        setMeasuredDimension(size, size)
    }
}
