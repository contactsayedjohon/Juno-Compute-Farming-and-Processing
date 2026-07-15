package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.*
import com.example.viewmodel.JunoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    viewModel: JunoViewModel,
    onPairingSuccess: () -> Unit,
    onAuthOnlySuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Steps definition
    val STEP_WELCOME = 0
    val STEP_SIGN_UP = 1
    val STEP_LOGIN = 2
    val STEP_PAIRING = 3

    var currentStep by remember {
        mutableStateOf(
            if (viewModel.prefs.authToken.isEmpty()) STEP_WELCOME else STEP_PAIRING
        )
    }

    // Input Fields States
    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }

    var signupEmail by remember { mutableStateOf("") }
    var signupPassword by remember { mutableStateOf("") }
    var signupConfirmPassword by remember { mutableStateOf("") }
    var signupPasswordVisible by remember { mutableStateOf(false) }
    var signupConfirmPasswordVisible by remember { mutableStateOf(false) }

    var manualPairingCode by remember { mutableStateOf("") }

    // Loading & Dialogs States
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var forgotPasswordEmail by remember { mutableStateOf("") }

    // System Permissions States
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else true
        )
    }

    // Permission launchers
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JunoBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Show Back Button if in Sub-auth screens
            if (currentStep == STEP_SIGN_UP || currentStep == STEP_LOGIN) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    IconButton(
                        onClick = { currentStep = STEP_WELCOME },
                        modifier = Modifier.testTag("auth_back_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = JunoPrimary)
                    }
                }
            } else if (currentStep == STEP_PAIRING) {
                // If logged in, show user email and sign out option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FLEET NODE PROVISIONING",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = JunoPrimary
                        )
                        Text(
                            text = viewModel.prefs.userEmail,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = JunoTextSecondary
                        )
                    }
                    TextButton(
                        onClick = {
                            viewModel.logout()
                            currentStep = STEP_WELCOME
                        },
                        modifier = Modifier.testTag("auth_logout_button")
                    ) {
                        Icon(Icons.Default.Logout, "Logout", tint = JunoDanger, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sign Out", color = JunoDanger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Core Welcome Logo Section
            if (currentStep == STEP_WELCOME) {
                Spacer(modifier = Modifier.height(40.dp))
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(JunoPrimary, JunoSecondary)))
                        .padding(2.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(JunoBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsInputComponent,
                        contentDescription = "Juno Node Logo",
                        tint = JunoPrimary,
                        modifier = Modifier.size(42.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "JUNOCOMPUTE",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = JunoTextPrimary,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "SaaS CLUSTER NODE DAEMON",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = JunoPrimary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Link your smartphone to deploy distributed worker nodes. Securely compute cluster workload loops 24/7 in background.",
                    fontSize = 13.sp,
                    color = JunoTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )

                Spacer(modifier = Modifier.height(50.dp))

                // Welcome Options Screen 1A
                Button(
                    onClick = { currentStep = STEP_LOGIN },
                    colors = ButtonDefaults.buttonColors(containerColor = JunoPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("welcome_login_btn")
                ) {
                    Icon(Icons.Default.Login, "Login", tint = JunoBackground)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("I HAVE AN ACCOUNT", color = JunoBackground, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = { currentStep = STEP_SIGN_UP },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JunoPrimary),
                    border = BorderStroke(1.dp, JunoBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("welcome_signup_btn")
                ) {
                    Icon(Icons.Default.PersonAdd, "Sign up", tint = JunoPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CREATE NEW ACCOUNT", color = JunoTextPrimary, fontWeight = FontWeight.Bold)
                }
            }

            // Screen 1B: Sign Up Flow
            if (currentStep == STEP_SIGN_UP) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Create Account",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = JunoTextPrimary,
                    modifier = Modifier.align(Alignment.Start)
                )
                Text(
                    text = "Register as an operator on the JunoCompute SaaS network",
                    fontSize = 13.sp,
                    color = JunoTextSecondary,
                    modifier = Modifier.align(Alignment.Start).padding(bottom = 24.dp)
                )

                OutlinedTextField(
                    value = signupEmail,
                    onValueChange = { signupEmail = it },
                    label = { Text("Email Address", color = JunoTextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JunoPrimary,
                        unfocusedBorderColor = JunoBorder,
                        focusedTextColor = JunoTextPrimary,
                        unfocusedTextColor = JunoTextPrimary,
                        focusedContainerColor = JunoSurface,
                        unfocusedContainerColor = JunoSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_email_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = signupPassword,
                    onValueChange = { signupPassword = it },
                    label = { Text("Password (min 8 characters)", color = JunoTextSecondary) },
                    singleLine = true,
                    visualTransformation = if (signupPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { signupPasswordVisible = !signupPasswordVisible }) {
                            Icon(
                                imageVector = if (signupPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle password visibility",
                                tint = JunoTextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JunoPrimary,
                        unfocusedBorderColor = JunoBorder,
                        focusedTextColor = JunoTextPrimary,
                        unfocusedTextColor = JunoTextPrimary,
                        focusedContainerColor = JunoSurface,
                        unfocusedContainerColor = JunoSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_password_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = signupConfirmPassword,
                    onValueChange = { signupConfirmPassword = it },
                    label = { Text("Confirm Password", color = JunoTextSecondary) },
                    singleLine = true,
                    visualTransformation = if (signupConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { signupConfirmPasswordVisible = !signupConfirmPasswordVisible }) {
                            Icon(
                                imageVector = if (signupConfirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle password visibility",
                                tint = JunoTextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JunoPrimary,
                        unfocusedBorderColor = JunoBorder,
                        focusedTextColor = JunoTextPrimary,
                        unfocusedTextColor = JunoTextPrimary,
                        focusedContainerColor = JunoSurface,
                        unfocusedContainerColor = JunoSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_confirm_password_input")
                )

                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = JunoDanger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = {
                        if (signupEmail.isEmpty() || signupPassword.isEmpty()) {
                            errorMessage = "All fields are required"
                        } else if (signupPassword.length < 8) {
                            errorMessage = "Password must be at least 8 characters"
                        } else if (signupPassword != signupConfirmPassword) {
                            errorMessage = "Passwords do not match"
                        } else {
                            errorMessage = ""
                            isLoading = true
                            loadingMessage = "Creating secure cluster operator account..."
                            coroutineScope.launch {
                                delay(1200) // Simulated secure API lookup
                                isLoading = false
                                viewModel.signUp(signupEmail) {
                                    onAuthOnlySuccess()
                                    currentStep = STEP_PAIRING
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JunoPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("signup_submit_button")
                ) {
                    Text("CREATE CLUSTER ACCOUNT", color = JunoBackground, fontWeight = FontWeight.Bold)
                }
            }

            // Screen 1C: Login Flow
            if (currentStep == STEP_LOGIN) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Welcome Back",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = JunoTextPrimary,
                    modifier = Modifier.align(Alignment.Start)
                )
                Text(
                    text = "Sign in to provision and authorize node connections",
                    fontSize = 13.sp,
                    color = JunoTextSecondary,
                    modifier = Modifier.align(Alignment.Start).padding(bottom = 24.dp)
                )

                OutlinedTextField(
                    value = loginEmail,
                    onValueChange = { loginEmail = it },
                    label = { Text("Email Address", color = JunoTextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JunoPrimary,
                        unfocusedBorderColor = JunoBorder,
                        focusedTextColor = JunoTextPrimary,
                        unfocusedTextColor = JunoTextPrimary,
                        focusedContainerColor = JunoSurface,
                        unfocusedContainerColor = JunoSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_email_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = loginPassword,
                    onValueChange = { loginPassword = it },
                    label = { Text("Password", color = JunoTextSecondary) },
                    singleLine = true,
                    visualTransformation = if (loginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { loginPasswordVisible = !loginPasswordVisible }) {
                            Icon(
                                imageVector = if (loginPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle password visibility",
                                tint = JunoTextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JunoPrimary,
                        unfocusedBorderColor = JunoBorder,
                        focusedTextColor = JunoTextPrimary,
                        unfocusedTextColor = JunoTextPrimary,
                        focusedContainerColor = JunoSurface,
                        unfocusedContainerColor = JunoSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password_input")
                )

                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = JunoDanger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "Forgot Password?",
                        color = JunoPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { showForgotPasswordDialog = true }
                            .testTag("forgot_password_link")
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (loginEmail.isEmpty() || loginPassword.isEmpty()) {
                            errorMessage = "All fields are required"
                        } else {
                            errorMessage = ""
                            isLoading = true
                            loadingMessage = "Authenticating and checking registration..."
                            coroutineScope.launch {
                                delay(1000) // Simulated auth latency
                                isLoading = false
                                viewModel.login(loginEmail) {
                                    onAuthOnlySuccess()
                                    if (viewModel.prefs.isPaired) {
                                        onPairingSuccess()
                                    } else {
                                        currentStep = STEP_PAIRING
                                    }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JunoPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("login_submit_button")
                ) {
                    Text("SIGN IN TO CLUSTER", color = JunoBackground, fontWeight = FontWeight.Bold)
                }
            }

            // Screen 1D: Device Pairing Flow
            if (currentStep == STEP_PAIRING) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Provision Cluster Node",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = JunoTextPrimary,
                    modifier = Modifier.align(Alignment.Start)
                )
                Text(
                    text = "Pair this smartphone with your computer dashboard fleet to stream metrics and accept decentralized processing cycles.",
                    fontSize = 13.sp,
                    color = JunoTextSecondary,
                    modifier = Modifier.align(Alignment.Start).padding(bottom = 20.dp)
                )

                // Method 1: QR Code Scanner Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, JunoBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = JunoSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan icon",
                                tint = JunoPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Camera QR Code Scanner",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = JunoTextPrimary
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (!hasCameraPermission) {
                                Button(
                                    onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                    colors = ButtonDefaults.buttonColors(containerColor = JunoPrimary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Enable", fontSize = 11.sp, color = JunoBackground, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (hasCameraPermission) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                                    .border(1.dp, JunoBorder, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                CameraPreviewView(onQrCodeScanned = { qrPayload ->
                                    isLoading = true
                                    loadingMessage = "Scanning and verifying Cloud QR signature..."
                                    coroutineScope.launch {
                                        val pairSuccess = viewModel.pairWithJson(qrPayload)
                                        isLoading = false
                                        if (pairSuccess) {
                                            onPairingSuccess()
                                        } else {
                                            Toast.makeText(context, "Invalid QR code payload", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                })
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .border(2.dp, JunoPrimary, RoundedCornerShape(8.dp))
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(JunoPrimary)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Scan the QR code displayed in your browser fleet dashboard.",
                                fontSize = 12.sp,
                                color = JunoTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(JunoBackground)
                                    .border(1.dp, JunoBorder, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.CameraAlt, "No Camera", tint = JunoTextSecondary, modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Camera permission required to scan", fontSize = 12.sp, color = JunoTextSecondary)
                                }
                            }
                        }

                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Method 2: Manual Code Entry Card (Fallback)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, JunoBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = JunoSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, "Manual input", tint = JunoPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Manual Pairing Code (Fallback)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = JunoTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Or, type the 6-digit verification pairing code displayed on your dashboard:",
                            fontSize = 12.sp,
                            color = JunoTextSecondary,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = manualPairingCode,
                            onValueChange = { if (it.length <= 6) manualPairingCode = it },
                            label = { Text("6-Digit Pairing Code", color = JunoTextSecondary) },
                            placeholder = { Text("e.g. 483921") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JunoPrimary,
                                unfocusedBorderColor = JunoBorder,
                                focusedTextColor = JunoTextPrimary,
                                unfocusedTextColor = JunoTextPrimary,
                                focusedContainerColor = JunoBackground,
                                unfocusedContainerColor = JunoBackground
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_pairing_code_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (manualPairingCode.length == 6) {
                                    isLoading = true
                                    loadingMessage = "Verifying manual pairing code..."
                                    coroutineScope.launch {
                                        delay(1200)
                                        isLoading = false
                                        val success = viewModel.pairWithCode(manualPairingCode)
                                        if (success) {
                                            onPairingSuccess()
                                        } else {
                                            Toast.makeText(context, "Pairing code must be 6 digits", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JunoPrimary),
                            shape = RoundedCornerShape(10.dp),
                            enabled = manualPairingCode.length == 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("manual_pairing_submit_button")
                        ) {
                            Text("VERIFY & LINK DEVICE", color = JunoBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // System Permissions Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, JunoBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = JunoSurface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Permissions Integrity Check",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = JunoTextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Permission Item 1: Battery Optimization Exemption
                        PermissionItem(
                            title = "Keep Active 24/7",
                            description = "Ignore battery optimizations to compute loops in background",
                            isGranted = isIgnoringBatteryOptimizations,
                            onGrant = {
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
                        )

                        Divider(color = JunoBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))

                        // Permission Item 2: Notifications
                        PermissionItem(
                            title = "Status Notifications",
                            description = "Keeps worker daemon active and prevents task termination",
                            isGranted = hasNotificationPermission,
                            onGrant = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        // Global Processing Overlay (for highly responsive SaaS authenticating delays)
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = JunoPrimary, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = loadingMessage,
                        color = JunoTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }

        // Forgot Password Dialog
        if (showForgotPasswordDialog) {
            AlertDialog(
                onDismissRequest = { showForgotPasswordDialog = false },
                title = { Text("Reset Password", color = JunoTextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            text = "Enter your registered email address below. We'll send a secure password reset link to request a change.",
                            fontSize = 13.sp,
                            color = JunoTextSecondary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        OutlinedTextField(
                            value = forgotPasswordEmail,
                            onValueChange = { forgotPasswordEmail = it },
                            label = { Text("Email Address", color = JunoTextSecondary) },
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
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (forgotPasswordEmail.isNotEmpty()) {
                                showForgotPasswordDialog = false
                                Toast.makeText(context, "Password reset email sent to: $forgotPasswordEmail", Toast.LENGTH_LONG).show()
                                forgotPasswordEmail = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JunoPrimary)
                    ) {
                        Text("Send Reset Link", color = JunoBackground)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showForgotPasswordDialog = false }) {
                        Text("Cancel", color = JunoTextSecondary)
                    }
                },
                containerColor = JunoSurface,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = JunoTextPrimary)
            Text(description, fontSize = 11.sp, color = JunoTextSecondary, lineHeight = 14.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (isGranted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Granted",
                tint = JunoSuccess,
                modifier = Modifier.size(24.dp)
            )
        } else {
            OutlinedButton(
                onClick = onGrant,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = JunoPrimary),
                border = BorderStroke(1.dp, JunoPrimary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@SuppressLint("UnrememberedMutableState")
@Composable
fun CameraPreviewView(onQrCodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                    .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(ContextCompat.getMainExecutor(ctx), QrCodeAnalyzer { qrPayload ->
                            onQrCodeScanned(qrPayload)
                        })
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                } catch (e: Exception) {
                    // Fail gracefully
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}
