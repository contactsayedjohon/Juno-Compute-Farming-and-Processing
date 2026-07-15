package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.viewmodel.JunoViewModel
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: JunoViewModel, onUnpairTriggered: () -> Unit) {
    val isConnected by viewModel.isConnected.collectAsState()
    val taskStatus by viewModel.taskStatus.collectAsState()
    val taskProgress by viewModel.taskProgress.collectAsState()
    val tasksCompleted by viewModel.tasksCompletedToday.collectAsState()
    val vitals by viewModel.vitals.collectAsState()
    
    val context = LocalContext.current

    // Pulse animation for Processing state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulsingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Scaffold(
        containerColor = JunoBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Glowing status dot
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(
                                        (if (isConnected) JunoSuccess else JunoDanger).copy(alpha = pulsingAlpha * 0.3f)
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnected) JunoSuccess else JunoDanger)
                            )
                        }
                        
                        Text(
                            text = viewModel.prefs.deviceName.uppercase(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Text(
                        text = "JunoCompute Node • ${android.os.Build.MODEL} • v1.0.8".uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = JunoTextSecondary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                // Header Right Icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(JunoSurface)
                        .border(1.dp, JunoBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_SETTINGS)
                                    context.startActivity(intent)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsInputComponent,
                            contentDescription = "Node Status Icon",
                            tint = JunoPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Hero Task Card
            val isProcessing = taskStatus.contains("PROCESSING") || taskStatus.contains("DOWNLOADING") || taskStatus.contains("UPLOADING")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (isProcessing) JunoPrimary.copy(alpha = 0.6f) else JunoBorder.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(28.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = JunoSurface),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "CURRENT OPERATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = JunoPrimary,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    Text(
                        text = taskStatus,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .testTag("task_status_text")
                    )

                    if (isProcessing) {
                        Text(
                            text = "Parallel compute chunk execution in progress...",
                            fontSize = 13.sp,
                            color = JunoTextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PROGRESS",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = JunoTextSecondary
                            )
                            Text(
                                text = "${(taskProgress * 100).toInt()}%",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = JunoPrimary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LinearProgressIndicator(
                            progress = { taskProgress },
                            color = JunoPrimary,
                            trackColor = JunoBorder.copy(alpha = 0.3f),
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    } else {
                        Row(
                            modifier = Modifier.padding(top = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(JunoBorder.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = JunoPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Passive Standby Mode",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Ready to receive distributed workloads",
                                    fontSize = 11.sp,
                                    color = JunoTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2x3 Grid of System Vitals
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Vital Card 1: CPU Load
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .border(1.dp, JunoBorder.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                        colors = CardDefaults.cardColors(containerColor = JunoSurface.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "CPU LOAD",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = JunoTextSecondary,
                                    letterSpacing = 1.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.DeveloperBoard,
                                    contentDescription = null,
                                    tint = JunoPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            Column {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", vitals.cpuUsage)}%",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Light,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                )
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    val progress = vitals.cpuUsage / 100f
                                    for (i in 0 until 4) {
                                        val filled = progress >= (i + 1) / 4f || (i == 0 && progress > 0f)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(if (filled) JunoPrimary else JunoBorder.copy(alpha = 0.3f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Vital Card 2: Memory GBs Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .border(1.dp, JunoBorder.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                        colors = CardDefaults.cardColors(containerColor = JunoSurface.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "MEMORY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = JunoTextSecondary,
                                    letterSpacing = 1.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = JunoTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            Column {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", vitals.ramUsedGb)}/${String.format(Locale.US, "%.1f", vitals.ramTotalGb)} GB",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Light,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val usedPct = if (vitals.ramTotalGb > 0) (vitals.ramUsedGb / vitals.ramTotalGb * 100).toInt() else 0
                                Text(
                                    text = "$usedPct% utilized",
                                    fontSize = 10.sp,
                                    color = JunoTextSecondary
                                )
                            }
                        }
                    }
                }

                // Vital Card 3: Battery Power Card
                item {
                    val batteryColor = if (vitals.batteryPercent < 20) JunoDanger else JunoTertiary
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .border(1.dp, JunoBorder.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                        colors = CardDefaults.cardColors(containerColor = JunoSurface.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "POWER",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = JunoTextSecondary,
                                    letterSpacing = 1.sp
                                )
                                Icon(
                                    imageVector = if (vitals.isCharging) Icons.Default.ElectricalServices else Icons.Default.BatteryChargingFull,
                                    contentDescription = null,
                                    tint = if (vitals.isCharging) JunoSuccess else JunoTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            Column {
                                Text(
                                    text = "${vitals.batteryPercent}%",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Light,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (vitals.isCharging) "AC Charging" else "Discharging",
                                    fontSize = 10.sp,
                                    color = if (vitals.isCharging) JunoSuccess else JunoTextSecondary
                                )
                            }
                        }
                    }
                }

                // Vital Card 4: Thermal Card
                item {
                    val temp = vitals.batteryTempC
                    val tempColor = when {
                        temp < 38f -> JunoSuccess
                        temp < 42f -> JunoWarning
                        else -> JunoDanger
                    }
                    val tempStatus = when {
                        temp < 38f -> "Optimal Range"
                        temp < 42f -> "Warm State"
                        else -> "Throttled"
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .border(1.dp, JunoBorder.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                        colors = CardDefaults.cardColors(containerColor = JunoSurface.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "THERMAL",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = JunoTextSecondary,
                                    letterSpacing = 1.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.DeviceThermostat,
                                    contentDescription = null,
                                    tint = tempColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            Column {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", temp)}°C",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Light,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tempStatus,
                                    fontSize = 10.sp,
                                    color = tempColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Vital Card 5: Network Diagnostics
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .border(1.dp, JunoBorder.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                        colors = CardDefaults.cardColors(containerColor = JunoSurface.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "LINK",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = JunoTextSecondary,
                                    letterSpacing = 1.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = JunoTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            Column {
                                Text(
                                    text = vitals.ipAddress,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "WLAN Signal High",
                                    fontSize = 10.sp,
                                    color = JunoTextSecondary
                                )
                            }
                        }
                    }
                }

                // Vital Card 6: Uptime
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .border(1.dp, JunoBorder.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                        colors = CardDefaults.cardColors(containerColor = JunoSurface.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "UP-TIME",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = JunoTextSecondary,
                                    letterSpacing = 1.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.Update,
                                    contentDescription = null,
                                    tint = JunoTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            Column {
                                val hoursInt = vitals.uptimeHours.toInt()
                                val minsInt = ((vitals.uptimeHours - hoursInt) * 60).toInt()
                                val secsInt = (((vitals.uptimeHours - hoursInt) * 60 - minsInt) * 60).toInt()
                                val valueStr = String.format(Locale.US, "%02d:%02d:%02d", hoursInt, minsInt, secsInt)
                                Text(
                                    text = valueStr,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Light,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Stable Connection",
                                    fontSize = 10.sp,
                                    color = JunoTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer row matching elegant design
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SESSION HISTORY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = JunoTextSecondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%,d", tasksCompleted),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White,
                            modifier = Modifier.testTag("completed_tasks_counter")
                        )
                        Text(
                            text = "Tasks OK",
                            fontSize = 11.sp,
                            color = JunoSuccess,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Red glowing emergency unpair button
                Button(
                    onClick = {
                        viewModel.unpairNode()
                        onUnpairTriggered()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JunoDanger),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("emergency_stop_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Stop",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
