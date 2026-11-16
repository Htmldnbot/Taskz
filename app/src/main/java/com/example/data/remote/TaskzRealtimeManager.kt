package com.example.data.remote

import android.util.Log
import com.example.core.model.OrgScope
import com.example.core.network.SupabaseConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

enum class RealtimeEventType {
    TASK_CHANGED,
    INCIDENT_CHANGED,
    NOTIFICATION_CREATED,
    ROLE_OR_SCOPE_CHANGED
}

enum class ChannelJoinState {
    JOINING,
    JOINED,
    ERROR
}

data class RealtimeSignal(
    val type: RealtimeEventType,
    val table: String,
    val recordId: String? = null,
    val commitTimestamp: String? = null
)

/**
 * Lifecycle-aware Supabase Realtime (Phoenix v2 WebSocket) manager.
 * - Connects ONLY while app is active in foreground and session is authenticated.
 * - Confirms channel `phx_reply` joins before marking realtime ready/connected.
 * - Scopes subscriptions by user ID and active OrgScope where unambiguous.
 * - Deduplicates repeated realtime payloads with a bounded LRU event cache.
 * - Uses jittered exponential backoff and recovers cleanly on network restoration / token refresh.
 */
class TaskzRealtimeManager(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "TaskzRealtime"
        private const val MAX_DEDUP_SIZE = 512
        private const val HEARTBEAT_INTERVAL_MS = 25_000L
        private const val MAX_RECONNECT_ATTEMPTS = 6
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refCounter = AtomicInteger(1)
    private val recentEventKeys = LinkedHashSet<String>()
    private val pendingJoinTopicsByRef = mutableMapOf<String, String>()
    private val _channelJoinStates = MutableStateFlow<Map<String, ChannelJoinState>>(emptyMap())
    val channelJoinStates: StateFlow<Map<String, ChannelJoinState>> = _channelJoinStates.asStateFlow()

    private val _lastChannelError = MutableStateFlow<String?>(null)
    val lastChannelError: StateFlow<String?> = _lastChannelError.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _signals = MutableSharedFlow<RealtimeSignal>(extraBufferCapacity = 64)
    val signals: SharedFlow<RealtimeSignal> = _signals.asSharedFlow()

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var socketOpen: Boolean = false
    @Volatile private var heartbeatJob: Job? = null
    @Volatile private var reconnectJob: Job? = null
    @Volatile private var currentAccessToken: String? = null
    @Volatile private var currentUserId: String? = null
    @Volatile private var currentRoleCode: String = "IT_MANAGER"
    @Volatile private var currentOrgScope: OrgScope? = null
    @Volatile private var isForeground: Boolean = false
    @Volatile private var reconnectAttempt: Int = 0
    @Volatile private var authRejected: Boolean = false

    fun setForegroundState(foreground: Boolean) {
        isForeground = foreground
        if (foreground) {
            if (!authRejected) {
                reconnectAttempt = 0
                connectIfEligible()
            }
        } else {
            disconnect(clearSession = false)
        }
    }

    fun recoverConnectionIfEligible() {
        if (!isForeground || authRejected) return
        if (webSocket == null) {
            reconnectAttempt = 0
            connectIfEligible()
        }
    }

    fun onNetworkUnavailable() {
        disconnect(clearSession = false)
    }

    fun updateSessionAndRole(
        accessToken: String?,
        userId: String?,
        activeRoleCode: String,
        orgScope: OrgScope? = currentOrgScope
    ) {
        val changed = currentAccessToken != accessToken ||
            currentUserId != userId ||
            currentRoleCode != activeRoleCode ||
            currentOrgScope != orgScope
        currentAccessToken = accessToken
        currentUserId = userId
        currentRoleCode = activeRoleCode
        currentOrgScope = orgScope
        authRejected = false
        reconnectAttempt = 0
        if (accessToken.isNullOrBlank() || userId.isNullOrBlank() || accessToken.startsWith("offline_")) {
            disconnect(clearSession = true)
            return
        }
        if (changed && isForeground) {
            disconnect(clearSession = false)
            connectIfEligible()
        }
    }

    fun refreshSubscriptionsForRoleSwitch(activeRoleCode: String, orgScope: OrgScope? = currentOrgScope) {
        currentRoleCode = activeRoleCode
        currentOrgScope = orgScope
        reconnectAttempt = 0
        if (isForeground && !currentAccessToken.isNullOrBlank() && !currentUserId.isNullOrBlank()) {
            disconnect(clearSession = false)
            connectIfEligible()
        }
    }

    fun disconnect(clearSession: Boolean = true) {
        heartbeatJob?.cancel()
        heartbeatJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        socketOpen = false
        try {
            webSocket?.close(1000, "Lifecycle/Logout disconnect")
        } catch (_: Exception) {}
        webSocket = null
        synchronized(pendingJoinTopicsByRef) { pendingJoinTopicsByRef.clear() }
        _channelJoinStates.value = emptyMap()
        _isConnected.value = false
        if (clearSession) {
            currentAccessToken = null
            currentUserId = null
            currentOrgScope = null
            _lastChannelError.value = null
            synchronized(recentEventKeys) { recentEventKeys.clear() }
        }
    }

    private fun connectIfEligible() {
        val token = currentAccessToken ?: return
        val uid = currentUserId ?: return
        if (!isForeground || token.startsWith("offline_") || !SupabaseConfig.isConfigured || webSocket != null) {
            return
        }

        val httpUrl = SupabaseConfig.getActiveUrl().trimEnd('/')
        val wsUrl = httpUrl
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") +
            "/realtime/v1/websocket?apikey=${SupabaseConfig.getActiveKey()}&vsn=1.0.0"

        val request = Request.Builder().url(wsUrl).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Realtime socket opened for user=$uid role=$currentRoleCode; awaiting phx_reply joins")
                socketOpen = true
                reconnectAttempt = 0
                _lastChannelError.value = null
                subscribeUserChannels(webSocket, token, uid)
                startHeartbeat(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingFrame(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                Log.w(TAG, "Realtime failure (code=$code): ${t.message}")
                this@TaskzRealtimeManager.webSocket = null
                socketOpen = false
                _isConnected.value = false
                _channelJoinStates.value = emptyMap()
                heartbeatJob?.cancel()
                if (code == 401 || code == 403) {
                    authRejected = true
                    _lastChannelError.value = "Realtime unauthorized (HTTP $code)"
                    return
                }
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                this@TaskzRealtimeManager.webSocket = null
                socketOpen = false
                _isConnected.value = false
                _channelJoinStates.value = emptyMap()
                heartbeatJob?.cancel()
                if (isForeground && code != 1000 && !authRejected) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun resolveScopedFilter(): String? {
        val sc = currentOrgScope ?: return null
        return when (currentRoleCode.uppercase()) {
            "SUPER_ADMIN", "COMMAND_CENTER" -> null
            "ZOM" -> sc.zoneId?.let { "zone_id=eq.$it" }
            "ROM" -> sc.stateId?.let { "state_id=eq.$it" }
            "CITY_HEAD" -> sc.cityId?.let { "city_id=eq.$it" }
            "VCO" -> sc.cityId?.let { "city_id=eq.$it" } ?: sc.centreId?.let { "centre_id=eq.$it" }
            "IT_MANAGER" -> sc.centreId?.let { "centre_id=eq.$it" }
            else -> sc.centreId?.let { "centre_id=eq.$it" }
        }
    }

    private fun subscribeUserChannels(ws: WebSocket, token: String, uid: String) {
        val scopeFilter = resolveScopedFilter()
        val specs = listOf(
            Triple("realtime:notifications_$uid", "notifications", "user_id=eq.$uid"),
            Triple("realtime:user_roles_$uid", "user_roles", "user_id=eq.$uid"),
            Triple("realtime:profiles_$uid", "profiles", "id=eq.$uid"),
            Triple("realtime:tasks_$currentRoleCode", "tasks", scopeFilter),
            Triple("realtime:incidents_$currentRoleCode", "system_incidents", scopeFilter)
        )
        val initialStates = mutableMapOf<String, ChannelJoinState>()
        synchronized(pendingJoinTopicsByRef) {
            pendingJoinTopicsByRef.clear()
            specs.forEach { (topic, table, filter) ->
                val ref = refCounter.getAndIncrement().toString()
                pendingJoinTopicsByRef[ref] = topic
                initialStates[topic] = ChannelJoinState.JOINING

                val pgChange = JSONObject().apply {
                    put("event", "*")
                    put("schema", "public")
                    put("table", table)
                    if (!filter.isNullOrBlank()) put("filter", filter)
                }
                val payload = JSONObject().apply {
                    put("config", JSONObject().apply {
                        put("postgres_changes", JSONArray().put(pgChange))
                    })
                    put("access_token", token)
                }
                val frame = JSONArray().apply {
                    put("1")
                    put(ref)
                    put(topic)
                    put("phx_join")
                    put(payload)
                }
                ws.send(frame.toString())
            }
        }
        _channelJoinStates.value = initialStates
    }

    private fun startHeartbeat(ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isForeground && webSocket == ws) {
                delay(HEARTBEAT_INTERVAL_MS)
                val hb = JSONArray().apply {
                    put("1")
                    put(refCounter.getAndIncrement().toString())
                    put("phoenix")
                    put("heartbeat")
                    put(JSONObject())
                }
                runCatching { ws.send(hb.toString()) }
            }
        }
    }

    private fun scheduleReconnect() {
        if (!isForeground || authRejected || currentAccessToken.isNullOrBlank()) return
        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            _lastChannelError.value = "Realtime paused after max retries; waiting for network/foreground recovery"
            return
        }
        reconnectJob?.cancel()
        val attempt = reconnectAttempt++
        val baseDelay = minOf(60_000L, 1_500L shl attempt)
        val jitter = Random.nextLong(250L, 1_250L)
        reconnectJob = scope.launch {
            delay(baseDelay + jitter)
            connectIfEligible()
        }
    }

    fun handleIncomingFrame(rawFrame: String): RealtimeSignal? {
        return try {
            val arr = JSONArray(rawFrame)
            if (arr.length() < 5) return null
            val ref = arr.optString(1)
            val topic = arr.optString(2)
            val event = arr.optString(3)
            val payload = arr.optJSONObject(4) ?: return null

            when (event) {
                "phx_reply" -> {
                    val joinedTopic = synchronized(pendingJoinTopicsByRef) {
                        pendingJoinTopicsByRef.remove(ref)
                    } ?: topic.takeIf { it.startsWith("realtime:") }
                    if (joinedTopic != null) {
                        val status = payload.optString("status")
                        val updated = _channelJoinStates.value.toMutableMap()
                        if (status.equals("ok", ignoreCase = true)) {
                            updated[joinedTopic] = ChannelJoinState.JOINED
                            _channelJoinStates.value = updated
                            _isConnected.value = updated.values.any { it == ChannelJoinState.JOINED }
                        } else {
                            updated[joinedTopic] = ChannelJoinState.ERROR
                            _channelJoinStates.value = updated
                            val reason = payload.optJSONObject("response")?.optString("reason")
                                ?: payload.optString("response")
                            _lastChannelError.value = "Channel join rejected ($joinedTopic): $reason"
                            Log.w(TAG, "Channel join rejected: topic=$joinedTopic reason=$reason")
                            if (reason.contains("unauthorized", true) || reason.contains("jwt", true)) {
                                authRejected = true
                            }
                            _isConnected.value = updated.values.any { it == ChannelJoinState.JOINED }
                        }
                    }
                    return null
                }
                "phx_error", "phx_close" -> {
                    if (topic.startsWith("realtime:")) {
                        val updated = _channelJoinStates.value.toMutableMap()
                        updated[topic] = ChannelJoinState.ERROR
                        _channelJoinStates.value = updated
                        _lastChannelError.value = "Channel $event on $topic"
                        _isConnected.value = updated.values.any { it == ChannelJoinState.JOINED }
                    }
                    return null
                }
                "postgres_changes" -> {
                    val data = payload.optJSONObject("data") ?: return null
                    val table = data.optString("table")
                    val commitTs = data.optString("commit_timestamp")
                    val record = data.optJSONObject("record") ?: data.optJSONObject("old_record")
                    val recordId = record?.optString("id")

                    val dedupKey = "$table|$recordId|$commitTs|${data.optString("type")}"
                    if (!markSeenIfNew(dedupKey)) {
                        return null
                    }

                    val signalType = when (table) {
                        "notifications" -> RealtimeEventType.NOTIFICATION_CREATED
                        "user_roles", "profiles" -> RealtimeEventType.ROLE_OR_SCOPE_CHANGED
                        "system_incidents" -> RealtimeEventType.INCIDENT_CHANGED
                        else -> RealtimeEventType.TASK_CHANGED
                    }
                    val signal = RealtimeSignal(
                        type = signalType,
                        table = table,
                        recordId = recordId,
                        commitTimestamp = commitTs
                    )
                    _signals.tryEmit(signal)
                    return signal
                }
                else -> return null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun markSeenIfNew(key: String): Boolean = synchronized(recentEventKeys) {
        if (recentEventKeys.contains(key)) return false
        if (recentEventKeys.size >= MAX_DEDUP_SIZE) {
            recentEventKeys.firstOrNull()?.let { recentEventKeys.remove(it) }
        }
        recentEventKeys.add(key)
        true
    }
}
