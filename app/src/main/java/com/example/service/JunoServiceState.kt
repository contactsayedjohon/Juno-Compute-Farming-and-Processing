package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SystemVitals(
    val cpuUsage: Float = 0f,
    val ramUsedGb: Float = 0f,
    val ramTotalGb: Float = 0f,
    val batteryPercent: Int = 0,
    val batteryTempC: Float = 0f,
    val isCharging: Boolean = false,
    val networkType: String = "Offline",
    val ipAddress: String = "0.0.0.0",
    val uptimeHours: Float = 0f
)

object JunoServiceState {
    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning = _isServiceRunning.asStateFlow()

    private val _taskStatus = MutableStateFlow("IDLE — Waiting for tasks")
    val taskStatus = _taskStatus.asStateFlow()

    private val _taskProgress = MutableStateFlow(0f)
    val taskProgress = _taskProgress.asStateFlow()

    private val _tasksCompletedToday = MutableStateFlow(0)
    val tasksCompletedToday = _tasksCompletedToday.asStateFlow()

    private val _vitals = MutableStateFlow(SystemVitals())
    val vitals = _vitals.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun setConnected(connected: Boolean) {
        _isConnected.value = connected
        log(if (connected) "WebSocket Connected to Cloud Dashboard" else "WebSocket Disconnected. Reconnecting...")
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun updateTask(status: String, progress: Float) {
        _taskStatus.value = status
        _taskProgress.value = progress
    }

    fun incrementTasksCompleted() {
        _tasksCompletedToday.value += 1
    }

    fun setTasksCompleted(count: Int) {
        _tasksCompletedToday.value = count
    }

    fun updateVitals(vitals: SystemVitals) {
        _vitals.value = vitals
    }

    fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val logLine = "[$timestamp] $message"
        val currentList = _logs.value.toMutableList()
        currentList.add(logLine)
        if (currentList.size > 500) {
            currentList.removeAt(0)
        }
        _logs.value = currentList
    }

    fun clearLogs() {
        _logs.value = emptyList()
        log("Local task cache and log buffer cleared.")
    }
}
