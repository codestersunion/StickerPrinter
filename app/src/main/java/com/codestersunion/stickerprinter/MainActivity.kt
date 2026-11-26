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
