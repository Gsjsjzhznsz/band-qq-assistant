package com.example.bandqq.sync

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.xiaomi.xms.wearable.Wearable
import com.xiaomi.xms.wearable.auth.AuthApi
import com.xiaomi.xms.wearable.auth.Permission
import com.xiaomi.xms.wearable.message.MessageApi
import com.xiaomi.xms.wearable.message.OnMessageReceivedListener
import com.xiaomi.xms.wearable.node.Node
import com.xiaomi.xms.wearable.node.NodeApi
import java.nio.charset.StandardCharsets

/**
 * 手环互联桥：基于小米 xms-wearable-lib 实现手环 <-> 手机互联通道。
 * 连接流程（参考弦电子书）：
 *   1. getNodeApi.connectedNodes 发现已通过运动健康连接的手环
 *   2. authApi 申请 DEVICE_MANAGER 权限（触发运动健康授权）
 *   3. launchWearApp 拉起手环端应用页面
 *   4. messageApi.addListener 注册接收手环帧
 *   5. sendMessage 向手环下发帧
 */
object InterconnectBridge {

    private const val TAG = "InterconnectBridge"

    /** 手环端 manifest 路由，用于 launchWearApp 拉起入口页 */
    const val WEAR_ENTRY_ROUTE = "/pages/index"

    private var broker: MessageBroker? = null
    private var context: Context? = null

    private var nodeApi: NodeApi? = null
    private var authApi: AuthApi? = null
    private var messageApi: MessageApi? = null

    @Volatile
    private var currentNode: Node? = null

    @Volatile
    var available: Boolean = false
        private set

    private val messageListener = OnMessageReceivedListener { _, data ->
        try {
            val json = String(data, StandardCharsets.UTF_8)
            Log.d(TAG, "onBandMessage: $json")
            broker?.onBandFrame(json)
        } catch (e: Exception) {
            Log.e(TAG, "onBandMessage parse error", e)
        }
    }

    fun register(broker: MessageBroker) {
        this.broker = broker
        broker.bandSender = { frame -> sendToBand(frame) }
        SyncState.bandConnected = false
    }

    fun unregister(broker: MessageBroker) {
        if (this.broker === broker) this.broker = null
        release()
    }

    /**
     * 初始化互联 SDK 通道并建立连接。
     * 需在 Service/Activity 持有 Context 时调用（连接状态通过 SyncState 暴露）。
     */
    fun init(context: Context) {
        this.context = context.applicationContext
        if (available) return
        available = true
        try {
            nodeApi = Wearable.getNodeApi(context)
            authApi = Wearable.getAuthApi(context)
            messageApi = Wearable.getMessageApi(context)
        } catch (e: Throwable) {
            Log.e(TAG, "init xms-wearable-lib failed", e)
            available = false
        }
    }

    /** 异步建立与手环的互联连接：发现设备 -> 鉴权 -> 拉起应用 -> 注册监听 */
    fun connect() {
        val nodeApi = nodeApi ?: run {
            Log.e(TAG, "connect: not initialized")
            SyncState.bandConnected = false
            BandStateBus.notify(false)
            return
        }
        nodeApi.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    Log.w(TAG, "connect: 未发现已连接的手环，请确认小米运动健康已连接手环")
                    SyncState.bandConnected = false
                    BandStateBus.notify(false)
                    return@addOnSuccessListener
                }
                currentNode = nodes[0]
                Log.d(TAG, "connect: found device ${nodes[0].name}")
                auth(nodes[0])
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "connect: getConnectedNodes failed", e)
                SyncState.bandConnected = false
                BandStateBus.notify(false)
            }
    }

    private fun auth(node: Node) {
        val authApi = authApi ?: return
        authApi.checkPermissions(node.id, arrayOf(Permission.DEVICE_MANAGER))
            .addOnSuccessListener { results ->
                var needRequest = false
                for ((_, value) in results.withIndex()) {
                    if (!value) {
                        authApi.requestPermission(node.id, Permission.DEVICE_MANAGER)
                            .addOnFailureListener { e -> Log.e(TAG, "auth request failed", e) }
                        needRequest = true
                    }
                }
                if (needRequest) {
                    Log.d(TAG, "auth: 已请求 DEVICE_MANAGER 权限，等待用户在运动健康中授权")
                }
                // 无论本次结果如何，继续尝试后续流程；连接真正可用取决于授权完成
                openApp(node)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "auth check failed", e)
                openApp(node)
            }
    }

    private fun openApp(node: Node) {
        val nodeApi = nodeApi ?: return
        nodeApi.launchWearApp(node.id, WEAR_ENTRY_ROUTE)
            .addOnSuccessListener {
                Log.d(TAG, "openApp: 已在手环上拉起应用")
                registerListener(node)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "openApp failed", e)
                // 手环端可能未安装应用或未在前台；仍注册监听以便手环侧主动连接
                registerListener(node)
            }
    }

    private fun registerListener(node: Node) {
        val messageApi = messageApi ?: return
        messageApi.addListener(node.id, messageListener)
            .addOnSuccessListener {
                Log.d(TAG, "registerListener ok")
                onConnect()
            }
            .addOnFailureListener { error ->
                val msg = error.message.orEmpty()
                if (msg.contains("You have registered", ignoreCase = true)) {
                    Log.w(TAG, "listener already registered, continue")
                    onConnect()
                } else {
                    Log.e(TAG, "registerListener failed", error)
                    SyncState.bandConnected = false
                    BandStateBus.notify(false)
                }
            }
    }

    /** 将同步器产生的 JSON 帧发送到已连接的手环 */
    fun sendToBand(frame: String) {
        val node = currentNode ?: return
        val messageApi = messageApi ?: return
        if (!SyncState.bandConnected) return
        messageApi.sendMessage(node.id, frame.toByteArray(StandardCharsets.UTF_8))
            .addOnSuccessListener { Log.d(TAG, "sendToBand ok") }
            .addOnFailureListener { e -> Log.e(TAG, "sendToBand failed", e) }
    }

    /** 手环消息回调：由 SDK 在收到手环帧时调用 */
    fun onBandMessage(json: String) {
        val b = broker ?: return
        b.onBandFrame(json)
    }

    fun onConnect() {
        SyncState.bandConnected = true
        BandStateBus.notify(true)
        broker?.bandSender?.invoke(buildStateFrame(true))
    }

    fun onDisconnect() {
        SyncState.bandConnected = false
        BandStateBus.notify(false)
        broker?.bandSender?.invoke(buildStateFrame(false))
    }

    private fun buildStateFrame(connected: Boolean): String {
        val obj = JsonObject()
        obj.addProperty("type", "connect_state")
        obj.addProperty("seq", 0)
        obj.addProperty("state", if (connected) "connected" else "disconnected")
        return obj.toString()
    }

    fun release() {
        val node = currentNode
        val messageApi = messageApi
        if (node != null && messageApi != null) {
            messageApi.removeListener(node.id)
                .addOnSuccessListener { SyncState.bandConnected = false }
                .addOnFailureListener { SyncState.bandConnected = false }
        }
        currentNode = null
    }
}
