package com.codestersunion.stickerprinter

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.codestersunion.stickerprinter.ui.theme.GrassGreen
import com.codestersunion.stickerprinter.ui.theme.MagicPurple
import com.codestersunion.stickerprinter.ui.theme.OceanBlue
import com.codestersunion.stickerprinter.ui.theme.PlayfulCoral
import com.codestersunion.stickerprinter.ui.theme.PlayfulCoralLight
import com.codestersunion.stickerprinter.ui.theme.SkyBlue
import com.codestersunion.stickerprinter.ui.theme.StickerPrinterTheme
import com.codestersunion.stickerprinter.ui.theme.SunnyYellow
import com.codestersunion.stickerprinter.ui.theme.SunnyYellowLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Screen states for the kid-friendly flow
enum class AppScreen {
    HOME,       // Microphone button front and center
    RECORDING,  // Recording in progress with visual feedback
    GENERATING, // Generating the image
    RESULT      // Show generated image with print/back buttons
}

data class BluetoothDeviceInfo(
    val device: BluetoothDevice,
    val isConnected: Boolean,
    val connectedProfiles: List<String>
)

object BluetoothHelper {
    fun getBluetoothDevicesWithStatus(context: Context): List<BluetoothDeviceInfo> {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            return emptyList()
        }

        return try {
            val bondedDevices = bluetoothAdapter.bondedDevices
            bondedDevices.map { device ->
                val (isConnected, profiles) = checkDeviceConnectionStatus(context, device, bluetoothAdapter)
                BluetoothDeviceInfo(
                    device = device,
                    isConnected = isConnected,
                    connectedProfiles = profiles
                )
            }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun checkDeviceConnectionStatus(
        context: Context,
        device: BluetoothDevice,
        adapter: BluetoothAdapter
    ): Pair<Boolean, List<String>> {
        val connectedProfiles = mutableListOf<String>()
        var isConnected = false
        val profileResults = mutableMapOf<Int, Boolean>()

        val lock = Object()

        fun checkProfile(profileType: Int, profileName: String) {
            try {
                adapter.getProfileProxy(
                    context,
                    object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                            synchronized(lock) {
                                try {
                                    val connectionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        proxy.getConnectionState(device)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        proxy.getConnectionState(device)
                                    }
                                    
                                    if (connectionState == BluetoothProfile.STATE_CONNECTED) {
                                        connectedProfiles.add(profileName)
                                        isConnected = true
                                        profileResults[profile] = true
                                    } else {
                                        profileResults[profile] = false
                                    }
                                    
                                    val connectedDevices = proxy.connectedDevices
                                    if (connectedDevices.any { it.address == device.address }) {
                                        if (!connectedProfiles.contains(profileName)) {
                                            connectedProfiles.add(profileName)
                                        }
                                        isConnected = true
                                    }
                                } catch (e: Exception) {
                                    // Error checking connection state
                                } finally {
                                    try {
                                        adapter.closeProfileProxy(profile, proxy)
                                    } catch (e: Exception) {
                                        // Error closing proxy
                                    }
                                }
                            }
                        }

                        override fun onServiceDisconnected(profile: Int) {
                            synchronized(lock) {
                                profileResults[profile] = false
                            }
                        }
                    },
                    profileType
                )
            } catch (e: Exception) {
                synchronized(lock) {
                    profileResults[profileType] = false
                }
            }
        }

        try {
            checkProfile(BluetoothProfile.HEADSET, "HFP")
            checkProfile(BluetoothProfile.A2DP, "A2DP")
            Thread.sleep(500)
        } catch (e: SecurityException) {
            // Permission denied
        } catch (e: Exception) {
            // Other errors
        }

        return Pair(isConnected, connectedProfiles)
    }
}

class MainActivity : ComponentActivity() {
    
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminName: ComponentName
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminName = ComponentName(this, AdminReceiver::class.java)
        
        if (dpm.isDeviceOwnerApp(packageName)) {
            setupKioskMode()
        }
        
        setContent {
            StickerPrinterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KidFriendlyApp()
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Hide the system UI (Status bar and Nav bar)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // Hide nav bar
            or View.SYSTEM_UI_FLAG_FULLSCREEN // Hide status bar
        )
        
        // Re-engage Lock Task if the app was paused/resumed
        if (dpm.isDeviceOwnerApp(packageName)) {
            startLockTask()
        }
    }
    
    private fun setupKioskMode() {
        // 1. Whitelist our app for Lock Task (removes "Unpin" dialogs)
        // We also whitelist Settings so we can launch Wi-Fi if needed
        val packages = arrayOf(packageName, "com.android.settings")
        dpm.setLockTaskPackages(adminName, packages)
        
        // 2. Set User Restrictions (Disable Safe Mode, Volume, etc)
        // This effectively "hardens" the device
        val restrictions = arrayOf(
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
            UserManager.DISALLOW_ADJUST_VOLUME,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_USB_FILE_TRANSFER
        )
        
        for (restriction in restrictions) {
            dpm.addUserRestriction(adminName, restriction)
        }
        
        // 3. Disable the Keyguard (Lock Screen) so it boots straight to app
        dpm.setKeyguardDisabled(adminName, true)
        
        // 4. Start the Lock!
        startLockTask()
    }
    
    fun stopKioskMode() {
        stopLockTask()
        
        // Optional: Re-enable the status bar functionality if needed
        dpm.clearUserRestriction(adminName, UserManager.DISALLOW_ADJUST_VOLUME)
        
        // Close the app or go to settings
        finish()
    }
}

@Composable
fun KidFriendlyApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // App state
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var generatedImage by remember { mutableStateOf<Bitmap?>(null) }
    var isPrinting by remember { mutableStateOf(false) }
    var selectedDeviceAddress by remember { mutableStateOf<String?>(null) }
    var hasBluetoothPermission by remember { mutableStateOf(false) }
    
    // Audio recording state
    val audioRecorder = remember { AudioRecorder(context) }
    var recordingJob: kotlinx.coroutines.Job? by remember { mutableStateOf(null) }
    var audioAmplitude by remember { mutableFloatStateOf(0f) }
    
    // Load saved printer selection or auto-select on launch
    LaunchedEffect(hasBluetoothPermission) {
        if (hasBluetoothPermission) {
            // First try to load saved preference
            val savedAddress = PrinterPreferences.getSelectedDeviceAddress(context)
            if (savedAddress != null) {
                selectedDeviceAddress = savedAddress
            } else {
                // If no saved preference, auto-select
                withContext(Dispatchers.IO) {
                    val devices = BluetoothHelper.getBluetoothDevicesWithStatus(context)
                    withContext(Dispatchers.Main) {
                        // Auto-select first device (or any Phomemo device if found)
                        val phomemoDevice = devices.find { it.device.name?.contains("Phomemo", ignoreCase = true) == true }
                        val address = phomemoDevice?.device?.address ?: devices.firstOrNull()?.device?.address
                        selectedDeviceAddress = address
                        // Save the auto-selected device
                        address?.let { PrinterPreferences.setSelectedDeviceAddress(context, it) }
                    }
                }
            }
        }
    }
    
    // Permission handling
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasBluetoothPermission = permissions.all { it.value }
    }
    
    LaunchedEffect(Unit) {
        val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }
        
        val allGranted = bluetoothPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        
        if (allGranted) {
            hasBluetoothPermission = true
        } else {
            permissionLauncher.launch(bluetoothPermissions)
        }
    }
    
    // Functions for recording and generating
    val processAudio: (File) -> Unit = { file ->
        currentScreen = AppScreen.GENERATING
        coroutineScope.launch {
            try {
                // Wait for audio to fully stop before generating image
                delay(1000)
                
                val transcribedText = ColoringBookGenerator.transcribeAudio(
                    apiKey = BuildConfig.GOOGLE_API_KEY,
                    audioFile = file
                )
                
                if (!transcribedText.isNullOrEmpty()) {
                    // Generate the image
                    val bitmap = ColoringBookGenerator.generateColoringPage(
                        apiKey = BuildConfig.GOOGLE_API_KEY,
                        userPrompt = transcribedText
                    )
                    
                    if (bitmap != null) {
                        generatedImage = bitmap
                        currentScreen = AppScreen.RESULT
                    } else {
                        snackbarHostState.showSnackbar("Oops! Couldn't create the picture. Try again!")
                        currentScreen = AppScreen.HOME
                    }
                } else {
                    snackbarHostState.showSnackbar("I couldn't hear you. Try speaking louder!")
                    currentScreen = AppScreen.HOME
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Something went wrong. Let's try again!")
                currentScreen = AppScreen.HOME
            } finally {
                file.delete()
            }
        }
    }
    
    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val file = audioRecorder.startRecording()
            if (file != null) {
                currentScreen = AppScreen.RECORDING
                
                recordingJob = coroutineScope.launch {
                    var silenceStartTime = 0L
                    val silenceThreshold = 2500L
                    val amplitudeThreshold = 2000
                    
                    try {
                        while (currentScreen == AppScreen.RECORDING) {
                            val amplitude = audioRecorder.getAmplitude()
                            // Normalize amplitude for visual display (0-1 range)
                            audioAmplitude = (amplitude / 32767f).coerceIn(0f, 1f)
                            
                            if (amplitude < amplitudeThreshold) {
                                if (silenceStartTime == 0L) {
                                    silenceStartTime = System.currentTimeMillis()
                                } else {
                                    val silenceDuration = System.currentTimeMillis() - silenceStartTime
                                    if (silenceDuration > silenceThreshold) {
                                        val recordedFile = audioRecorder.stopRecording()
                                        if (recordedFile != null) {
                                            processAudio(recordedFile)
                                        }
                                        break
                                    }
                                }
                            } else {
                                silenceStartTime = 0L
                            }
                            delay(100)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Couldn't start listening. Try again!")
                }
            }
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }
    
    fun stopRecording() {
        recordingJob?.cancel()
        val file = audioRecorder.stopRecording()
        if (file != null) {
            processAudio(file)
        } else {
            currentScreen = AppScreen.HOME
        }
    }
    
    // Main UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SkyBlue.copy(alpha = 0.3f),
                        SunnyYellowLight.copy(alpha = 0.2f),
                        MagicPurple.copy(alpha = 0.2f)
                    )
                )
            )
    ) {
        // Settings button in top right
        IconButton(
            onClick = {
                val intent = android.content.Intent(context, SettingsActivity::class.java)
                intent.putExtra("device_address", selectedDeviceAddress ?: PrinterPreferences.getSelectedDeviceAddress(context))
                context.startActivity(intent)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .padding(top = 32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.size(28.dp)
            )
        }
        
        // Content based on current screen
        when (currentScreen) {
            AppScreen.HOME -> HomeScreen(
                onMicrophoneClick = { startRecording() }
            )
            
            AppScreen.RECORDING -> RecordingScreen(
                amplitude = audioAmplitude,
                onStopClick = { stopRecording() }
            )
            
            AppScreen.GENERATING -> GeneratingScreen()
            
            AppScreen.RESULT -> ResultScreen(
                image = generatedImage,
                isPrinting = isPrinting,
                onPrintClick = {
                    if (selectedDeviceAddress != null && generatedImage != null) {
                        isPrinting = true
                        coroutineScope.launch {
                            val result = PrinterHelper.printImage(selectedDeviceAddress!!, generatedImage!!)
                            isPrinting = false
                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar("Sticker printed! 🎉")
                            } else {
                                snackbarHostState.showSnackbar("Printing didn't work. Try again!")
                            }
                        }
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("No printer found. Check settings!")
                        }
                    }
                },
                onBackClick = {
                    generatedImage = null
                    currentScreen = AppScreen.HOME
                }
            )
        }
        
        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

/**
 * Calculates a responsive text size based on screen height.
 * Uses a reference height of 800dp (typical for larger phones/tablets).
 * Scales down proportionally for smaller screens like Nexus 5 (640dp).
 */
@Composable
fun responsiveTextSize(baseSize: TextUnit): TextUnit {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightDp = configuration.screenHeightDp.toFloat()
    
    // Reference height: 800dp (typical for larger phones)
    // Minimum scale: 0.75 (for very small screens like Nexus 5)
    // Maximum scale: 1.0 (for larger screens)
    val scaleFactor = (screenHeightDp / 800f).coerceIn(0.75f, 1.0f)
    
    return with(density) {
        (baseSize.value * scaleFactor).sp
    }
}

@Composable
fun HomeScreen(onMicrophoneClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(150),
        label = "scale"
    )
    
    // Pulsing animation for the mic button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    // Calculate responsive text sizes
    val titleTextSize = responsiveTextSize(MaterialTheme.typography.displaySmall.fontSize)
    val subtitleTextSize = responsiveTextSize(MaterialTheme.typography.titleLarge.fontSize)
    val hintTextSize = responsiveTextSize(MaterialTheme.typography.headlineMedium.fontSize)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Title with fun styling
        Text(
            text = "✨ Sticker Magic! ✨",
            style = MaterialTheme.typography.displaySmall.copy(fontSize = titleTextSize),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Tap the microphone and tell me\nwhat sticker you want!",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = subtitleTextSize),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(64.dp))
        
        // Giant microphone button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.scale(pulseScale * scale)
        ) {
            // Outer glow ring
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                PlayfulCoral.copy(alpha = 0.4f),
                                PlayfulCoral.copy(alpha = 0f)
                            )
                        ),
                        shape = CircleShape
                    )
            )
            
            // Main button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(180.dp)
                    .shadow(12.dp, CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(PlayfulCoral, PlayfulCoralLight)
                        ),
                        shape = CircleShape
                    )
                    .border(4.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onMicrophoneClick
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Start Recording",
                    tint = Color.White,
                    modifier = Modifier.size(80.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Hint text with arrow
        Text(
            text = "👆 Tap me!",
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = hintTextSize),
            color = PlayfulCoral
        )
    }
}

@Composable
fun RecordingScreen(
    amplitude: Float,
    onStopClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording")
    
    // Multiple animated rings
    val ring1Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1"
    )
    
    val ring2Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, delayMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2"
    )
    
    val ring3Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, delayMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring3"
    )
    
    // Amplitude-based scale
    val amplitudeScale by animateFloatAsState(
        targetValue = 1f + (amplitude * 0.3f),
        animationSpec = tween(100),
        label = "amplitude"
    )
    
    // Calculate responsive text sizes
    val titleTextSize = responsiveTextSize(MaterialTheme.typography.displaySmall.fontSize)
    val subtitleTextSize = responsiveTextSize(MaterialTheme.typography.titleLarge.fontSize)
    val hintTextSize = responsiveTextSize(MaterialTheme.typography.headlineMedium.fontSize)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Listening text with animation
        Text(
            text = "🎤 I'm listening!",
            style = MaterialTheme.typography.displaySmall.copy(fontSize = titleTextSize),
            color = GrassGreen,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Tell me what sticker\nyou want to make...",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = subtitleTextSize),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(64.dp))
        
        // Recording visualization
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(250.dp)
        ) {
            // Animated rings
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(ring1Scale)
                    .background(
                        color = GrassGreen.copy(alpha = 0.3f / ring1Scale),
                        shape = CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(ring2Scale)
                    .background(
                        color = SkyBlue.copy(alpha = 0.3f / ring2Scale),
                        shape = CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(ring3Scale)
                    .background(
                        color = MagicPurple.copy(alpha = 0.3f / ring3Scale),
                        shape = CircleShape
                    )
            )
            
            // Main recording button (amplitude reactive)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .scale(amplitudeScale)
                    .shadow(8.dp, CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(GrassGreen, SkyBlue)
                        ),
                        shape = CircleShape
                    )
                    .border(4.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                    .clickable(onClick = onStopClick)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Recording",
                    tint = Color.White,
                    modifier = Modifier.size(70.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "Tap when you're done!",
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = hintTextSize),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun GeneratingScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "generating")
    
    // Bouncing dots
    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -20f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    
    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -20f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    
    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -20f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )
    
    // Calculate responsive text sizes
    val titleTextSize = responsiveTextSize(MaterialTheme.typography.displaySmall.fontSize)
    val subtitleTextSize = responsiveTextSize(MaterialTheme.typography.titleLarge.fontSize)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🪄 Creating Magic!",
            style = MaterialTheme.typography.displaySmall.copy(fontSize = titleTextSize),
            color = MagicPurple,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Making your sticker...",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = subtitleTextSize),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(64.dp))
        
        // Fun loading animation
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(180.dp),
                strokeWidth = 8.dp,
                color = MagicPurple,
                trackColor = MagicPurple.copy(alpha = 0.2f)
            )
            
            // Center sparkle emoji
            Text(
                text = "✨",
                style = MaterialTheme.typography.displayLarge
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Bouncing dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .offset(y = dot1Offset.dp)
                    .background(SunnyYellow, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .offset(y = dot2Offset.dp)
                    .background(PlayfulCoral, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .offset(y = dot3Offset.dp)
                    .background(OceanBlue, CircleShape)
            )
        }
    }
}

@Composable
fun ResultScreen(
    image: Bitmap?,
    isPrinting: Boolean,
    onPrintClick: () -> Unit,
    onBackClick: () -> Unit
) {
    // Calculate responsive text sizes
    val titleTextSize = responsiveTextSize(MaterialTheme.typography.displaySmall.fontSize)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        // Title
        Text(
            text = "🎨 Your Sticker!",
            style = MaterialTheme.typography.displaySmall.copy(fontSize = titleTextSize),
            color = OceanBlue,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Generated image
        if (image != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "Your generated sticker",
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(600f / 800f)
                        .shadow(16.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                )
            }
        }
        
        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            ActionButton(
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Try Again",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                },
                label = "Try Again",
                backgroundColor = SkyBlue,
                enabled = !isPrinting,
                onClick = onBackClick
            )
            
            // Print button
            ActionButton(
                icon = {
                    if (isPrinting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = Color.White,
                            strokeWidth = 4.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Print Sticker",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                },
                label = if (isPrinting) "Printing..." else "Print!",
                backgroundColor = GrassGreen,
                enabled = !isPrinting,
                onClick = onPrintClick
            )
        }
    }
}

@Composable
fun ActionButton(
    icon: @Composable () -> Unit,
    label: String,
    backgroundColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(100),
        label = "buttonScale"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .scale(scale)
                .size(100.dp)
                .shadow(8.dp, CircleShape)
                .background(
                    color = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.5f),
                    shape = CircleShape
                )
                .border(3.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                )
        ) {
            icon()
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    StickerPrinterTheme {
        HomeScreen(onMicrophoneClick = {})
    }
}

@Preview(showBackground = true)
@Composable
fun RecordingScreenPreview() {
    StickerPrinterTheme {
        RecordingScreen(amplitude = 0.5f, onStopClick = {})
    }
}

@Preview(showBackground = true)
@Composable
fun GeneratingScreenPreview() {
    StickerPrinterTheme {
        GeneratingScreen()
    }
}
