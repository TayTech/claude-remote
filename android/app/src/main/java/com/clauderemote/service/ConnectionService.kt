package com.clauderemote.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.clauderemote.MainActivity
import com.clauderemote.R
import com.clauderemote.data.model.ConnectionState
import com.clauderemote.data.remote.SocketService
import com.clauderemote.data.repository.ClaudeRemoteRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ConnectionService : Service() {

    companion object {
        private const val CHANNEL_ID = "claude_remote_connection"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_DISCONNECT = "com.clauderemote.action.DISCONNECT"
    }

    @Inject
    lateinit var repository: ClaudeRemoteRepository

    @Inject
    lateinit var socketService: SocketService

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var currentHost: String? = null

    inner class LocalBinder : Binder() {
        fun getService(): ConnectionService = this@ConnectionService
        fun getSocketService(): SocketService = socketService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerNetworkCallback()
        observeConnectionState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                disconnect()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, createNotification(ConnectionState.Connecting))

        scope.launch {
            val settings = repository.getServerSettings()
            if (settings != null) {
                currentHost = settings.host
                repository.connect()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterNetworkCallback()
        disconnect()
        scope.cancel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.service_channel_name)
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(state: ConnectionState): Notification {
        val contentText = when (state) {
            is ConnectionState.Connected -> getString(R.string.service_connected_to, currentHost ?: "")
            is ConnectionState.Connecting -> getString(R.string.connecting)
            is ConnectionState.Reconnecting -> getString(R.string.reconnecting)
            is ConnectionState.Disconnected -> getString(R.string.service_disconnected)
            is ConnectionState.Error -> state.message
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, ConnectionService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_delete,
                getString(R.string.service_disconnect_action),
                disconnectIntent
            )
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun observeConnectionState() {
        scope.launch {
            repository.connectionState.collectLatest { state ->
                val notification = createNotification(state)
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun registerNetworkCallback() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                // Network became available, reconnect if disconnected
                scope.launch {
                    if (!repository.isConnected()) {
                        repository.connect()
                    }
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                // Network lost, socket will handle reconnection
            }
        }

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let { callback ->
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(callback)
            networkCallback = null
        }
    }

    private fun disconnect() {
        repository.disconnect()
    }
}
