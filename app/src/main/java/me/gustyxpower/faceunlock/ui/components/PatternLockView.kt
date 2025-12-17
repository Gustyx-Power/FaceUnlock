package me.gustyxpower.faceunlock.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/**
 * Composable untuk menggambar pattern lock 3x3
 * Grid:
 * 1  2  3
 * 4  5  6
 * 7  8  9
 */
@Composable
fun PatternLockView(
    modifier: Modifier = Modifier,
    onPatternChanged: (String) -> Unit = {},
    onPatternComplete: (String) -> Unit = {}
) {
    var selectedDots by remember { mutableStateOf(listOf<Int>()) }
    var currentTouchPosition by remember { mutableStateOf<Offset?>(null) }
    var isDrawing by remember { mutableStateOf(false) }
    var dotPositions by remember { mutableStateOf(Array(9) { Offset.Zero }) }
    
    val dotColor = MaterialTheme.colorScheme.outline
    val selectedDotColor = MaterialTheme.colorScheme.primary
    val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    val currentLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    
    val dotRadius = 16f
    val selectedDotRadius = 22f
    val hitRadius = 50f
    
    fun getIntermediateDot(from: Int, to: Int): Int? {
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
    
    fun handleTouch(position: Offset) {
        for (i in 0 until 9) {
            val dotPos = dotPositions[i]
            val distance = sqrt(
                (position.x - dotPos.x) * (position.x - dotPos.x) +
                (position.y - dotPos.y) * (position.y - dotPos.y)
            )
            
            val dotNumber = i + 1
            if (distance <= hitRadius && !selectedDots.contains(dotNumber)) {
                val newList = selectedDots.toMutableList()
                
                // Add intermediate dots if needed
                if (newList.isNotEmpty()) {
                    val lastDot = newList.last()
                    val intermediate = getIntermediateDot(lastDot, dotNumber)
                    if (intermediate != null && !newList.contains(intermediate)) {
                        newList.add(intermediate)
                    }
                }
                
                newList.add(dotNumber)
                selectedDots = newList
                onPatternChanged(newList.joinToString(""))
                break
            }
        }
    }
    
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                RoundedCornerShape(16.dp)
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            selectedDots = emptyList()
                            isDrawing = true
                            currentTouchPosition = offset
                            handleTouch(offset)
                        },
                        onDrag = { change, _ ->
                            currentTouchPosition = change.position
                            handleTouch(change.position)
                        },
                        onDragEnd = {
                            isDrawing = false
                            currentTouchPosition = null
                            if (selectedDots.isNotEmpty()) {
                                onPatternComplete(selectedDots.joinToString(""))
                            }
                        },
                        onDragCancel = {
                            isDrawing = false
                            currentTouchPosition = null
                        }
                    )
                }
        ) {
            val canvasSize = size.minDimension
            val padding = canvasSize * 0.1f
            val spacing = (canvasSize - 2 * padding) / 2
            
            // Calculate dot positions
            for (i in 0 until 9) {
                val row = i / 3
                val col = i % 3
                dotPositions[i] = Offset(
                    padding + col * spacing,
                    padding + row * spacing
                )
            }
            
            // Draw connecting lines
            if (selectedDots.size > 1) {
                val path = Path()
                val firstDot = selectedDots[0] - 1
                path.moveTo(dotPositions[firstDot].x, dotPositions[firstDot].y)
                
                for (i in 1 until selectedDots.size) {
                    val dotIndex = selectedDots[i] - 1
                    path.lineTo(dotPositions[dotIndex].x, dotPositions[dotIndex].y)
                }
                
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(
                        width = 8f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            
            // Draw line to current touch position
            if (isDrawing && selectedDots.isNotEmpty() && currentTouchPosition != null) {
                val lastDot = selectedDots.last() - 1
                drawLine(
                    color = currentLineColor,
                    start = dotPositions[lastDot],
                    end = currentTouchPosition!!,
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            }
            
            // Draw dots
            for (i in 0 until 9) {
                val pos = dotPositions[i]
                val dotNumber = i + 1
                val isSelected = selectedDots.contains(dotNumber)
                
                // Outer ring for selected dots
                if (isSelected) {
                    drawCircle(
                        color = selectedDotColor.copy(alpha = 0.3f),
                        radius = selectedDotRadius + 12f,
                        center = pos
                    )
                }
                
                // Main dot
                drawCircle(
                    color = if (isSelected) selectedDotColor else dotColor,
                    radius = if (isSelected) selectedDotRadius else dotRadius,
                    center = pos
                )
                
                // Inner dot for selected
                if (isSelected) {
                    drawCircle(
                        color = Color.White,
                        radius = 6f,
                        center = pos
                    )
                }
            }
        }
    }
}
