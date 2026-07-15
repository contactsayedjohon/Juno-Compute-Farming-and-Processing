package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.JunoBackground
import com.example.ui.theme.JunoPrimary
import com.example.ui.theme.JunoBorder
import com.example.ui.theme.JunoTextSecondary
import com.example.viewmodel.JunoViewModel
import com.example.service.JunoComputeService
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build

class MainActivity : ComponentActivity() {
    private val viewModel: JunoViewModel by viewModels()

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, JunoComputeService::class.java).apply {
                action = JunoComputeService.ACTION_START_MIRROR
                putExtra(JunoComputeService.EXTRA_PROJECTION_RESULT_INTENT, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    private val screenCaptureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            try {
                screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
            } catch (e: Exception) {
                viewModel.startWorkerService()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Auto launch foreground service on startup if already paired
        if (viewModel.prefs.isPaired) {
            viewModel.startWorkerService()
        }

        // Register receiver for MediaProjection trigger
        val filter = IntentFilter("com.example.REQUEST_SCREEN_CAPTURE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenCaptureReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenCaptureReceiver, filter)
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = JunoBackground
                ) {
                    MainAppContent(viewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenCaptureReceiver)
        } catch (e: Exception) {
            // Ignored
        }
    }
}

@Composable
fun MainAppContent(viewModel: JunoViewModel) {
    var isLoggedIn by remember { mutableStateOf(viewModel.prefs.authToken.isNotEmpty()) }
    var isPaired by remember { mutableStateOf(viewModel.prefs.isPaired) }
    var currentTab by remember { mutableStateOf("dashboard") }

    if (!isLoggedIn || !isPaired) {
        SetupScreen(
            viewModel = viewModel,
            onPairingSuccess = {
                isLoggedIn = true
                isPaired = true
                currentTab = "dashboard"
            },
            onAuthOnlySuccess = {
                isLoggedIn = true
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = JunoBackground,
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("bottom_nav_bar")
                ) {
                    NavigationBarItem(
                        selected = currentTab == "dashboard",
                        onClick = { currentTab = "dashboard" },
                        icon = { Icon(Icons.Default.Dashboard, "Dashboard") },
                        label = { Text("Dashboard", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = JunoPrimary,
                            selectedTextColor = JunoPrimary,
                            unselectedIconColor = JunoTextSecondary,
                            unselectedTextColor = JunoTextSecondary,
                            indicatorColor = JunoBorder
                        ),
                        modifier = Modifier.testTag("tab_dashboard")
                    )
                    NavigationBarItem(
                        selected = currentTab == "settings",
                        onClick = { currentTab = "settings" },
                        icon = { Icon(Icons.Default.Settings, "Settings") },
                        label = { Text("Settings", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = JunoPrimary,
                            selectedTextColor = JunoPrimary,
                            unselectedIconColor = JunoTextSecondary,
                            unselectedTextColor = JunoTextSecondary,
                            indicatorColor = JunoBorder
                        ),
                        modifier = Modifier.testTag("tab_settings")
                    )
                    NavigationBarItem(
                        selected = currentTab == "about",
                        onClick = { currentTab = "about" },
                        icon = { Icon(Icons.Default.Info, "About") },
                        label = { Text("About", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = JunoPrimary,
                            selectedTextColor = JunoPrimary,
                            unselectedIconColor = JunoTextSecondary,
                            unselectedTextColor = JunoTextSecondary,
                            indicatorColor = JunoBorder
                        ),
                        modifier = Modifier.testTag("tab_about")
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(JunoBackground)
            ) {
                when (currentTab) {
                    "dashboard" -> {
                        DashboardScreen(
                            viewModel = viewModel,
                            onUnpairTriggered = {
                                isPaired = false
                            }
                        )
                    }
                    "settings" -> {
                        SettingsScreen(
                            viewModel = viewModel,
                            onUnpairTriggered = {
                                isPaired = false
                            },
                            onLogoutTriggered = {
                                isLoggedIn = false
                                isPaired = false
                            }
                        )
                    }
                    "about" -> {
                        AboutScreen()
                    }
                }
            }
        }
    }
}
