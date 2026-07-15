package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import com.example.service.JunoComputeService
import com.example.service.JunoServiceState
import com.example.utils.PreferencesHelper
import kotlinx.coroutines.flow.StateFlow

class JunoViewModel(application: Application) : AndroidViewModel(application) {
    
    val prefs = PreferencesHelper(application)

    // Expose state flows from JunoServiceState
    val isConnected: StateFlow<Boolean> = JunoServiceState.isConnected
    val isServiceRunning: StateFlow<Boolean> = JunoServiceState.isServiceRunning
    val taskStatus: StateFlow<String> = JunoServiceState.taskStatus
    val taskProgress: StateFlow<Float> = JunoServiceState.taskProgress
    val tasksCompletedToday: StateFlow<Int> = JunoServiceState.tasksCompletedToday
    val vitals = JunoServiceState.vitals
    val logs = JunoServiceState.logs

    fun pairNode(url: String, token: String, deviceName: String, tags: String) {
        prefs.serverUrl = url
        prefs.pairingToken = token
        prefs.deviceName = deviceName
        prefs.tags = tags
        prefs.isPaired = true
        
        // Auto launch foreground service after pairing
        startWorkerService()
    }

    fun login(email: String, onSuccess: () -> Unit) {
        prefs.userEmail = email
        // Generate simulated user_id and auth_token for SaaS experience
        prefs.userId = "usr_" + email.split("@")[0].replace(".", "_") + "_" + (1000..9999).random()
        prefs.authToken = "auth_tok_" + (100000..999999).random()
        onSuccess()
    }

    fun signUp(email: String, onSuccess: () -> Unit) {
        prefs.userEmail = email
        prefs.userId = "usr_" + email.split("@")[0].replace(".", "_") + "_" + (1000..9999).random()
        prefs.authToken = "auth_tok_" + (100000..999999).random()
        onSuccess()
    }

    fun pairWithJson(jsonStr: String): Boolean {
        return try {
            val obj = org.json.JSONObject(jsonStr)
            val serverUrl = obj.getString("server_url")
            val pairingToken = obj.getString("pairing_token")
            val userId = obj.getString("user_id")

            prefs.serverUrl = serverUrl
            prefs.pairingToken = pairingToken
            prefs.userId = userId
            prefs.isPaired = true

            startWorkerService()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun pairWithCode(code: String): Boolean {
        // Simple mock validation of 6-digit code
        if (code.length == 6 && code.all { it.isDigit() }) {
            prefs.serverUrl = "wss://cluster.junoverseai.com/ws/node"
            prefs.pairingToken = "usr_${prefs.userId}_pair_${code}"
            prefs.isPaired = true

            startWorkerService()
            return true
        }
        return false
    }

    fun logout() {
        stopWorkerService()
        prefs.authToken = ""
        prefs.userId = ""
        prefs.userEmail = ""
        prefs.isPaired = false
        prefs.pairingToken = ""
        JunoServiceState.setConnected(false)
        JunoServiceState.clearLogs()
    }

    fun unlinkDevice() {
        stopWorkerService()
        prefs.isPaired = false
        prefs.pairingToken = ""
        JunoServiceState.setConnected(false)
        JunoServiceState.clearLogs()
    }

    fun unpairNode() {
        unlinkDevice()
    }

    fun startWorkerService() {
        val context = getApplication<Application>()
        val intent = Intent(context, JunoComputeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopWorkerService() {
        val context = getApplication<Application>()
        val intent = Intent(context, JunoComputeService::class.java)
        context.stopService(intent)
    }

    fun clearTaskCache() {
        JunoServiceState.clearLogs()
    }

    fun updateDeviceIdentity(name: String, tags: String) {
        prefs.deviceName = name
        prefs.tags = tags
        // Log changes
        JunoServiceState.log("Device identity updated locally. Name: $name, Tags: $tags")
    }

    fun updateToggle(key: String, enabled: Boolean) {
        when (key) {
            "auto_start" -> prefs.autoStartOnBoot = enabled
            "keep_screen" -> prefs.keepScreenOn = enabled
            "adb_proxy" -> prefs.allowAdbProxy = enabled
            "terminal" -> prefs.allowTerminalAccess = enabled
            "file_transfer" -> prefs.allowFileTransfer = enabled
            "charge_only" -> prefs.chargeOnlyMode = enabled
            "accessibility" -> prefs.allowAccessibility = enabled
        }
    }

    fun updateCpuLimit(limit: Float) {
        prefs.maxCpuLimit = limit
    }

    fun updateTempThreshold(threshold: Float) {
        prefs.maxTempThreshold = threshold
    }
}
