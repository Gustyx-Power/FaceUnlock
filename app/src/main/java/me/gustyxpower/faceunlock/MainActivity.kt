package me.gustyxpower.faceunlock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import me.gustyxpower.faceunlock.manager.CredentialManager
import me.gustyxpower.faceunlock.manager.FaceDataManager
import me.gustyxpower.faceunlock.service.FaceUnlockAccessibilityService
import me.gustyxpower.faceunlock.ui.theme.FaceUnlockTheme

class MainActivity : ComponentActivity() {
    
    private lateinit var faceDataManager: FaceDataManager
    private lateinit var credentialManager: CredentialManager
    
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, getString(R.string.toast_camera_granted), Toast.LENGTH_SHORT).show()
        }
    }
    
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        faceDataManager = FaceDataManager(this)
        credentialManager = CredentialManager(this)
        
        setContent {
            FaceUnlockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        faceDataManager = faceDataManager,
                        credentialManager = credentialManager,
                        onEnrollFace = { enrollFace() },
                        onDeleteFace = { deleteFace() },
                        onOpenAccessibility = { openAccessibilitySettings() },
                        onRequestOverlay = { requestOverlayPermission() },
                        hasCameraPermission = { hasCameraPermission() },
                        hasOverlayPermission = { Settings.canDrawOverlays(this) },
                        isAccessibilityEnabled = { isAccessibilityServiceEnabled() }
                    )
                }
            }
        }
        
        if (!hasCameraPermission()) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun enrollFace() {
        if (hasCameraPermission()) {
            startActivity(Intent(this, EnrollFaceActivity::class.java))
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    private fun deleteFace() {
        faceDataManager.deleteFaceData()
        Toast.makeText(this, getString(R.string.toast_face_deleted), Toast.LENGTH_SHORT).show()
    }
    
    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        Toast.makeText(this, getString(R.string.toast_find_face_unlock), Toast.LENGTH_LONG).show()
    }
    
    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "${packageName}/${FaceUnlockAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(serviceName)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    faceDataManager: FaceDataManager,
    credentialManager: CredentialManager,
    onEnrollFace: () -> Unit,
    onDeleteFace: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit,
    hasCameraPermission: () -> Boolean,
    hasOverlayPermission: () -> Boolean,
    isAccessibilityEnabled: () -> Boolean
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // States
    var hasFace by remember { mutableStateOf(faceDataManager.hasFaceData()) }
    var hasAccessibility by remember { mutableStateOf(isAccessibilityEnabled()) }
    var hasOverlay by remember { mutableStateOf(hasOverlayPermission()) }
    var isEnabled by remember { mutableStateOf(faceDataManager.isEnabled()) }
    var lockType by remember { mutableStateOf(credentialManager.getLockType()) }
    var hasCredential by remember { mutableStateOf(false) }
    
    // Credential input states
    var credentialInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var selectedLockType by remember { mutableStateOf(CredentialManager.LockType.PIN) }
    
    // Auto-refresh states when app resumes
    // Using key to trigger refresh
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val activity = context as? ComponentActivity
    
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose {
            activity?.lifecycle?.removeObserver(observer)
        }
    }
    
    // Refresh states when trigger changes
    LaunchedEffect(refreshTrigger) {
        hasFace = faceDataManager.hasFaceData()
        hasAccessibility = isAccessibilityEnabled()
        hasOverlay = hasOverlayPermission()
        isEnabled = faceDataManager.isEnabled()
        lockType = credentialManager.getLockType()
        hasCredential = when (lockType) {
            CredentialManager.LockType.PIN, CredentialManager.LockType.PASSWORD -> credentialManager.hasCredential()
            else -> false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.main_title),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Dev GustyxPower",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val statusText = when {
                        !hasFace -> stringResource(R.string.status_register_face)
                        !hasAccessibility -> stringResource(R.string.status_enable_accessibility)
                        !hasOverlay -> stringResource(R.string.status_enable_overlay)
                        !isEnabled -> stringResource(R.string.status_enable_face_unlock)
                        else -> "🎉 ${stringResource(R.string.status_active)}"
                    }
                    
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.switch_enable),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { checked ->
                                faceDataManager.setEnabled(checked)
                                isEnabled = checked
                            },
                            enabled = hasFace && hasAccessibility && hasOverlay
                        )
                    }
                }
            }
            
            // Face Data Card
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Face,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.section_face_data),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (hasFace) Icons.Filled.CheckCircle else Icons.Outlined.Cancel,
                            contentDescription = null,
                            tint = if (hasFace) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasFace) stringResource(R.string.face_registered) else stringResource(R.string.face_not_registered),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onEnrollFace() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_register_face))
                        }
                        
                        OutlinedButton(
                            onClick = {
                                onDeleteFace()
                                hasFace = faceDataManager.hasFaceData()
                            },
                            enabled = hasFace
                        ) {
                            Text(stringResource(R.string.btn_delete))
                        }
                    }
                }
            }
            
            // Credentials Card
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.section_credentials),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.credentials_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (hasCredential) Icons.Filled.CheckCircle else Icons.Outlined.Cancel,
                            contentDescription = null,
                            tint = if (hasCredential) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasCredential) 
                                stringResource(R.string.credentials_saved, lockType.name) 
                            else 
                                stringResource(R.string.credentials_not_saved),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Lock Type Selection
                    Row(
                        modifier = Modifier.fillMaxWidth().selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedLockType == CredentialManager.LockType.PIN,
                            onClick = { 
                                selectedLockType = CredentialManager.LockType.PIN
                                credentialInput = ""
                            },
                            label = { Text("PIN") },
                            modifier = Modifier.weight(1f),
                            leadingIcon = if (selectedLockType == CredentialManager.LockType.PIN) {
                                { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = selectedLockType == CredentialManager.LockType.PASSWORD,
                            onClick = { 
                                selectedLockType = CredentialManager.LockType.PASSWORD
                                credentialInput = ""
                            },
                            label = { Text("Password") },
                            modifier = Modifier.weight(1f),
                            leadingIcon = if (selectedLockType == CredentialManager.LockType.PASSWORD) {
                                { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }
                            } else null
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Input Field
                    OutlinedTextField(
                        value = credentialInput,
                        onValueChange = { 
                            credentialInput = if (selectedLockType == CredentialManager.LockType.PIN) {
                                it.filter { c -> c.isDigit() }
                            } else it
                        },
                        label = { 
                            Text(
                                if (selectedLockType == CredentialManager.LockType.PIN) 
                                    stringResource(R.string.hint_pin) 
                                else 
                                    stringResource(R.string.hint_password)
                            ) 
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (selectedLockType == CredentialManager.LockType.PIN) 
                                KeyboardType.NumberPassword 
                            else 
                                KeyboardType.Password
                        ),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (credentialInput.length >= 4) {
                                    credentialManager.saveCredential(credentialInput, selectedLockType)
                                    val msg = if (selectedLockType == CredentialManager.LockType.PIN) 
                                        context.getString(R.string.toast_pin_saved) 
                                    else 
                                        context.getString(R.string.toast_password_saved)
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    credentialInput = ""
                                    hasCredential = true
                                    lockType = selectedLockType
                                } else {
                                    val msg = if (selectedLockType == CredentialManager.LockType.PIN) 
                                        context.getString(R.string.toast_pin_min) 
                                    else 
                                        context.getString(R.string.toast_password_min)
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Save, null, Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_save))
                        }
                        
                        OutlinedButton(
                            onClick = {
                                credentialManager.clearAll()
                                credentialInput = ""
                                hasCredential = false
                                lockType = CredentialManager.LockType.NONE
                                Toast.makeText(context, context.getString(R.string.toast_credentials_cleared), Toast.LENGTH_SHORT).show()
                            },
                            enabled = hasCredential
                        ) {
                            Text(stringResource(R.string.btn_clear))
                        }
                    }
                }
            }
            
            // Permissions Card
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.section_permissions),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Accessibility
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (hasAccessibility) Icons.Filled.CheckCircle else Icons.Outlined.Error,
                            contentDescription = null,
                            tint = if (hasAccessibility) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasAccessibility) stringResource(R.string.accessibility_active) else stringResource(R.string.accessibility_inactive),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { onOpenAccessibility() }) {
                            Text(stringResource(R.string.btn_enable))
                        }
                    }
                    
                    // Overlay
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (hasOverlay) Icons.Filled.CheckCircle else Icons.Outlined.Error,
                            contentDescription = null,
                            tint = if (hasOverlay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasOverlay) stringResource(R.string.overlay_granted) else stringResource(R.string.overlay_needed),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { onRequestOverlay() }) {
                            Text(stringResource(R.string.btn_allow))
                        }
                    }
                }
            }
            
            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.section_info),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    listOf(
                        stringResource(R.string.info_1),
                        stringResource(R.string.info_2),
                        stringResource(R.string.info_3)
                    ).forEach { info ->
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = info,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
