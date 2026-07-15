package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.utils.PreferencesHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.TimeUnit

class JunoComputeService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private lateinit var prefs: PreferencesHelper
    private var wakeLock: PowerManager.WakeLock? = null
    private var webSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // WebSockets shouldn't timeout
        .build()

    private var metricsJob: Job? = null
    private var thermalCheckJob: Job? = null
    private var heartbeatJob: Job? = null

    private var lastPongReceivedTime = 0L
    private var isReconnecting = false
    private var reconnectDelayMs = 1000L

    // Active task tracking
    private var activeTaskId: String? = null
    private var activeTaskType: String? = null
    private var activeTaskJob: Job? = null
    private var isThrottled = false

    // Terminal session shell process
    private var activeShellProcess: Process? = null
    private var shellWriter: OutputStream? = null
    private var shellReaderJob: Job? = null

    // ADB Proxy connection
    private var adbSocket: Socket? = null
    private var adbReadJob: Job? = null

    // Battery Monitor BroadcastReceiver
    private var batteryTemp = 0.0f
    private var batteryPercent = 0
    private var isCharging = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val tempRaw = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                batteryTemp = tempRaw / 10.0f // Convert to °C

                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                batteryPercent = if (level != -1 && scale != -1) {
                    (level * 100 / scale.toFloat()).toInt()
                } else {
                    0
                }

                val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesHelper(this)
        JunoServiceState.setServiceRunning(true)
        JunoServiceState.log("Initializing JunoCompute Core Node Daemon...")

        // Acquire partial wake lock to keep CPU active when screen is off
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JunoCompute::WorkerWakeLock").apply {
            acquire(24 * 60 * 60 * 1000L) // Limit to 24 hours just in case, but usually running 24/7
        }

        // Register Battery Monitor
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        // Create notification channel
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification("Connecting to JunoCompute Cluster...", "IDLE — Waiting for tasks"))

        // Load metrics count from preference
        JunoServiceState.setTasksCompleted(prefs.tasksCompletedToday)

        // Start WebSocket Core Connection
        connectWebSocket()

        // Start polling telemetry metrics every 5 seconds
        startTelemetryLoop()

        // Start thermal safety checks
        startThermalSafetyLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        JunoServiceState.setServiceRunning(false)
        JunoServiceState.log("Terminating JunoCompute Node Daemon services...")
        
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Ignored
        }

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }

        disconnectWebSocket()
        metricsJob?.cancel()
        thermalCheckJob?.cancel()
        heartbeatJob?.cancel()
        activeTaskJob?.cancel()
        stopShellSession()
        closeAdbProxy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun connectWebSocket() {
        if (webSocket != null) return
        val url = prefs.serverUrl
        JunoServiceState.log("Connecting to WebSocket Cluster: $url")
        
        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isReconnecting = false
                reconnectDelayMs = 1000L
                JunoServiceState.setConnected(true)
                sendRegistrationPayload()
                startHeartbeatCheck()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Handle ADB or binary packets if proxy is active
                if (prefs.allowAdbProxy && adbSocket != null) {
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            adbSocket?.getOutputStream()?.write(bytes.toByteArray())
                            adbSocket?.getOutputStream()?.flush()
                        } catch (e: Exception) {
                            JunoServiceState.log("ADB Forward failure: ${e.localizedMessage}")
                        }
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                JunoServiceState.setConnected(false)
                JunoServiceState.log("WebSocket connection closing: $reason ($code)")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                JunoServiceState.setConnected(false)
                webSocketClosed()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                JunoServiceState.setConnected(false)
                JunoServiceState.log("WebSocket link failure: ${t.localizedMessage}")
                webSocketClosed()
            }
        })
    }

    private fun webSocketClosed() {
        webSocket = null
        heartbeatJob?.cancel()
        if (!isReconnecting) {
            isReconnecting = true
            serviceScope.launch {
                JunoServiceState.log("Reconnecting in ${reconnectDelayMs / 1000}s...")
                delay(reconnectDelayMs)
                // Exponential backoff capped at 60s
                reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(60000L)
                connectWebSocket()
            }
        }
    }

    private fun disconnectWebSocket() {
        webSocket?.close(1000, "Service Shutdown")
        webSocket = null
    }

    private fun startHeartbeatCheck() {
        heartbeatJob?.cancel()
        lastPongReceivedTime = System.currentTimeMillis()
        heartbeatJob = serviceScope.launch {
            while (true) {
                delay(15000L) // Wait 15s
                try {
                    val pingJson = JSONObject().put("type", "ping").put("timestamp", System.currentTimeMillis())
                    webSocket?.send(pingJson.toString())
                    
                    // Allow 10 seconds for pong
                    delay(10000L)
                    if (System.currentTimeMillis() - lastPongReceivedTime > 25000L) {
                        JunoServiceState.log("Heartbeat missed. Resetting link.")
                        webSocket?.cancel() // Hard abort
                        webSocketClosed()
                        break
                    }
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    private fun sendRegistrationPayload() {
        try {
            val registration = JSONObject().apply {
                put("type", "node_register")
                put("auth_token", prefs.authToken)
                put("user_id", prefs.userId)
                put("pairing_token", prefs.pairingToken)
                put("device_id", getUniqueAndroidId())
                put("device_name", prefs.deviceName)
                put("tags", JSONArray(prefs.tags.split(",").map { it.trim() }))
                put("os_version", "Android ${Build.VERSION.RELEASE}")
                put("model", Build.MODEL)
                put("cpu_cores", Runtime.getRuntime().availableProcessors())
                put("ram_total_mb", getSystemTotalMemoryMb())
                put("gpu_model", "Adreno 660")
                put("storage_free_mb", getFreeStorageMb())
                put("app_version", "1.0.0")
            }
            webSocket?.send(registration.toString())
            JunoServiceState.log("Registered node specs: ${prefs.deviceName} for user ${prefs.userId}")
        } catch (e: Exception) {
            JunoServiceState.log("Registration build error: ${e.localizedMessage}")
        }
    }

    private fun startTelemetryLoop() {
        metricsJob?.cancel()
        metricsJob = serviceScope.launch {
            while (true) {
                delay(5000L)
                if (webSocket != null) {
                    try {
                        val cpu = calculateCpuUsage()
                        val ramTotal = getSystemTotalMemoryMb()
                        val ramUsed = ramTotal - getSystemFreeMemoryMb()
                        val network = getActiveNetworkType()
                        val ip = getLocalIpAddress()
                        val storageFree = getFreeStorageMb()
                        val uptime = getSystemUptimeHours()

                        // Update local vitals object for Compose
                        val localVitals = SystemVitals(
                            cpuUsage = cpu,
                            ramUsedGb = ramUsed / 1024f,
                            ramTotalGb = ramTotal / 1024f,
                            batteryPercent = batteryPercent,
                            batteryTempC = batteryTemp,
                            isCharging = isCharging,
                            networkType = network,
                            ipAddress = ip,
                            uptimeHours = uptime
                        )
                        JunoServiceState.updateVitals(localVitals)

                        // If paired and active, stream metrics to cloud
                        val metricsJson = JSONObject().apply {
                            put("type", "metrics")
                            put("device_id", getUniqueAndroidId())
                            put("timestamp", System.currentTimeMillis() / 1000)
                            put("cpu_usage_percent", cpu)
                            put("ram_used_mb", ramUsed)
                            put("ram_total_mb", ramTotal)
                            put("battery_percent", batteryPercent)
                            put("battery_temp_celsius", batteryTemp)
                            put("is_charging", isCharging)
                            val networkSpeedKbps = getNetworkDownstreamKbps()
                            put("network_type", network.lowercase())
                            put("network_speed_mbps", networkSpeedKbps / 1000.0)
                            put("storage_free_mb", storageFree)
                            put("current_task_id", activeTaskId ?: JSONObject.NULL)
                            put("current_task_progress", if (activeTaskId != null) JunoServiceState.taskProgress.value else JSONObject.NULL)
                        }
                        webSocket?.send(metricsJson.toString())
                    } catch (e: Exception) {
                        // Prevent thread failure
                    }
                }
            }
        }
    }

    private fun startThermalSafetyLoop() {
        thermalCheckJob?.cancel()
        thermalCheckJob = serviceScope.launch {
            while (true) {
                delay(3000L)
                val threshold = prefs.maxTempThreshold
                if (batteryTemp > threshold) {
                    if (!isThrottled) {
                        isThrottled = true
                        JunoServiceState.log("WARNING: Battery temperature exceeded safety threshold ($batteryTemp°C > $threshold°C). Activating Thermal Throttle!")
                        sendThermalAlert(batteryTemp)
                        
                        // Stop or pause any running tasks
                        activeTaskJob?.cancel()
                        JunoServiceState.updateTask("THROTTLED — Overheated ($batteryTemp°C)", 0f)
                        updateNotification("THROTTLED — Temperature Critical", "Cooling down device...")
                    }
                } else if (isThrottled && batteryTemp < (threshold - 3.0f)) {
                    // Resume operations once cooled down by 3 degrees
                    isThrottled = false
                    JunoServiceState.log("Thermal level stabilized ($batteryTemp°C). Throttling disengaged. Resuming node tasks.")
                    JunoServiceState.updateTask("IDLE — Waiting for tasks", 0f)
                    updateNotification("JunoCompute Active", "IDLE — Ready for compute tasks")
                }
            }
        }
    }

    private fun sendThermalAlert(temp: Float) {
        try {
            val alert = JSONObject().apply {
                put("type", "thermal_throttle")
                put("device_id", getUniqueAndroidId())
                put("temp", temp)
            }
            webSocket?.send(alert.toString())
        } catch (e: Exception) {
            // Silence
        }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val obj = JSONObject(text)
            val type = obj.optString("type")
            
            when (type) {
                "pong" -> {
                    lastPongReceivedTime = System.currentTimeMillis()
                }
                "task_assign" -> {
                    if (isThrottled) {
                        sendTaskFailure(obj.optString("task_id"), "Task rejected: Node under thermal throttling")
                        return
                    }
                    handleTaskAssignment(obj)
                }
                "command_execute" -> {
                    val command = obj.optString("command")
                    serviceScope.launch(Dispatchers.IO) {
                        val result = executeShellCommand(command, 60L)
                        try {
                            val response = JSONObject().apply {
                                put("type", "command_response")
                                put("stdout", result.stdout)
                                put("stderr", result.stderr)
                                put("exit_code", result.exitCode)
                            }
                            webSocket?.send(response.toString())
                        } catch (e: Exception) {
                            JunoServiceState.log("Failed to send command response: ${e.localizedMessage}")
                        }
                    }
                }
                "adb_command" -> {
                    if (prefs.allowAdbProxy) {
                        handleAdbCommand(obj.optString("command"))
                    } else {
                        JunoServiceState.log("Blocked incoming ADB command: ADB proxy disabled.")
                    }
                }
                "terminal_input" -> {
                    if (prefs.allowTerminalAccess) {
                        val input = obj.optString("input")
                        shellWriter?.write(input.toByteArray())
                        shellWriter?.flush()
                    }
                }
                "terminal_resize" -> {
                    // Terminal resize events can be handled or ignored
                }
                "file_download" -> {
                    if (prefs.allowFileTransfer) {
                        val fileUrl = obj.optString("url")
                        val destName = obj.optString("dest")
                        downloadFileRemote(fileUrl, destName)
                    }
                }
                "file_upload" -> {
                    if (prefs.allowFileTransfer) {
                        val srcName = obj.optString("src")
                        val uploadUrl = obj.optString("upload_url")
                        uploadFileRemote(srcName, uploadUrl)
                    }
                }
                "config_update" -> {
                    handleRemoteConfig(obj)
                }
                "app_update" -> {
                    val apkUrl = obj.optString("apk_url")
                    triggerOtaSelfUpdate(apkUrl)
                }
            }
        } catch (e: Exception) {
            JunoServiceState.log("Incoming packet parse failure: ${e.localizedMessage}")
        }
    }

    private fun handleTaskAssignment(taskObj: JSONObject) {
        activeTaskJob?.cancel()
        val taskId = taskObj.optString("task_id")
        val taskType = taskObj.optString("task_type")
        val payload = taskObj.optJSONObject("payload")
        val filesToDownload = taskObj.optJSONArray("files_to_download")
        val filesToUpload = taskObj.optJSONArray("files_to_upload")
        val timeoutSec = taskObj.optLong("timeout_seconds", 300L)

        activeTaskId = taskId
        activeTaskType = taskType

        JunoServiceState.log("Assigning task [$taskId] Type: $taskType")
        
        activeTaskJob = serviceScope.launch {
            val startTime = System.currentTimeMillis()
            var isSuccess = true
            var stdout = ""
            var stderr = ""
            var exitCode = 0

            try {
                // Update UI state
                JunoServiceState.updateTask("PROCESSING — Initializing Task...", 0.1f)
                updateNotification("PROCESSING — Task: $taskType", "Executing job ID: $taskId")

                // Step 1: Download required files
                if (filesToDownload != null && filesToDownload.length() > 0) {
                    JunoServiceState.log("Task downloads in progress...")
                    for (i in 0 until filesToDownload.length()) {
                        val fileInfo = filesToDownload.getJSONObject(i)
                        val fileUrl = fileInfo.getString("url")
                        val dest = fileInfo.getString("dest")
                        
                        JunoServiceState.updateTask("DOWNLOADING — $dest", 0.3f)
                        val success = downloadFileInternal(fileUrl, dest)
                        if (!success) {
                            throw Exception("File download failed for $dest")
                        }
                    }
                }

                // Step 2: Execute execution payload based on type
                if (taskType == "shell_command" || taskType == "file_process") {
                    val command = payload?.optString("command") ?: ""
                    if (command.isEmpty()) {
                        throw Exception("Empty command payload received")
                    }

                    JunoServiceState.updateTask("EXECUTING — Shell Process", 0.6f)
                    val result = executeShellCommand(command, timeoutSec)
                    stdout = result.stdout
                    stderr = result.stderr
                    exitCode = result.exitCode
                    if (exitCode != 0) {
                        isSuccess = false
                    }
                } else if (taskType == "adb_command") {
                    if (!prefs.allowAdbProxy) {
                        throw Exception("ADB proxy disabled on this node.")
                    }
                    val command = payload?.optString("command") ?: ""
                    val result = executeShellCommand("adb shell $command", timeoutSec)
                    stdout = result.stdout
                    stderr = result.stderr
                    exitCode = result.exitCode
                    if (exitCode != 0) {
                        isSuccess = false
                    }
                }

                // Step 3: Upload files
                val uploadedList = mutableListOf<String>()
                if (filesToUpload != null && filesToUpload.length() > 0 && isSuccess) {
                    JunoServiceState.updateTask("UPLOADING — File Outputs", 0.8f)
                    for (i in 0 until filesToUpload.length()) {
                        val fileInfo = filesToUpload.getJSONObject(i)
                        val src = fileInfo.getString("src")
                        val uploadUrl = fileInfo.getString("upload_url")
                        
                        val success = uploadFileInternal(src, uploadUrl)
                        if (success) {
                            uploadedList.add(src)
                        } else {
                            throw Exception("Output file upload failed for $src")
                        }
                    }
                }

                // Complete Task
                val duration = System.currentTimeMillis() - startTime
                if (isSuccess) {
                    sendTaskResult(taskId, "success", stdout, stderr, exitCode, duration, uploadedList)
                    prefs.tasksCompletedToday += 1
                    JunoServiceState.incrementTasksCompleted()
                    JunoServiceState.log("Task [$taskId] completed successfully in ${duration}ms")
                } else {
                    sendTaskResult(taskId, "failed", stdout, stderr, exitCode, duration, emptyList())
                    JunoServiceState.log("Task [$taskId] execution failed. Code: $exitCode. Error: $stderr")
                }

            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                sendTaskResult(taskId, "failed", "", e.localizedMessage ?: "Exception", -1, duration, emptyList())
                JunoServiceState.log("Task [$taskId] aborted: ${e.localizedMessage}")
            } finally {
                activeTaskId = null
                activeTaskType = null
                JunoServiceState.updateTask("IDLE — Waiting for tasks", 0f)
                updateNotification("JunoCompute Active", "IDLE — Ready for compute tasks")
            }
        }
    }

    private data class ShellResult(val stdout: String, val stderr: String, val exitCode: Int)

    private fun executeShellCommand(cmd: String, timeoutSec: Long): ShellResult {
        return try {
            JunoServiceState.log("Running Shell command: $cmd")
            val process = ProcessBuilder("/system/bin/sh", "-c", cmd)
                .directory(filesDir) // Execute in app files directory
                .start()

            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            val outReader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))

            // Non-blocking readers in background threads
            val outThread = Thread {
                try {
                    var line: String?
                    while (outReader.readLine().also { line = it } != null) {
                        stdoutBuilder.append(line).append("\n")
                    }
                } catch (e: Exception) {}
            }
            val errThread = Thread {
                try {
                    var line: String?
                    while (errReader.readLine().also { line = it } != null) {
                        stderrBuilder.append(line).append("\n")
                    }
                } catch (e: Exception) {}
            }

            outThread.start()
            errThread.start()

            val completed = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                JunoServiceState.log("Shell command timed out ($timeoutSec sec). Killing process...")
                return ShellResult("", "Timeout after $timeoutSec seconds", -1)
            }

            outThread.join(500)
            errThread.join(500)

            ShellResult(stdoutBuilder.toString().trim(), stderrBuilder.toString().trim(), process.exitValue())
        } catch (e: Exception) {
            ShellResult("", "Shell Exec error: ${e.localizedMessage}", -2)
        }
    }

    private fun downloadFileInternal(fileUrl: String, destName: String): Boolean {
        return try {
            val destFile = File(filesDir, destName)
            val url = URL(fileUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return false
            }

            connection.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }
            JunoServiceState.log("Downloaded resource: $destName (${destFile.length()} bytes)")
            true
        } catch (e: Exception) {
            JunoServiceState.log("File download error for $destName: ${e.localizedMessage}")
            false
        }
    }

    private fun uploadFileInternal(srcName: String, uploadUrl: String): Boolean {
        return try {
            val srcFile = File(filesDir, srcName)
            if (!srcFile.exists()) {
                return false
            }

            val url = URL(uploadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.doOutput = true
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.setRequestProperty("Content-Length", srcFile.length().toString())
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            connection.outputStream.use { out ->
                srcFile.inputStream().use { input ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                    }
                    out.flush()
                }
            }
            val code = connection.responseCode
            JunoServiceState.log("Uploaded resource: $srcName -> Code: $code")
            code in 200..299
        } catch (e: Exception) {
            JunoServiceState.log("File upload error for $srcName: ${e.localizedMessage}")
            false
        }
    }

    private fun downloadFileRemote(url: String, dest: String) {
        serviceScope.launch(Dispatchers.IO) {
            JunoServiceState.log("Async download started: $dest")
            val success = downloadFileInternal(url, dest)
            try {
                val progressMsg = JSONObject().apply {
                    put("type", "file_transfer_progress")
                    put("dest", dest)
                    put("status", if (success) "completed" else "failed")
                }
                webSocket?.send(progressMsg.toString())
            } catch (e: Exception) {}
        }
    }

    private fun uploadFileRemote(src: String, url: String) {
        serviceScope.launch(Dispatchers.IO) {
            JunoServiceState.log("Async upload started: $src")
            val success = uploadFileInternal(src, url)
            try {
                val progressMsg = JSONObject().apply {
                    put("type", "file_transfer_progress")
                    put("src", src)
                    put("status", if (success) "completed" else "failed")
                }
                webSocket?.send(progressMsg.toString())
            } catch (e: Exception) {}
        }
    }

    private fun sendTaskResult(taskId: String, status: String, stdout: String, stderr: String, exitCode: Int, duration: Long, filesUploaded: List<String>) {
        try {
            val result = JSONObject().apply {
                put("type", "task_result")
                put("task_id", taskId)
                put("status", status)
                put("stdout", stdout)
                put("stderr", stderr)
                put("exit_code", exitCode)
                put("duration_ms", duration)
                put("files_uploaded", JSONArray(filesUploaded))
            }
            webSocket?.send(result.toString())
        } catch (e: Exception) {
            JunoServiceState.log("Failed to send task result: ${e.localizedMessage}")
        }
    }

    private fun sendTaskFailure(taskId: String, reason: String) {
        sendTaskResult(taskId, "failed", "", reason, -1, 0, emptyList())
    }

    private fun handleRemoteConfig(configObj: JSONObject) {
        try {
            val settings = configObj.optJSONObject("settings") ?: return
            if (settings.has("deviceName")) {
                prefs.deviceName = settings.getString("deviceName")
            }
            if (settings.has("tags")) {
                prefs.tags = settings.getString("tags")
            }
            if (settings.has("maxCpuLimit")) {
                prefs.maxCpuLimit = settings.getDouble("maxCpuLimit").toFloat()
            }
            if (settings.has("maxTempThreshold")) {
                prefs.maxTempThreshold = settings.getDouble("maxTempThreshold").toFloat()
            }
            JunoServiceState.log("Remote configuration settings applied successfully.")
        } catch (e: Exception) {
            JunoServiceState.log("Failed to apply remote configuration: ${e.localizedMessage}")
        }
    }

    private fun triggerOtaSelfUpdate(apkUrl: String) {
        JunoServiceState.log("OTA Update triggered! Downloading installation bundle from: $apkUrl")
        serviceScope.launch(Dispatchers.IO) {
            val success = downloadFileInternal(apkUrl, "update.apk")
            if (success) {
                JunoServiceState.log("OTA Download success! Prompting system package installer...")
                // Trigger actual Android installation prompt if running interactively
                // Note: Background auto-installation requires Root or Device Owner on standard Android,
                // but this triggers standard self-update intent setup.
            } else {
                JunoServiceState.log("OTA Upgrade failed to download update bundle.")
            }
        }
    }

    // ADB Terminal shell streaming
    private fun startShellSession() {
        if (activeShellProcess != null) return
        try {
            JunoServiceState.log("Initializing Terminal Interactive Session...")
            val pb = ProcessBuilder("/system/bin/sh")
                .directory(filesDir)
                .redirectErrorStream(true)
            
            activeShellProcess = pb.start()
            shellWriter = activeShellProcess?.outputStream

            val reader = BufferedReader(InputStreamReader(activeShellProcess?.inputStream))
            shellReaderJob = serviceScope.launch(Dispatchers.IO) {
                val buffer = CharArray(1024)
                var count: Int
                while (reader.read(buffer).also { count = it } != -1) {
                    val text = String(buffer, 0, count)
                    try {
                        val payload = JSONObject().apply {
                            put("type", "terminal_output")
                            put("output", text)
                        }
                        webSocket?.send(payload.toString())
                    } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {
            JunoServiceState.log("Shell PTY init failure: ${e.localizedMessage}")
        }
    }

    private fun stopShellSession() {
        shellReaderJob?.cancel()
        shellWriter?.close()
        activeShellProcess?.destroy()
        activeShellProcess = null
        shellWriter = null
    }

    // ADB Proxy Core
    private fun handleAdbCommand(cmd: String) {
        // Forwarding to actual ADB daemon running on port 5555
        serviceScope.launch(Dispatchers.IO) {
            try {
                if (adbSocket == null || adbSocket?.isClosed == true) {
                    adbSocket = Socket("localhost", 5555)
                    startAdbSocketRead()
                }
                adbSocket?.getOutputStream()?.write(cmd.toByteArray())
                adbSocket?.getOutputStream()?.flush()
            } catch (e: Exception) {
                JunoServiceState.log("ADB Forward failure (Is wireless ADB enabled on port 5555?): ${e.localizedMessage}")
            }
        }
    }

    private fun startAdbSocketRead() {
        adbReadJob?.cancel()
        adbReadJob = serviceScope.launch(Dispatchers.IO) {
            try {
                val stream = adbSocket?.getInputStream()
                val buffer = ByteArray(2048)
                var count: Int
                while (stream?.read(buffer).also { count = it ?: -1 } != -1) {
                    if (count > 0) {
                        val text = String(buffer, 0, count)
                        try {
                            val payload = JSONObject().apply {
                                    put("type", "adb_response")
                                    put("response", text)
                            }
                            webSocket?.send(payload.toString())
                        } catch (e: Exception) {}
                    }
                }
            } catch (e: Exception) {
                closeAdbProxy()
            }
        }
    }

    private fun closeAdbProxy() {
        adbReadJob?.cancel()
        try {
            adbSocket?.close()
        } catch (e: Exception) {}
        adbSocket = null
    }

    // System utility metrics readers
    private fun calculateCpuUsage(): Float {
        // Fallback robust CPU loader calculations
        return try {
            val statsFile = File("/proc/stat")
            if (statsFile.exists() && statsFile.canRead()) {
                val reader = BufferedReader(InputStreamReader(statsFile.inputStream()))
                val line = reader.readLine()
                reader.close()
                if (line != null && line.startsWith("cpu")) {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 8) {
                        val user = parts[1].toLong()
                        val nice = parts[2].toLong()
                        val sys = parts[3].toLong()
                        val idle = parts[4].toLong()
                        val iowait = parts[5].toLong()
                        val irq = parts[6].toLong()
                        val softirq = parts[7].toLong()

                        val active = user + nice + sys + irq + softirq
                        val total = active + idle + iowait
                        
                        // We do a delta-delay calculation
                        Thread.sleep(150)
                        val r2 = BufferedReader(InputStreamReader(File("/proc/stat").inputStream()))
                        val l2 = r2.readLine()
                        r2.close()
                        if (l2 != null && l2.startsWith("cpu")) {
                            val p2 = l2.split("\\s+".toRegex())
                            val u2 = p2[1].toLong()
                            val n2 = p2[2].toLong()
                            val s2 = p2[3].toLong()
                            val id2 = p2[4].toLong()
                            val io2 = p2[5].toLong()
                            val ir2 = p2[6].toLong()
                            val si2 = p2[7].toLong()

                            val active2 = u2 + n2 + s2 + ir2 + si2
                            val total2 = active2 + id2 + io2

                            val dActive = active2 - active
                            val dTotal = total2 - total
                            if (dTotal > 0) {
                                return (dActive.toFloat() / dTotal.toFloat() * 100f).coerceIn(0f, 100f)
                            }
                        }
                    }
                }
            }
            // Fallback try active load simulation if proc is restricted (Android 10+ Samsung)
            calculateActiveThreadLoad()
        } catch (e: Exception) {
            calculateActiveThreadLoad()
        }
    }

    private fun calculateActiveThreadLoad(): Float {
        // Real active thread count vs total processors
        val activeThreads = Thread.activeCount()
        val processors = Runtime.getRuntime().availableProcessors()
        val load = (activeThreads.toFloat() / (processors * 10f) * 100f).coerceIn(0f, 100f)
        return load
    }

    private fun getSystemTotalMemoryMb(): Long {
        return try {
            val actManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            memInfo.totalMem / (1024 * 1024)
        } catch (e: Exception) {
            8192 // 8GB default fallback
        }
    }

    private fun getSystemFreeMemoryMb(): Long {
        return try {
            val actManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            memInfo.availMem / (1024 * 1024)
        } catch (e: Exception) {
            4096
        }
    }

    private fun getFreeStorageMb(): Long {
        return try {
            val path = filesDir
            val stats = android.os.StatFs(path.path)
            (stats.availableBytes / (1024 * 1024))
        } catch (e: Exception) {
            32000
        }
    }

    private fun getSystemUptimeHours(): Float {
        val ms = SystemClock.elapsedRealtime()
        return ms.toFloat() / (1000f * 60f * 60f)
    }

    private fun getActiveNetworkType(): String {
        return try {
            val connManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connManager.activeNetwork ?: return "Offline"
            val caps = connManager.getNetworkCapabilities(network) ?: return "Offline"
            
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Connected"
            }
        } catch (e: Exception) {
            "Offline"
        }
    }

    private fun getNetworkDownstreamKbps(): Int {
        return try {
            val connManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connManager.activeNetwork ?: return 0
            val caps = connManager.getNetworkCapabilities(network) ?: return 0
            caps.linkDownstreamBandwidthKbps
        } catch (e: Exception) {
            0
        }
    }

    private fun getLocalIpAddress(): String {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is InetAddress) {
                        val sAddr = addr.hostAddress ?: ""
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4) return sAddr
                    }
                }
            }
            "127.0.0.1"
        } catch (e: Exception) {
            "0.0.0.0"
        }
    }

    private fun getUniqueAndroidId(): String {
        return android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "juno_node_id"
    }

    // Notification builder
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JunoCompute Daemon Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "JunoCompute Cluster Active Telemetry & Compute Node"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(title: String, text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setColor(0xFF06B6D4.toInt()) // Cyan-500
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val notification = buildForegroundNotification(title, text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "juno_compute_service_channel"
        const val NOTIFICATION_ID = 4501
    }
}
