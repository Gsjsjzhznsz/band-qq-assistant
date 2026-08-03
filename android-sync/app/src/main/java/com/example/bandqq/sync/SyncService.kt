package com.example.bandqq.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.example.bandqq.R
import com.example.bandqq.config.ConfigManager
import com.example.bandqq.onebot.OneBotClient
import com.example.bandqq.onebot.OneBotListener
import com.example.bandqq.onebot.OneBotMessage
import com.example.bandqq.onebot.OneBotParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

object SyncState {
    @Volatile var oneBotConnected: Boolean = false
    @Volatile var bandConnected: Boolean = false
}

class SyncService : Service() {

    companion object {
        const val ACTION_START = "com.example.bandqq.action.START"
        const val ACTION_STOP = "com.example.bandqq.action.STOP"
        private const val CHANNEL_ID = "sync_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SyncService::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, SyncService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var configManager: ConfigManager
    private lateinit var parser: OneBotParser
    private lateinit var oneBot: OneBotClient
    private lateinit var broker: MessageBroker

    override fun onCreate() {
        super.onCreate()
        createChannel()
        configManager = ConfigManager(this)
        parser = OneBotParser()
        oneBot = OneBotClient(parser)
        broker = MessageBroker(parser, oneBot, MessageStore())
        oneBot.startWithListener(broker)
        InterconnectBridge.register(broker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                scope.launch {
                    val config = configManager.load()
                    oneBot.start(config, broker)
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        oneBot.stop()
        InterconnectBridge.unregister(broker)
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "同步器服务", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("QQ同步器")
            .setContentText("同步器运行中")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }
}
