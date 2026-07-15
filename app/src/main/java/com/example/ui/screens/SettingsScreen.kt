package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.viewmodel.JunoViewModel
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: JunoViewModel,
    onUnpairTriggered: () -> Unit,
    onLogoutTriggered: () -> Unit
) {
    val logs by viewModel.logs.collectAsState()
    val listState = rememberLazyListState()

    // Preferences states
    var deviceName by remember { mutableStateOf(viewModel.prefs.deviceName) }
    var tags by remember { mutableStateOf(viewModel.prefs.tags) }

    // Toggles
    var autoStart by remember { mutableStateOf(viewModel.prefs.autoStartOnBoot) }
    var keepScreen by remember { mutableStateOf(viewModel.prefs.keepScreenOn) }
    var allowAdb by remember { mutableStateOf(viewModel.prefs.allowAdbProxy) }
    var allowTerminal by remember { mutableStateOf(viewModel.prefs.allowTerminalAccess) }
    var allowFile by remember { mutableStateOf(viewModel.prefs.allowFileTransfer) }
    var chargeOnly by remember { mutableStateOf(viewModel.prefs.chargeOnlyMode) }

    // Sliders
    var cpuLimit by remember { mutableStateOf(viewModel.prefs.maxCpuLimit) }
    var tempThreshold by remember { mutableStateOf(viewModel.prefs.maxTempThreshold) }

    // Auto-scroll logs to bottom when a new log arrives
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(JunoBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Account Section Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, JunoBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = JunoSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(JunoPrimary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountCircle, null, tint = JunoPrimary, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "CLOUD ACCOUNT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = JunoPrimary
                            )
                            Text(
                                text = viewModel.prefs.userEmail.ifEmpty { "no-account@junoverseai.com" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = JunoTextPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.unlinkDevice()
                                onUnpairTriggered()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JunoSurfaceVariant),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("unlink_device_button")
                        ) {
                            Icon(Icons.Default.LinkOff, null, tint = JunoSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Unlink", color = JunoTextPrimary, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.logout()
                                onLogoutTriggered()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JunoSurfaceVariant),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("switch_account_button")
                        ) {
                            Icon(Icons.Default.Logout, null, tint = JunoDanger, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Switch Account", color = JunoTextPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Section 1: Server Configuration
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, JunoBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = JunoSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CLOUD LINK INTEGRITY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = JunoPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Active Server URL:",
                        fontSize = 12.sp,
                        color = JunoTextSecondary
                    )
                    Text(
                        text = viewModel.prefs.serverUrl,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = JunoTextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            viewModel.unpairNode()
                            onUnpairTriggered()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JunoSurfaceVariant),
                        modifier = Modifier.fillMaxWidth().testTag("re_pair_button")
                    ) {
                        Icon(Icons.Default.LinkOff, null, tint = JunoDanger, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Repair / Reconnect Token Connection", color = JunoTextPrimary, fontSize = 13.sp)
                    }
                }
            }
        }

        // Section 2: Device Profile Settings
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, JunoBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = JunoSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DEVICE PROFILE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = JunoPrimary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = {
                            deviceName = it
                            viewModel.updateDeviceIdentity(it, tags)
                        },
                        label = { Text("Device Name Label", color = JunoTextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JunoPrimary,
                            unfocusedBorderColor = JunoBorder,
                            focusedTextColor = JunoTextPrimary,
                            unfocusedTextColor = JunoTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("settings_device_name_input")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tags,
                        onValueChange = {
                            tags = it
                            viewModel.updateDeviceIdentity(deviceName, it)
                        },
                        label = { Text("Group Tags (comma separated)", color = JunoTextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JunoPrimary,
                            unfocusedBorderColor = JunoBorder,
                            focusedTextColor = JunoTextPrimary,
                            unfocusedTextColor = JunoTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Section 3: Governors & Behavior Toggles
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, JunoBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = JunoSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "HARDWARE GOVERNORS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = JunoPrimary
                    )
                    
                    Spacer(modifier = Modifier.height(14.dp))

                    // CPU Limit Governor cap
                    Column {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("Max CPU Usage Limit", fontSize = 13.sp, color = JunoTextPrimary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("${cpuLimit.toInt()}%", fontSize = 13.sp, color = JunoPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = cpuLimit,
                            onValueChange = {
                                cpuLimit = it
                                viewModel.updateCpuLimit(it)
                            },
                            valueRange = 50f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = JunoPrimary,
                                activeTrackColor = JunoPrimary,
                                inactiveTrackColor = JunoBorder
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Temp Threshold Slider
                    Column {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("Max Temperature Limit (Safety Throttle)", fontSize = 13.sp, color = JunoTextPrimary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("${tempThreshold.toInt()}°C", fontSize = 13.sp, color = JunoDanger, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = tempThreshold,
                            onValueChange = {
                                tempThreshold = it
                                viewModel.updateTempThreshold(it)
                            },
                            valueRange = 35f..45f,
                            colors = SliderDefaults.colors(
                                thumbColor = JunoDanger,
                                activeTrackColor = JunoDanger,
                                inactiveTrackColor = JunoBorder
                            )
                        )
                        Text(
                            text = "Safety governor automatically pauses cluster operations if battery temperature exceeds this limit.",
                            fontSize = 11.sp,
                            color = JunoTextSecondary,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        // Section 4: Behavior Toggle Checklist
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, JunoBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = JunoSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "OPERATION CONFIGURATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = JunoPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    ToggleItem(
                        title = "Auto-Start on Boot",
                        description = "Launch execution thread immediately on reboot",
                        checked = autoStart,
                        onCheckedChange = {
                            autoStart = it
                            viewModel.updateToggle("auto_start", it)
                        }
                    )

                    ToggleItem(
                        title = "Wakelock / Keep Screen On",
                        description = "Keep AMOLED screen active (WARNING: higher power draw)",
                        checked = keepScreen,
                        onCheckedChange = {
                            keepScreen = it
                            viewModel.updateToggle("keep_screen", it)
                        }
                    )

                    ToggleItem(
                        title = "Allow ADB Proxy Command",
                        description = "Enables cloud terminal to pipe commands to localhost:5555",
                        checked = allowAdb,
                        onCheckedChange = {
                            allowAdb = it
                            viewModel.updateToggle("adb_proxy", it)
                        }
                    )

                    ToggleItem(
                        title = "Allow Interactive Shell Terminal",
                        description = "Allows full bash multiplex stream interface access",
                        checked = allowTerminal,
                        onCheckedChange = {
                            allowTerminal = it
                            viewModel.updateToggle("terminal", it)
                        }
                    )

                    ToggleItem(
                        title = "Allow HTTP File Transfer",
                        description = "Download/Upload media files directly into task folders",
                        checked = allowFile,
                        onCheckedChange = {
                            allowFile = it
                            viewModel.updateToggle("file_transfer", it)
                        }
                    )

                    ToggleItem(
                        title = "Charge-Only Activity Mode",
                        description = "Only accept compute tasks when phone is plugged in",
                        checked = chargeOnly,
                        onCheckedChange = {
                            chargeOnly = it
                            viewModel.updateToggle("charge_only", it)
                        }
                    )
                }
            }
        }

        // Section 5: Storage Cache
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, JunoBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = JunoSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "PERSISTENT DISK STATISTICS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = JunoPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val cacheSize = getFolderSizeString(context.filesDir)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Internal Task Cache", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = JunoTextPrimary)
                            Text("Current size: $cacheSize", fontSize = 12.sp, color = JunoTextSecondary)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = { viewModel.clearTaskCache() },
                            colors = ButtonDefaults.buttonColors(containerColor = JunoSurfaceVariant),
                            modifier = Modifier.testTag("clear_cache_button")
                        ) {
                            Text("Clear", color = JunoPrimary)
                        }
                    }
                }
            }
        }

        // Section 6: Local Terminal Logging System Logs Viewport
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, JunoBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = JunoSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE CONSOLE STREAM",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = JunoPrimary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Showing last ${logs.size} lines",
                            fontSize = 10.sp,
                            color = JunoTextSecondary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    // Shell Console log terminal layout
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF070B11))
                            .border(1.dp, JunoBorder, RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        if (logs.isEmpty()) {
                            Text(
                                text = "Standby mode active. Waiting for system events...",
                                color = JunoTextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(logs) { log ->
                                    Text(
                                        text = log,
                                        color = JunoPrimary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun ToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = JunoTextPrimary)
            Text(description, fontSize = 11.sp, color = JunoTextSecondary, lineHeight = 14.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = JunoPrimary,
                checkedTrackColor = JunoPrimary.copy(alpha = 0.5f),
                uncheckedThumbColor = JunoTextSecondary,
                uncheckedTrackColor = JunoBorder
            )
        )
    }
}

private fun getFolderSizeString(file: File): String {
    val sizeBytes = getFolderSize(file)
    return when {
        sizeBytes < 1024 -> "$sizeBytes B"
        sizeBytes < 1024 * 1024 -> "${String.format(Locale.US, "%.1f", sizeBytes / 1024f)} KB"
        else -> "${String.format(Locale.US, "%.1f", sizeBytes / (1024f * 1024f))} MB"
    }
}

private fun getFolderSize(file: File): Long {
    var size = 0L
    if (file.isDirectory) {
        file.listFiles()?.forEach {
            size += getFolderSize(it)
        }
    } else {
        size = file.length()
    }
    return size
}
