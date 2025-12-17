package me.gustyxpower.faceunlock.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import me.gustyxpower.faceunlock.manager.CredentialManager

class FaceUnlockAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "FaceUnlockA11yService"
        var instance: FaceUnlockAccessibilityService? = null
            private set
    }
    
    private var isReceiverRegistered = false
    private lateinit var credentialManager: CredentialManager
    private val handler = Handler(Looper.getMainLooper())
    
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Received intent: ${intent?.action}")
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "Screen ON - starting face detection")
                    startFaceDetection()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen OFF - stopping face detection")
                    stopFaceDetection()
                }
                Intent.ACTION_USER_PRESENT -> {
                    Log.d(TAG, "User present - device unlocked")
                    stopFaceDetection()
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        credentialManager = CredentialManager(this)
        Log.d(TAG, "Accessibility Service created")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service connected")
        
        registerScreenReceiver()
    }
    
    private fun registerScreenReceiver() {
        if (isReceiverRegistered) return
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
            isReceiverRegistered = true
            Log.d(TAG, "Screen receiver registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register screen receiver", e)
        }
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Monitor untuk mendeteksi lock screen UI jika diperlukan
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
                isReceiverRegistered = false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister receiver", e)
            }
        }
        stopFaceDetection()
        Log.d(TAG, "Accessibility Service destroyed")
    }
    
    private fun startFaceDetection() {
        val intent = Intent(this, FaceDetectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    
    private fun stopFaceDetection() {
        val intent = Intent(this, FaceDetectionService::class.java)
        stopService(intent)
    }
    
    /**
     * Dipanggil setelah wajah terverifikasi
     * Akan melakukan swipe up, lalu input kredensial jika tersedia
     */
    fun performUnlock() {
        Log.d(TAG, "=== Performing unlock sequence ===")
        
        // Step 1: Swipe up untuk membuka lock screen
        performSwipeUp()
        
        // Step 2: Delay untuk menunggu lock screen UI muncul, lalu input kredensial
        val lockType = credentialManager.getLockType()
        Log.d(TAG, "Lock type: $lockType")
        
        // Delay lebih lama untuk memastikan lock screen sudah muncul
        val delayMs = 1500L
        
        when (lockType) {
            CredentialManager.LockType.PIN -> {
                val pin = credentialManager.getCredential()
                if (pin != null) {
                    Log.d(TAG, "PIN found (${pin.length} digits), will input in ${delayMs}ms")
                    handler.postDelayed({
                        inputPin(pin)
                    }, delayMs)
                } else {
                    Log.e(TAG, "PIN is null!")
                }
            }
            CredentialManager.LockType.PASSWORD -> {
                val password = credentialManager.getCredential()
                if (password != null) {
                    Log.d(TAG, "Password found (${password.length} chars), will input in ${delayMs}ms")
                    handler.postDelayed({
                        inputPassword(password)
                    }, delayMs)
                } else {
                    Log.e(TAG, "Password is null!")
                }
            }
            else -> {
                Log.d(TAG, "No credential saved, only swiping up")
            }
        }
    }
    
    fun performSwipeUp() {
        Log.d(TAG, "Performing swipe up gesture")
        
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        val startX = screenWidth / 2f
        val startY = screenHeight * 0.8f
        val endX = screenWidth / 2f
        val endY = screenHeight * 0.3f
        
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Swipe up gesture completed")
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Swipe up gesture cancelled")
            }
        }, null)
    }
    
    /**
     * Input PIN dengan menekan tombol numerik
     * Menggunakan dua pendekatan: accessibility nodes dan koordinat layar
     */
    private fun inputPin(pin: String) {
        Log.d(TAG, "=== Starting PIN input: ${pin.length} digits ===")
        
        // Coba gunakan accessibility nodes terlebih dahulu
        val success = tryInputPinWithNodes(pin)
        
        if (!success) {
            Log.d(TAG, "Accessibility nodes not found, using screen coordinates")
            inputPinWithCoordinates(pin)
        }
    }
    
    /**
     * Input PIN menggunakan accessibility nodes (lebih akurat)
     */
    private fun tryInputPinWithNodes(pin: String): Boolean {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.e(TAG, "Root node is null")
            return false
        }
        
        try {
            var foundButtons = false
            
            pin.forEachIndexed { index, digit ->
                handler.postDelayed({
                    val digitStr = digit.toString()
                    val buttons = rootNode.findAccessibilityNodeInfosByText(digitStr)
                    
                    var clicked = false
                    for (button in buttons) {
                        // Pastikan ini tombol yang bisa diklik dan hanya berisi digit
                        val text = button.text?.toString()?.trim() ?: ""
                        if (button.isClickable && text == digitStr) {
                            button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            Log.d(TAG, "Clicked digit $digit via accessibility node")
                            clicked = true
                            foundButtons = true
                            break
                        }
                        // Coba parent jika button tidak clickable
                        val parent = button.parent
                        if (parent != null && parent.isClickable) {
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            Log.d(TAG, "Clicked digit $digit via parent node")
                            clicked = true
                            foundButtons = true
                            break
                        }
                    }
                    
                    if (!clicked) {
                        Log.w(TAG, "Could not find button for digit $digit, trying coordinates")
                        tapDigitAtCoordinate(digit)
                    }
                }, index * 250L)
            }
            
            // Pada beberapa device, PIN otomatis submit setelah 4-6 digit
            // Tapi untuk amannya, coba tekan Enter/OK jika ada
            handler.postDelayed({
                tryClickEnterButton()
            }, pin.length * 250L + 400L)
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error using accessibility nodes", e)
            return false
        }
    }
    
    /**
     * Tekan digit menggunakan koordinat layar
     */
    private fun tapDigitAtCoordinate(digit: Char) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // Keypad biasanya di 50-90% tinggi layar
        val keypadStartY = screenHeight * 0.50f
        val keypadHeight = screenHeight * 0.40f
        val buttonWidth = screenWidth / 3f
        val buttonHeight = keypadHeight / 4f
        
        val (x, y) = when (digit) {
            '1' -> Pair(buttonWidth * 0.5f, keypadStartY + buttonHeight * 0.5f)
            '2' -> Pair(buttonWidth * 1.5f, keypadStartY + buttonHeight * 0.5f)
            '3' -> Pair(buttonWidth * 2.5f, keypadStartY + buttonHeight * 0.5f)
            '4' -> Pair(buttonWidth * 0.5f, keypadStartY + buttonHeight * 1.5f)
            '5' -> Pair(buttonWidth * 1.5f, keypadStartY + buttonHeight * 1.5f)
            '6' -> Pair(buttonWidth * 2.5f, keypadStartY + buttonHeight * 1.5f)
            '7' -> Pair(buttonWidth * 0.5f, keypadStartY + buttonHeight * 2.5f)
            '8' -> Pair(buttonWidth * 1.5f, keypadStartY + buttonHeight * 2.5f)
            '9' -> Pair(buttonWidth * 2.5f, keypadStartY + buttonHeight * 2.5f)
            '0' -> Pair(buttonWidth * 1.5f, keypadStartY + buttonHeight * 3.5f)
            else -> Pair(screenWidth / 2f, screenHeight / 2f)
        }
        
        performTap(x, y)
        Log.d(TAG, "Tapped digit $digit at coordinate ($x, $y)")
    }
    
    /**
     * Input PIN hanya dengan koordinat layar (fallback)
     */
    private fun inputPinWithCoordinates(pin: String) {
        Log.d(TAG, "Using coordinate-based PIN input")
        
        pin.forEachIndexed { index, digit ->
            handler.postDelayed({
                tapDigitAtCoordinate(digit)
            }, index * 200L)
        }
        
        // Tekan enter
        handler.postDelayed({
            tryClickEnterButton()
        }, pin.length * 200L + 300L)
    }
    
    /**
     * Cari dan klik tombol Enter/OK/Confirm
     */
    private fun tryClickEnterButton() {
        val rootNode = rootInActiveWindow ?: return
        
        try {
            // Cari tombol dengan berbagai label
            val buttonLabels = listOf("OK", "ok", "Enter", "ENTER", "✓", "→", "Done", "Confirm", "MASUK", "Masuk")
            
            for (label in buttonLabels) {
                val buttons = rootNode.findAccessibilityNodeInfosByText(label)
                for (button in buttons) {
                    if (button.isClickable) {
                        button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d(TAG, "Clicked enter/OK button: $label")
                        return
                    }
                    // Coba parent
                    val parent = button.parent
                    if (parent != null && parent.isClickable) {
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d(TAG, "Clicked enter/OK button parent: $label")
                        return
                    }
                }
            }
            
            // Jika tidak ditemukan, tap di posisi umum tombol Enter (kanan bawah keypad)
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            val enterX = screenWidth * 0.83f
            val enterY = screenHeight * 0.88f
            performTap(enterX, enterY)
            Log.d(TAG, "Tapped enter at fallback position ($enterX, $enterY)")
            
        } finally {
            // No need to recycle rootNode on newer APIs
        }
    }
    
    /**
     * Input password menggunakan accessibility node
     */
    private fun inputPassword(password: String) {
        Log.d(TAG, "=== Inputting password (${password.length} chars) ===")
        
        // Cari text field dan isi password
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.e(TAG, "Root node is null, cannot input password")
            return
        }
        
        try {
            val success = findAndFillPasswordField(rootNode, password)
            if (success) {
                Log.d(TAG, "Password field filled, will press Enter in 800ms")
                // Setelah password diisi, tekan Enter di keyboard
                handler.postDelayed({
                    pressKeyboardEnter()
                }, 800)
            } else {
                Log.e(TAG, "Could not find password field!")
            }
        } finally {
            // Don't recycle on newer APIs
        }
    }
    
    private fun findAndFillPasswordField(node: AccessibilityNodeInfo, password: String): Boolean {
        // Cek apakah node ini adalah text field yang bisa diisi
        if (node.isEditable && node.isFocusable) {
            Log.d(TAG, "Found editable field, focusing...")
            
            // Focus ke field
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            
            // Set text ke field
            val arguments = android.os.Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, password)
            val result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            
            Log.d(TAG, "Password set result: $result")
            return true
        }
        
        // Cari di child nodes
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndFillPasswordField(child, password)) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Tekan tombol Enter di keyboard virtual
     * Posisi Enter key biasanya di kanan bawah keyboard
     */
    private fun pressKeyboardEnter() {
        Log.d(TAG, "Pressing keyboard Enter key")
        
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // Untuk layar 1080x2400, keyboard biasanya:
        // - Tinggi keyboard sekitar 40% dari layar di bagian bawah
        // - Tombol Enter di pojok kanan bawah
        
        // Posisi Enter key di keyboard QWERTY standar
        val enterX = screenWidth * 0.90f  // Kanan sekali
        val enterY = screenHeight * 0.88f  // Bawah sekali (di area keyboard)
        
        Log.d(TAG, "Tapping Enter at ($enterX, $enterY)")
        performTap(enterX, enterY)
        
        // Fallback: coba lagi dengan posisi sedikit berbeda
        handler.postDelayed({
            // Coba posisi Enter yang lebih umum (tengah kanan bawah keyboard)
            val enterX2 = screenWidth * 0.85f
            val enterY2 = screenHeight * 0.92f
            Log.d(TAG, "Fallback tap at ($enterX2, $enterY2)")
            performTap(enterX2, enterY2)
        }, 300)
    }
    
    /**
     * Cari dan klik tombol submit/OK/Enter
     */
    private fun findAndClickSubmitButton() {
        val rootNode = rootInActiveWindow ?: return
        
        try {
            // Cari tombol dengan berbagai label
            val buttonLabels = listOf("OK", "ok", "Enter", "ENTER", "Submit", "Done", "Confirm", "✓")
            
            for (label in buttonLabels) {
                val buttons = rootNode.findAccessibilityNodeInfosByText(label)
                for (button in buttons) {
                    if (button.isClickable) {
                        button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d(TAG, "Clicked submit button: $label")
                        button.recycle()
                        return
                    }
                    button.recycle()
                }
            }
            
            // Jika tidak ada tombol ditemukan, coba cari по contentDescription
            findClickableByContentDescription(rootNode)
            
        } finally {
            rootNode.recycle()
        }
    }
    
    private fun findClickableByContentDescription(node: AccessibilityNodeInfo): Boolean {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (node.isClickable && (desc.contains("ok") || desc.contains("enter") || desc.contains("submit") || desc.contains("confirm"))) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "Clicked by content description: $desc")
            return true
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findClickableByContentDescription(child)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }
    
    /**
     * Gambar pattern lock
     * Pattern adalah string 1-9 yang merepresentasikan posisi grid 3x3
     * Grid:
     * 1 2 3
     * 4 5 6
     * 7 8 9
     */
    private fun drawPattern(pattern: String) {
        Log.d(TAG, "=== Drawing pattern: $pattern ===")
        
        if (pattern.isEmpty()) {
            Log.e(TAG, "Pattern is empty!")
            return
        }
        
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        Log.d(TAG, "Screen size: ${screenWidth}x${screenHeight}")
        
        // Untuk Poco F5 / MIUI / HyperOS dengan layar 1080x2400 20:9
        // Pattern grid di MIUI biasanya:
        // - Centered horizontally
        // - Berada di sekitar 40-65% dari tinggi layar
        // - Grid size sekitar 60% dari lebar layar (agar tidak keluar layar)
        
        val gridWidth = screenWidth * 0.60f  // 60% lebar layar = 648px
        
        // Center horizontally
        val gridStartX = (screenWidth - gridWidth) / 2f  // = 216px
        
        // Untuk layar 20:9 (2400px), pattern biasanya mulai dari sekitar 38% tinggi
        val gridStartY = screenHeight * 0.38f  // = 912px
        
        // Jarak antar titik = ukuran grid / 2 (ada 3 titik jadi 2 gap)
        val cellSize = gridWidth / 2f  // = 324px
        
        Log.d(TAG, "Grid config for ${screenWidth}x${screenHeight}:")
        Log.d(TAG, "  gridWidth=$gridWidth")
        Log.d(TAG, "  gridStartX=$gridStartX, gridStartY=$gridStartY")
        Log.d(TAG, "  cellSize=$cellSize")
        
        // Fungsi untuk mendapatkan posisi titik berdasarkan angka 1-9
        fun getPointPosition(point: Char): Pair<Float, Float> {
            val num = point.digitToIntOrNull() ?: return Pair(screenWidth / 2f, screenHeight / 2f)
            if (num < 1 || num > 9) return Pair(screenWidth / 2f, screenHeight / 2f)
            
            val index = num - 1 // 0-8
            val row = index / 3  // 0, 1, atau 2
            val col = index % 3  // 0, 1, atau 2
            
            // Posisi titik di tengah cell
            val x = gridStartX + (cellSize * col) + (cellSize / 2f)
            val y = gridStartY + (cellSize * row) + (cellSize / 2f)
            
            return Pair(x, y)
        }
        
        // Log semua posisi grid untuk referensi
        Log.d(TAG, "Pattern grid positions (all 9 dots):")
        for (i in 1..9) {
            val (x, y) = getPointPosition(i.digitToChar())
            Log.d(TAG, "  Dot $i -> ($x, $y)")
        }
        
        // Log posisi pattern yang akan digambar
        Log.d(TAG, "Drawing path for pattern '$pattern':")
        
        // Buat path untuk gesture
        val path = Path()
        
        val (startX, startY) = getPointPosition(pattern[0])
        path.moveTo(startX, startY)
        Log.d(TAG, "  moveTo: (${startX.toInt()}, ${startY.toInt()}) - point ${pattern[0]}")
        
        for (i in 1 until pattern.length) {
            val (x, y) = getPointPosition(pattern[i])
            path.lineTo(x, y)
            Log.d(TAG, "  lineTo: (${x.toInt()}, ${y.toInt()}) - point ${pattern[i]}")
        }
        
        // Duration: lebih lama = lebih akurat
        // 800ms base + 200ms per titik
        val duration = 800L + (pattern.length * 200L)
        Log.d(TAG, "Gesture duration: ${duration}ms for ${pattern.length} points")
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "✓ Pattern gesture completed successfully!")
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e(TAG, "✗ Pattern gesture was CANCELLED!")
            }
        }, null)
    }
    
    fun performTap(x: Float, y: Float) {
        Log.d(TAG, "Performing tap at ($x, $y)")
        
        val path = Path()
        path.moveTo(x, y)
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        
        dispatchGesture(gesture, null, null)
    }
}
