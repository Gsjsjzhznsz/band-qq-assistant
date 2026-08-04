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

/** 全局可访问的数据仓（手机端为主存储），供服务与界面共享 */
object StoreHolder {
    @Volatile var store: MessageStore? = null
        private set

    fun setStore(s: MessageStore) {
        store = s
    }
}

/** 手环连接状态变化回调，供界面刷新 */
object BandStateBus {
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(Boolean) -> Unit>()

    fun add(listener: (Boolean) -> Unit) {
        listeners.add(listener)
    }

    fun remove(listener: (Boolean) -> Unit) {
        listeners.remove(listener)
    }

    fun notify(connected: Boolean) {
        for (l in listeners) {
            try {
                l(connected)
            } catch (_: Exception) {
            }
        }
    }
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
        val store = MessageStore(SyncPreferencesKv(this))
        StoreHolder.setStore(store)
        broker = MessageBroker(parser, oneBot, store)
        oneBot.startWithListener(broker)
        InterconnectBridge.register(broker)
        InterconnectBridge.init(this)
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
                    InterconnectBridge.connect()
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
