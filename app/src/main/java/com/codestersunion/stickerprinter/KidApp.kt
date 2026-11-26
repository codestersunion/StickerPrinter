package com.codestersunion.stickerprinter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Screen states for the kid-friendly flow
enum class AppScreen {
    HOME,       // Microphone button front and center
    RECORDING,  // Recording in progress with visual feedback
    GENERATING, // Understanding the request and drawing the picture
    RESULT,     // Show generated image with print/again/new buttons
    GALLERY     // Recent stickers that can be printed again
}

/** Ideas used by the "Surprise me!" button and the rotating "Try saying..." hint.
 *  Emoji are limited to ones old Android versions (API 25) can draw. */
private val STICKER_IDEAS = listOf(
    "a dinosaur riding a skateboard" to "🦎",
    "a cat astronaut floating in space" to "🐱",
    "a unicorn eating a giant slice of pizza" to "🦄",
    "a robot baking cupcakes" to "🤖",
    "a puppy wearing a superhero cape" to "🐶",
    "a dragon having a tea party" to "🐉",
    "an octopus playing the drums" to "🐙",
    "a penguin building a snowman" to "🐧",
    "a monkey driving a race car" to "🐵",
    "a turtle with a castle on its back" to "🐢",
    "a bunny flying a hot air balloon" to "🐰",
    "a happy whale with a rainbow spout" to "🐳",
    "a lion wearing a crown and sunglasses" to "🦁",
    "a mermaid riding a dolphin" to "🐬",
    "an owl reading a big book" to "🦉",
    "a bear eating ice cream on the beach" to "🐻",
    "a frog prince on a lily pad" to "🐸",
    "a fire truck with a smiling face" to "🚒",
    "a pirate ship full of cats" to "⛵",
    "a sloth hanging from a candy cane" to "🍭"
)

private const val SETTINGS_HOLD_MS = 3000

@Composable
fun KidFriendlyApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current

    // App state
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var generatedImage by remember { mutableStateOf<Bitmap?>(null) }
    var currentIdea by remember { mutableStateOf<StickerIdea?>(null) }
    var isPrinting by remember { mutableStateOf(false) }
    var selectedDeviceAddress by remember { mutableStateOf<String?>(null) }
    var hasBluetoothPermission by remember { mutableStateOf(false) }

    // Gallery navigation: where "Back" returns to, which sticker is open, and a counter
    // bumped whenever a new sticker is saved so the grid reloads.
    var galleryReturnScreen by remember { mutableStateOf(AppScreen.HOME) }
    var gallerySelected by remember { mutableStateOf<SavedSticker?>(null) }
    var historyVersion by remember { mutableIntStateOf(0) }
    // The transcribe/understand/draw work in flight, so Back can cancel it.
    var workJob: Job? by remember { mutableStateOf(null) }

    // Audio recording state
    val audioRecorder = remember { AudioRecorder(context) }
    var recordingJob: Job? by remember { mutableStateOf(null) }
    var audioAmplitude by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        onDispose { audioRecorder.release() }
    }

    /** Show a short friendly message, replacing any message already on screen. */
    fun tell(message: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    // Load saved printer selection or auto-select on launch
    LaunchedEffect(hasBluetoothPermission) {
        if (hasBluetoothPermission) {
            val savedAddress = PrinterPreferences.getSelectedDeviceAddress(context)
            if (savedAddress != null) {
                selectedDeviceAddress = savedAddress
            } else {
                val devices = withContext(Dispatchers.IO) { BluetoothHelper.getBluetoothDevicesWithStatus(context) }
                val phomemoDevice = devices.find { it.device.name?.contains("Phomemo", ignoreCase = true) == true }
                val address = phomemoDevice?.device?.address ?: devices.firstOrNull()?.device?.address
                selectedDeviceAddress = address
                address?.let { PrinterPreferences.setSelectedDeviceAddress(context, it) }
            }
        }
    }

    // Bluetooth permission handling
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
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
        if (allGranted) hasBluetoothPermission = true else bluetoothPermissionLauncher.launch(bluetoothPermissions)
    }

    fun goHome() {
        recordingJob?.cancel()
        currentScreen = AppScreen.HOME
    }

    /** Background work ended without a sticker: go home, unless the kid is browsing the gallery. */
    fun abandonWork() {
        if (currentScreen == AppScreen.GALLERY) galleryReturnScreen = AppScreen.HOME else goHome()
    }

    fun handleFailure(e: Exception) {
        Log.e("KidFriendlyApp", "Sticker flow failed", e)
        if (e is GeminiApiException) {
            tell("The picture maker is taking a break. Ask a grown-up to check it!")
        } else {
            tell("Oops! Something went wrong. Let's try again!")
        }
        abandonWork()
    }

    /** Draws [idea]; each call picks a new art style, so "Draw again" gives a fresh take. */
    fun generate(idea: StickerIdea) {
        currentIdea = idea
        currentScreen = AppScreen.GENERATING
        workJob = coroutineScope.launch {
            try {
                val bitmap = ColoringBookGenerator.generateColoringPage(
                    apiKey = BuildConfig.GOOGLE_API_KEY,
                    userPrompt = idea.subject
                )
                if (bitmap != null) {
                    generatedImage = bitmap
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    when {
                        currentScreen == AppScreen.GENERATING -> currentScreen = AppScreen.RESULT
                        // Kid peeked at the gallery while waiting: Back will show the new sticker.
                        currentScreen == AppScreen.GALLERY && galleryReturnScreen == AppScreen.GENERATING ->
                            galleryReturnScreen = AppScreen.RESULT
                    }
                    StickerHistory.save(context, bitmap, idea.subject, idea.title)
                    historyVersion++
                } else {
                    tell("Oops! I couldn't draw that one. Let's try again!")
                    abandonWork()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFailure(e)
            }
        }
    }

    val processAudio: (File) -> Unit = { file ->
        currentScreen = AppScreen.GENERATING
        currentIdea = null
        workJob = coroutineScope.launch {
            try {
                val transcribedText = ColoringBookGenerator.transcribeAudio(
                    apiKey = BuildConfig.GOOGLE_API_KEY,
                    audioFile = file
                )
                if (transcribedText.isNullOrBlank()) {
                    tell("I couldn't hear you. Tap the microphone and try again!")
                    abandonWork()
                    return@launch
                }

                generate(StickerIdea(subject = transcribedText.trim()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFailure(e)
            } finally {
                file.delete()
            }
        }
    }

    fun startRecording() {
        val file = audioRecorder.startRecording()
        if (file == null) {
            tell("I couldn't start listening. Try again!")
            return
        }
        currentScreen = AppScreen.RECORDING
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

        recordingJob = coroutineScope.launch {
            val startTime = System.currentTimeMillis()
            var heardSpeech = false
            var silenceStartTime = 0L
            // Little kids often pause before they start talking, so give them time to
            // start, then stop shortly after they finish.
            val waitForSpeechMs = 7000L
            val silenceAfterSpeechMs = 2500L
            val maxRecordingMs = 15000L
            val amplitudeThreshold = 2000

            while (currentScreen == AppScreen.RECORDING) {
                val amplitude = audioRecorder.getAmplitude()
                audioAmplitude = (amplitude / 32767f).coerceIn(0f, 1f)
                val now = System.currentTimeMillis()
                val elapsed = now - startTime

                if (amplitude >= amplitudeThreshold) {
                    heardSpeech = true
                    silenceStartTime = 0L
                } else if (silenceStartTime == 0L) {
                    silenceStartTime = now
                }

                val silentFor = if (silenceStartTime == 0L) 0L else now - silenceStartTime
                when {
                    !heardSpeech && elapsed > waitForSpeechMs -> {
                        // Nothing said: skip the AI call entirely.
                        audioRecorder.stopRecording()?.delete()
                        tell("I didn't hear anything. Tap the microphone and tell me your idea!")
                        currentScreen = AppScreen.HOME
                        break
                    }
                    (heardSpeech && silentFor > silenceAfterSpeechMs) || elapsed > maxRecordingMs -> {
                        audioRecorder.stopRecording()?.let { processAudio(it) } ?: goHome()
                        break
                    }
                }
                delay(100)
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording() else tell("Ask a grown-up to let me use the microphone.")
    }

    fun onMicTapped() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stopRecording() {
        recordingJob?.cancel()
        val file = audioRecorder.stopRecording()
        if (file != null) processAudio(file) else goHome()
    }

    fun surpriseMe() {
        val (subject, _) = STICKER_IDEAS.random()
        generate(StickerIdea(subject = subject))
    }

    fun print(bitmap: Bitmap) {
        val address = selectedDeviceAddress
        if (address == null) {
            tell("I can't find the printer. Ask a grown-up to check it!")
            return
        }
        if (isPrinting) return
        isPrinting = true
        coroutineScope.launch {
            val result = PrinterHelper.printImage(address, bitmap)
            isPrinting = false
            if (result.isSuccess) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                tell("Your sticker is ready! 🎉")
            } else {
                tell("The printer didn't work. Is it turned on? Try again!")
            }
        }
    }

    fun cancelRecording() {
        recordingJob?.cancel()
        audioRecorder.stopRecording()?.delete()
    }

    fun openGallery() {
        if (currentScreen == AppScreen.RECORDING) cancelRecording()
        galleryReturnScreen = when (currentScreen) {
            AppScreen.RESULT, AppScreen.GENERATING -> currentScreen
            else -> AppScreen.HOME
        }
        gallerySelected = null
        currentScreen = AppScreen.GALLERY
    }

    fun goBack() {
        when (currentScreen) {
            AppScreen.HOME -> Unit
            AppScreen.RECORDING -> {
                cancelRecording()
                currentScreen = AppScreen.HOME
            }
            AppScreen.GENERATING -> {
                workJob?.cancel()
                currentScreen = AppScreen.HOME
            }
            AppScreen.RESULT -> currentScreen = AppScreen.HOME
            AppScreen.GALLERY ->
                if (gallerySelected != null) gallerySelected = null else currentScreen = galleryReturnScreen
        }
    }

    // Hardware/gesture back does the same as the on-screen Back button instead of leaving the app.
    BackHandler(enabled = currentScreen != AppScreen.HOME && !isPrinting) { goBack() }

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
      Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            showBack = currentScreen != AppScreen.HOME,
            showGallery = currentScreen != AppScreen.GALLERY,
            enabled = !isPrinting,
            onBack = { goBack() },
            onGallery = { openGallery() },
            trailing = {
                // Settings: grown-ups must hold the gear for a few seconds so kids don't wander in.
                if (currentScreen == AppScreen.HOME) {
                    HoldToOpenButton(
                        holdMillis = SETTINGS_HOLD_MS,
                        onShortTap = { tell("Grown-ups: hold the gear for 3 seconds") },
                        onOpen = {
                            val intent = Intent(context, SettingsActivity::class.java)
                            intent.putExtra("device_address", selectedDeviceAddress ?: PrinterPreferences.getSelectedDeviceAddress(context))
                            context.startActivity(intent)
                        }
                    )
                }
            }
        )

        Box(modifier = Modifier.weight(1f)) {
        when (currentScreen) {
            AppScreen.HOME -> HomeScreen(
                onMicrophoneClick = { onMicTapped() },
                onSurpriseClick = { surpriseMe() }
            )

            AppScreen.RECORDING -> RecordingScreen(
                amplitude = audioAmplitude,
                onStopClick = { stopRecording() }
            )

            AppScreen.GENERATING -> GeneratingScreen(idea = currentIdea)

            AppScreen.RESULT -> ResultScreen(
                image = generatedImage,
                caption = currentIdea?.title ?: currentIdea?.subject,
                isPrinting = isPrinting,
                onPrintClick = { generatedImage?.let { print(it) } },
                onAgainClick = { currentIdea?.let { generate(it) } },
                onNewClick = {
                    generatedImage = null
                    goHome()
                }
            )

            AppScreen.GALLERY -> GalleryScreen(
                isPrinting = isPrinting,
                historyVersion = historyVersion,
                selected = gallerySelected,
                onSelect = { gallerySelected = it },
                onPrint = { print(it) }
            )
        }
        }
      }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

/** Always-visible bar: Back on the left, "My stickers" (and the settings gear on home) on the right. */
@Composable
fun TopBar(
    showBack: Boolean,
    showGallery: Boolean,
    enabled: Boolean,
    onBack: () -> Unit,
    onGallery: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 20.dp, bottom = 4.dp)
            .height(56.dp)
    ) {
        if (showBack) {
            Box(modifier = Modifier.align(Alignment.CenterStart)) {
                PillButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = "Back",
                    color = SkyBlue,
                    enabled = enabled,
                    onClick = onBack
                )
            }
        }
        if (showGallery) {
            // Centered when it's alone (home); pushed right when Back takes the left side,
            // since both won't fit side by side with it centered on small phones.
            Box(modifier = Modifier.align(if (showBack) Alignment.CenterEnd else Alignment.Center)) {
                PillButton(
                    icon = Icons.Default.Collections,
                    label = "My stickers",
                    color = OceanBlue,
                    enabled = enabled,
                    onClick = onGallery
                )
            }
        }
        Box(modifier = Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
}

@Composable
fun PillButton(
    icon: ImageVector,
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.92f else 1f, tween(100), label = "pillScale")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .scale(scale)
            .shadow(6.dp, RoundedCornerShape(50))
            .background(if (enabled) color else color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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
    val scaleFactor = (configuration.screenHeightDp.toFloat() / 800f).coerceIn(0.75f, 1.0f)
    return with(density) { (baseSize.value * scaleFactor).sp }
}

/** A gear icon that only fires after being held down for [holdMillis], with a progress ring. */
@Composable
fun HoldToOpenButton(
    holdMillis: Int,
    onShortTap: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(56.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        var opened = false
                        val job = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, tween(holdMillis, easing = LinearEasing))
                            opened = true
                            onOpen()
                        }
                        tryAwaitRelease()
                        job.cancel()
                        scope.launch { progress.snapTo(0f) }
                        if (!opened && progress.value < 0.15f) onShortTap()
                    }
                )
            }
    ) {
        if (progress.value > 0f) {
            CircularProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.size(48.dp),
                color = MagicPurple,
                strokeWidth = 4.dp,
            )
        }
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings (hold for 3 seconds)",
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun HomeScreen(
    onMicrophoneClick: () -> Unit,
    onSurpriseClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(150),
        label = "scale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Rotating example so kids who freeze up have something to say.
    var ideaIndex by remember { mutableIntStateOf(STICKER_IDEAS.indices.random()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            ideaIndex = (ideaIndex + 1) % STICKER_IDEAS.size
        }
    }

    val titleTextSize = responsiveTextSize(MaterialTheme.typography.displaySmall.fontSize)
    val subtitleTextSize = responsiveTextSize(MaterialTheme.typography.titleLarge.fontSize)
    val hintTextSize = responsiveTextSize(MaterialTheme.typography.titleMedium.fontSize)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "✨ Sticker Magic! ✨",
            style = MaterialTheme.typography.displaySmall.copy(fontSize = titleTextSize),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Tap the microphone and tell me\nwhat sticker you want!",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = subtitleTextSize),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Giant microphone button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.scale(pulseScale * scale)
        ) {
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(PlayfulCoral.copy(alpha = 0.4f), PlayfulCoral.copy(alpha = 0f))
                        ),
                        shape = CircleShape
                    )
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(175.dp)
                    .shadow(12.dp, CircleShape)
                    .background(
                        brush = Brush.verticalGradient(colors = listOf(PlayfulCoral, PlayfulCoralLight)),
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
                    contentDescription = "Tap and say what sticker you want",
                    tint = Color.White,
                    modifier = Modifier.size(80.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedContent(
            targetState = ideaIndex,
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
            label = "idea"
        ) { index ->
            val (idea, emoji) = STICKER_IDEAS[index]
            Text(
                text = "Try saying: \"$idea\" $emoji",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = hintTextSize),
                color = PlayfulCoral,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        ActionButton(
            icon = { Icon(Icons.Default.Casino, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp)) },
            label = "Surprise me!",
            backgroundColor = MagicPurple,
            enabled = true,
            size = 80.dp,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onSurpriseClick()
            }
        )
    }
}

@Composable
fun RecordingScreen(
    amplitude: Float,
    onStopClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording")

    @Composable
    fun ringScale(delayMs: Int, label: String) = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, delayMillis = delayMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = label
    )

    val ring1Scale by ringScale(0, "ring1")
    val ring2Scale by ringScale(500, "ring2")
    val ring3Scale by ringScale(1000, "ring3")

    val amplitudeScale by animateFloatAsState(
        targetValue = 1f + (amplitude * 0.3f),
        animationSpec = tween(100),
        label = "amplitude"
    )

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

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(250.dp)
        ) {
            listOf(ring1Scale to GrassGreen, ring2Scale to SkyBlue, ring3Scale to MagicPurple).forEach { (s, c) ->
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(s)
                        .background(color = c.copy(alpha = 0.3f / s), shape = CircleShape)
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .scale(amplitudeScale)
                    .shadow(8.dp, CircleShape)
                    .background(brush = Brush.verticalGradient(colors = listOf(GrassGreen, SkyBlue)), shape = CircleShape)
                    .border(4.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                    .clickable(onClick = onStopClick)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Tap when you're done",
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

private val GENERATING_MESSAGES = listOf(
    "Sharpening my crayons… ✏️",
    "Drawing big bold lines… 🖍️",
    "Adding a little magic… 🪄",
    "Making it extra fun… 🌈",
    "Almost done… 🌟"
)

@Composable
fun GeneratingScreen(idea: StickerIdea?) {
    val infiniteTransition = rememberInfiniteTransition(label = "generating")

    @Composable
    fun dotOffset(delayMs: Int, label: String) = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -20f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, delayMillis = delayMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = label
    )

    val dot1Offset by dotOffset(0, "dot1")
    val dot2Offset by dotOffset(150, "dot2")
    val dot3Offset by dotOffset(300, "dot3")

    // Rotate fun status messages so the wait feels shorter.
    var messageIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            messageIndex = (messageIndex + 1).coerceAtMost(GENERATING_MESSAGES.lastIndex)
        }
    }

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

        // Show what was heard so kids (and grown-ups) know what's coming.
        Text(
            text = if (idea == null) "Listening to your idea…" else "Drawing ${idea.subject}",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = subtitleTextSize, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(48.dp))

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
            Text(text = "✨", style = MaterialTheme.typography.displayLarge)
        }

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedContent(
            targetState = if (idea == null) "Thinking… 🤔" else GENERATING_MESSAGES[messageIndex],
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "status"
        ) { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(dot1Offset to SunnyYellow, dot2Offset to PlayfulCoral, dot3Offset to OceanBlue).forEach { (o, c) ->
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .offset(y = o.dp)
                        .background(c, CircleShape)
                )
            }
        }
    }
}

@Composable
fun ResultScreen(
    image: Bitmap?,
    caption: String?,
    isPrinting: Boolean,
    onPrintClick: () -> Unit,
    onAgainClick: () -> Unit,
    onNewClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val titleTextSize = responsiveTextSize(MaterialTheme.typography.displaySmall.fontSize)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎨 Your Sticker!",
            style = MaterialTheme.typography.displaySmall.copy(fontSize = titleTextSize),
            color = OceanBlue,
            textAlign = TextAlign.Center
        )

        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (image != null) StickerImage(image)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            ActionButton(
                icon = { Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp)) },
                label = "New idea",
                backgroundColor = SkyBlue,
                enabled = !isPrinting,
                size = 80.dp,
                onClick = onNewClick
            )

            ActionButton(
                icon = {
                    if (isPrinting) {
                        CircularProgressIndicator(modifier = Modifier.size(52.dp), color = Color.White, strokeWidth = 5.dp)
                    } else {
                        Icon(Icons.Default.Print, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
                    }
                },
                label = if (isPrinting) "Printing..." else "Print!",
                backgroundColor = GrassGreen,
                enabled = !isPrinting,
                size = 112.dp,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPrintClick()
                }
            )

            ActionButton(
                icon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp)) },
                label = "Draw again",
                backgroundColor = MagicPurple,
                enabled = !isPrinting,
                size = 80.dp,
                onClick = onAgainClick
            )
        }
    }
}

@Composable
private fun StickerImage(image: Bitmap, modifier: Modifier = Modifier) {
    Image(
        bitmap = image.asImageBitmap(),
        contentDescription = "Your sticker",
        // Fit inside the space available (height first) so it never covers the buttons.
        modifier = modifier
            .aspectRatio(600f / 800f, matchHeightConstraintsFirst = true)
            .shadow(16.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
    )
}

/** Shows the last [StickerHistory.MAX_STICKERS] stickers; tap one to see it big and print it again. */
@Composable
fun GalleryScreen(
    isPrinting: Boolean,
    historyVersion: Int,
    selected: SavedSticker?,
    onSelect: (SavedSticker) -> Unit,
    onPrint: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val stickers by produceState<List<SavedSticker>?>(initialValue = null, historyVersion) {
        value = StickerHistory.list(context)
    }
    val titleTextSize = responsiveTextSize(MaterialTheme.typography.displaySmall.fontSize)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🖼️ My Stickers",
            style = MaterialTheme.typography.displaySmall.copy(fontSize = titleTextSize),
            color = OceanBlue,
            textAlign = TextAlign.Center
        )

        val current = selected
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                stickers == null -> CircularProgressIndicator(color = OceanBlue)

                stickers!!.isEmpty() -> Text(
                    text = "No stickers yet!\nTap the microphone to make one 🎨",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                current != null -> {
                    val bitmap by produceState<Bitmap?>(initialValue = null, current) {
                        value = withContext(Dispatchers.IO) { current.loadBitmap() }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = current.title ?: current.subject,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        bitmap?.let { StickerImage(it, Modifier.weight(1f, fill = false)) }
                            ?: CircularProgressIndicator(color = OceanBlue)
                    }
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(stickers!!, key = { it.id }) { sticker ->
                        StickerThumbnail(sticker) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(sticker)
                        }
                    }
                }
            }
        }

        if (current != null) {
            Box(modifier = Modifier.padding(bottom = 24.dp)) {
                ActionButton(
                    icon = {
                        if (isPrinting) {
                            CircularProgressIndicator(modifier = Modifier.size(52.dp), color = Color.White, strokeWidth = 5.dp)
                        } else {
                            Icon(Icons.Default.Print, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
                        }
                    },
                    label = if (isPrinting) "Printing..." else "Print!",
                    backgroundColor = GrassGreen,
                    enabled = !isPrinting,
                    size = 112.dp,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // Print at full resolution, not the thumbnail.
                        current.loadBitmap()?.let(onPrint)
                    }
                )
            }
        }
    }
}

@Composable
private fun StickerThumbnail(sticker: SavedSticker, onClick: () -> Unit) {
    val thumbnail by produceState<Bitmap?>(initialValue = null, sticker.id) {
        value = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 } // 150x200 is plenty
            BitmapFactory.decodeFile(sticker.file.absolutePath, options)
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(600f / 800f)
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
    ) {
        thumbnail?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = sticker.title ?: sticker.subject,
                modifier = Modifier.fillMaxSize()
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
    size: Dp = 100.dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(100),
        label = "buttonScale"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .scale(scale)
                .size(size)
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

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    StickerPrinterTheme {
        HomeScreen(onMicrophoneClick = {}, onSurpriseClick = {})
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
        GeneratingScreen(idea = StickerIdea("a dinosaur riding a skateboard"))
    }
}
