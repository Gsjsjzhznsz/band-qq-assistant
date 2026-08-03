package com.example.bandqq.sync

import com.example.bandqq.sync.SyncState
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 手环互联桥：抽象蓝牙互联通道（厂商 SDK 实现），负责：
 * - 将同步器消息（bandSender）发往手环
 * - 接收手环帧，转交 MessageBroker.onBandFrame
 * - 连接状态维护与 SyncState.bandConnected 同步
 *
 * 底层通道依赖厂商互联 SDK，接入处见 [initInterconnect]（需在对应厂商 SDK 可用时实现）。
 */
object InterconnectBridge {

    private var broker: MessageBroker? = null

    @Volatile
    var available: Boolean = false
        private set

    fun register(broker: MessageBroker) {
        this.broker = broker
        broker.bandSender = { frame -> sendToBand(frame) }
        SyncState.bandConnected = false
    }

    fun unregister(broker: MessageBroker) {
        if (this.broker === broker) this.broker = null
        SyncState.bandConnected = false
    }

    /** 将同步器产生的 JSON 帧发送到已连接的手环 */
    fun sendToBand(frame: String) {
        if (!SyncState.bandConnected) return
        // 调用厂商 SDK 发送。若 SDK 未注入，此为空实现。
    }

    /** 手环消息回调：由底层 SDK 在收到手环帧时调用 */
    fun onBandMessage(json: String) {
        val b = broker ?: return
        b.onBandFrame(json)
    }

    fun onConnect() {
        SyncState.bandConnected = true
        broker?.bandSender?.invoke(buildStateFrame(true))
    }

    fun onDisconnect() {
        SyncState.bandConnected = false
        broker?.bandSender?.invoke(buildStateFrame(false))
    }

    private fun buildStateFrame(connected: Boolean): String {
        val obj = JsonObject()
        obj.addProperty("type", "connect_state")
        obj.addProperty("seq", 0)
        obj.addProperty("state", if (connected) "connected" else "disconnected")
        return obj.toString()
    }

    /** 初始化厂商互联 SDK 通道；在对应 SDK 接入完成后调用 [onConnect] */
    fun initInterconnect() {
        if (available) return
        // 接入点：初始化互联 SDK，注册消息接收回调 -> onBandMessage(json)
        // 连接建立/断开回调 -> onConnect()/onDisconnect()
        // 此处为占位，需在目标厂商 SDK 环境下填充。
        available = true
    }
}
