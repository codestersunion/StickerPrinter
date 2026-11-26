package com.codestersunion.stickerprinter

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.codestersunion.stickerprinter.ui.theme.StickerPrinterTheme

object PrinterPreferences {
    private const val PREFS_NAME = "printer_prefs"
    private const val KEY_SELECTED_DEVICE = "selected_device_address"
    
    fun getSelectedDeviceAddress(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SELECTED_DEVICE, null)
    }
    
    fun setSelectedDeviceAddress(context: Context, address: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED_DEVICE, address).apply()
    }
}

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val deviceAddress = intent.getStringExtra("device_address")
        
        setContent {
            StickerPrinterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(
                        deviceAddress = deviceAddress,
                        onBackClick = { finish() }
                    )
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Hide the status bar
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN // Hide status bar
        )
    }
}

@Composable
fun SettingsScreen(
    deviceAddress: String?,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isPrinting by remember { mutableStateOf(false) }
    var showWizardHeroImage by remember { mutableStateOf(false) }
    
    // Printer selection state
    var bluetoothDevices by remember { mutableStateOf<List<BluetoothDeviceInfo>>(emptyList()) }
    var isLoadingDevices by remember { mutableStateOf(false) }
    var selectedDeviceAddress by remember { mutableStateOf<String?>(deviceAddress ?: PrinterPreferences.getSelectedDeviceAddress(context)) }
    
    // Load devices on launch
    LaunchedEffect(Unit) {
        loadBluetoothDevices(context, coroutineScope) { devices ->
            bluetoothDevices = devices
            // If no device is selected, try to auto-select
            if (selectedDeviceAddress == null) {
                val phomemoDevice = devices.find { it.device.name?.contains("Phomemo", ignoreCase = true) == true }
                selectedDeviceAddress = phomemoDevice?.device?.address ?: devices.firstOrNull()?.device?.address
                selectedDeviceAddress?.let { PrinterPreferences.setSelectedDeviceAddress(context, it) }
            }
        }
    }
    
    fun refreshDevices() {
        isLoadingDevices = true
        loadBluetoothDevices(context, coroutineScope) { devices ->
            bluetoothDevices = devices
            isLoadingDevices = false
        }
    }
    
    fun selectDevice(address: String) {
        selectedDeviceAddress = address
        PrinterPreferences.setSelectedDeviceAddress(context, address)
        coroutineScope.launch {
            snackbarHostState.showSnackbar("Printer selected: ${bluetoothDevices.find { it.device.address == address }?.device?.name ?: address}")
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        // Back button and title row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "Printer Settings",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        // Printer Selection Section
        Text(
            text = "Select Printer",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Available Bluetooth Devices",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { refreshDevices() },
                enabled = !isLoadingDevices
            ) {
                if (isLoadingDevices) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh devices"
                    )
                }
            }
        }
        
        if (bluetoothDevices.isEmpty()) {
            Text(
                text = "No Bluetooth devices found. Make sure Bluetooth is enabled and devices are paired.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(bluetoothDevices) { deviceInfo ->
                    PrinterDeviceItem(
                        deviceInfo = deviceInfo,
                        isSelected = deviceInfo.device.address == selectedDeviceAddress,
                        onClick = { selectDevice(deviceInfo.device.address) }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Wi-Fi button
        Button(
            onClick = {
                val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Wi-Fi")
        }
        
        // Bluetooth button
        Button(
            onClick = {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback: If the specific Bluetooth menu doesn't exist on this ROM,
                    // open the main settings menu.
                    try {
                        val fallbackIntent = Intent(android.provider.Settings.ACTION_SETTINGS)
                        fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(fallbackIntent)
                    } catch (e2: Exception) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Could not open Bluetooth settings")
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Bluetooth")
        }

        if (selectedDeviceAddress == null) {
            Text(
                text = "No device selected. Please select a device from the list above.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Text(
                text = "Selected Device: ${bluetoothDevices.find { it.device.address == selectedDeviceAddress }?.device?.name ?: selectedDeviceAddress}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Button(
                onClick = {
                    if (!isPrinting) {
                        isPrinting = true
                        coroutineScope.launch {
                            val result = PrinterHelper.forceConnection(
                                deviceAddress = selectedDeviceAddress!!
                            )
                            
                            if (result.isSuccess) {
                                val message = result.getOrNull() ?: "Connection established"
                                snackbarHostState.showSnackbar(message)
                            } else {
                                snackbarHostState.showSnackbar("Connection failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}")
                            }
                            isPrinting = false
                        }
                    }
                },
                enabled = !isPrinting && selectedDeviceAddress != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isPrinting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text("Connecting...", modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text("Force Connection")
                }
            }

            Button(
                onClick = {
                    if (!isPrinting) {
                        isPrinting = true
                        coroutineScope.launch {
                            val result = PrinterHelper.debugHandshake(
                                deviceAddress = selectedDeviceAddress!!
                            )

                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar("Debug handshake complete")
                            } else {
                                snackbarHostState.showSnackbar("Debug handshake failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}")
                            }
                            isPrinting = false
                        }
                    }
                },
                enabled = !isPrinting && selectedDeviceAddress != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isPrinting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text("Running...", modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text("Debug Handshake")
                }
            }
            
            Button(
                onClick = {
                    if (!isPrinting) {
                        isPrinting = true
                        coroutineScope.launch {
                            val result = PrinterHelper.testPrintText(
                                deviceAddress = selectedDeviceAddress!!
                            )
                            
                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar("Text print command sent successfully")
                            } else {
                                snackbarHostState.showSnackbar("Print failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}")
                            }
                            isPrinting = false
                        }
                    }
                },
                enabled = !isPrinting && selectedDeviceAddress != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isPrinting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text("Printing...", modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text("Test Print Text")
                }
            }

            Button(
                onClick = {
                    if (!isPrinting) {
                        showWizardHeroImage = true
                        isPrinting = true
                        coroutineScope.launch {
                            val result = PrinterHelper.printWizardSpiderHeroImage(
                                deviceAddress = selectedDeviceAddress!!, 
                                context = context, 
                                R.drawable.wizard_spider_hero
                            )

                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar("Safe LZO test completed successfully")
                            } else {
                                snackbarHostState.showSnackbar("Test failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}")
                            }
                            isPrinting = false
                        }
                    }
                },
                enabled = !isPrinting && selectedDeviceAddress != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isPrinting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text("Testing...", modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text("Print Wizard vs Spider Hero")
                }
            }

            Button(
                onClick = {
                    if (!isPrinting) {
                        showWizardHeroImage = true
                        isPrinting = true
                        coroutineScope.launch {
                            val result = PrinterHelper.testPacket(
                                deviceAddress = selectedDeviceAddress!!
                            )

                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar("Test Packet")
                            } else {
                                snackbarHostState.showSnackbar("Print failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}")
                            }
                            isPrinting = false
                        }
                    }
                },
                enabled = !isPrinting && selectedDeviceAddress != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isPrinting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text("Printing...", modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text("Test packet")
                }
            }
            
            // Display the wizard hero image when button is clicked
            if (showWizardHeroImage) {
                val imageResId = context.resources.getIdentifier("wizard_spider_hero", "drawable", context.packageName)
                if (imageResId != 0) {
                    Image(
                        painter = painterResource(id = imageResId),
                        contentDescription = "Wizard vs Spider Hero",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
        }
        
        // Snackbar for messages
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun PrinterDeviceItem(
    deviceInfo: BluetoothDeviceInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val device = deviceInfo.device
    val deviceName = device.name ?: "Unknown Device"
    val deviceAddress = device.address
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = deviceAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (deviceInfo.isConnected) {
                    Text(
                        text = "Connected ${if (deviceInfo.connectedProfiles.isNotEmpty()) "(${deviceInfo.connectedProfiles.joinToString()})" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

fun loadBluetoothDevices(
    context: Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onDevicesLoaded: (List<BluetoothDeviceInfo>) -> Unit
) {
    coroutineScope.launch {
        val devices = withContext(Dispatchers.IO) {
            BluetoothHelper.getBluetoothDevicesWithStatus(context)
        }
        onDevicesLoaded(devices)
    }
}

