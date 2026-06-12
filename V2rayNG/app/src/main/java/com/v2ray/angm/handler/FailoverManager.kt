package com.v2ray.angm.handler

import android.content.Context
import android.util.Log
import com.v2ray.angm.AppConfig
import com.v2ray.angm.core.CoreNativeManager
import com.v2ray.angm.core.CoreServiceManager
import com.v2ray.angm.dto.entities.ProfileItem
import com.v2ray.angm.util.JsonUtil
import com.v2ray.angm.util.MessageUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

object FailoverManager {
    private const val TAG = "FailoverManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null
    private var recoveryJob: Job? = null
    private var isStarted = AtomicBoolean(false)

    private val _isFailoverActive = MutableStateFlow(false)
    val isFailoverActive: StateFlow<Boolean> = _isFailoverActive.asStateFlow()

    private val backoffIntervals = listOf(10_000L, 30_000L, 60_000L, 300_000L, 900_000L)
    private var currentBackoffIndex = 0

    fun start(context: Context) {
        if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_FAILOVER_ENABLED)) {
            stop()
            return
        }

        if (isStarted.compareAndSet(false, true)) {
            Log.i(TAG, "Starting FailoverManager")
            startMonitoring(context)
            NetworkStateMonitor.onNetworkChangedListener = { isAvailable, _ ->
                if (isAvailable && isStarted.get()) {
                    Log.i(TAG, "Network restored, re-checking connectivity")
                    monitorJob?.cancel()
                    startMonitoring(context)
                }
            }
        }
    }

    fun stop() {
        if (isStarted.compareAndSet(true, false)) {
            Log.i(TAG, "Stopping FailoverManager")
            NetworkStateMonitor.onNetworkChangedListener = null
            monitorJob?.cancel()
            recoveryJob?.cancel()
            monitorJob = null
            recoveryJob = null
            _isFailoverActive.value = false
        }
    }

    private fun startMonitoring(context: Context) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                delay(30_000) // Check connectivity every 30 seconds
                if (!CoreServiceManager.isRunning()) {
                    Log.d(TAG, "Monitoring: Core is not running, skipping")
                    continue
                }

                val currentGuid = MmkvManager.getSelectServer() ?: continue
                val mainServer = MmkvManager.getMainServer()

                if (mainServer != null && currentGuid == mainServer.first) {
                    _isFailoverActive.value = false
                    Log.d(TAG, "Monitoring: On main server ${mainServer.second.remarks}. Checking connectivity...")
                    // We are on main server, check if it's still alive
                    if (!testConnection(mainServer.second)) {
                        Log.w(TAG, "Main server is down, starting failover")
                        performFailover(context)
                    } else {
                        Log.d(TAG, "Main server is healthy")
                    }
                } else if (mainServer != null) {
                    _isFailoverActive.value = true
                    Log.d(TAG, "Monitoring: Currently on backup server. Recovery task state: ${recoveryJob?.isActive}")
                    // We are on a backup server, ensure recovery job is running
                    startRecoveryTask(context)
                } else {
                    Log.d(TAG, "Monitoring: No main server configured")
                    _isFailoverActive.value = false
                }
            }
        }
    }

    private suspend fun performFailover(context: Context) {
        val favorites = MmkvManager.getFavoriteServers()
        val mainServer = MmkvManager.getMainServer()
        val currentGuid = MmkvManager.getSelectServer()

        // Filter out main server and current server if it's already a favorite
        val candidates = favorites.filter { it.first != mainServer?.first && it.first != currentGuid }

        for (candidate in candidates) {
            Log.i(TAG, "Testing favorite server: ${candidate.second.remarks}")
            if (testConnection(candidate.second)) {
                Log.i(TAG, "Switching to favorite server: ${candidate.second.remarks}")
                withContext(Dispatchers.Main) {
                    // 1. Update the selected server in storage
                    MmkvManager.setSelectServer(candidate.first)
                    
                    // 2. Tell the service to restart itself with the new configuration
                    // This bypasses background start restrictions and ensures the old core is stopped
                    MessageUtil.sendMsg2Service(context, AppConfig.MSG_STATE_HOT_RELOAD, "")
                }
                startRecoveryTask(context)
                return
            }
        }
        Log.e(TAG, "No working favorite servers found for failover")
    }

    private fun startRecoveryTask(context: Context) {
        if (recoveryJob?.isActive == true) return

        recoveryJob = scope.launch {
            currentBackoffIndex = 0
            while (isActive) {
                val mainServer = MmkvManager.getMainServer() ?: break
                
                // Wait for backoff interval
                val delayTime = backoffIntervals[currentBackoffIndex]
                Log.d(TAG, "Recovery: waiting ${delayTime / 1000}s before testing main server")
                delay(delayTime)

                // Hysteresis: 3 consecutive successful pings (3s interval)
                var successCount = 0
                for (i in 1..3) {
                    if (testConnection(mainServer.second)) {
                        successCount++
                        if (successCount < 3) delay(3000)
                    } else {
                        break
                    }
                }

                if (successCount == 3) {
                    Log.i(TAG, "Main server recovered and stable, failing back")
                    withContext(Dispatchers.Main) {
                        // Fail back to main server
                        MmkvManager.setSelectServer(mainServer.first)
                        MessageUtil.sendMsg2Service(context, AppConfig.MSG_STATE_HOT_RELOAD, "")
                    }
                    recoveryJob?.cancel()
                    recoveryJob = null
                    return@launch
                } else {
                    // Increase backoff index
                    if (currentBackoffIndex < backoffIntervals.size - 1) {
                        currentBackoffIndex++
                    }
                }
            }
        }
    }

    private fun testConnection(profile: ProfileItem): Boolean {
        val configJson = JsonUtil.toJson(profile)
        val testUrl = SettingsManager.getDelayTestUrl()
        
        // 1. Try core test first
        val delay = CoreNativeManager.measureOutboundDelay(configJson, testUrl)
        if (delay in 0..10000) {
            Log.d(TAG, "testConnection: Core test success for ${profile.remarks}: ${delay}ms")
            return true
        }
        
        // 2. Fallback to direct socket check
        // Core test might fail due to VPN loopback (core trying to route through the broken VPN).
        // Since our app is excluded from VPN, a direct socket check is more reliable in this state.
        val isReachable = isServerReachable(profile)
        if (isReachable) {
            Log.i(TAG, "testConnection: Core test failed but server ${profile.server} is reachable via socket. Considering it ALIVE.")
        } else {
            Log.w(TAG, "testConnection: Server ${profile.server} is unreachable by both core and socket.")
        }
        
        return isReachable
    }

    private fun isServerReachable(profile: ProfileItem): Boolean {
        return try {
            val socket = java.net.Socket()
            val port = profile.serverPort?.toIntOrNull() ?: 443
            val address = java.net.InetSocketAddress(profile.server, port)
            socket.connect(address, 3000)
            socket.close()
            true
        } catch (e: Exception) {
            Log.d(TAG, "Direct socket reachability failed for ${profile.server}: ${e.message}")
            false
        }
    }
}
