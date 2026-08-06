package com.example.bandqq.sync

import com.example.bandqq.onebot.OneBotMessage
import com.example.bandqq.onebot.OneBotParser
import com.google.gson.JsonParser

interface MessageSender {
    fun sendMessage(
        messageType: String,
        targetId: String,
        content: String,
        httpUrlOverride: String? = null,
        callback: (Boolean) -> Unit = {}
    )
}

class MessageBroker(
    private val parser: OneBotParser,
    private val oneBot: MessageSender,
    private val store: MessageStore,
    private val autoFetch: ((MessageStore) -> Unit)? = null
) : com.example.bandqq.onebot.OneBotListener {

    var autoFetchDone = false

    var bandSender: (String) -> Unit = {}

    /** 手环 pong 心跳应答回调，由互联层在收到 pong 时调用以确认手环在线。 */
    var onBandPong: () -> Unit = {}

    fun onBandFrame(json: String): Boolean {
        val obj = try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: Exception) {
            return false
        }
        val type = obj.get("type")?.asString ?: return false
        val seq = obj.get("seq")?.asInt ?: 0
        when (type) {
            "pong" -> {
                onBandPong()
                return true
            }
            "band_state" -> {
                onBandPong()
                return true
            }
            "send_message" -> {
                val messageType = obj.get("message_type")?.asString ?: "private"
                val targetId = obj.get("target_id")?.asString ?: return false
                val content = obj.get("content")?.asString ?: ""
                oneBot.sendMessage(messageType, targetId, content)
                return true
            }
            "get_history" -> {
                val targetId = obj.get("target_id")?.asString ?: return false
                val limit = obj.get("limit")?.asInt ?: 20
                bandSender(store.buildHistoryFrame(targetId, limit, seq))
                return true
            }
            "get_conversations" -> {
                bandSender(store.buildConversationFrame(seq))
                return true
            }
            "clear_all_history" -> {
                store.clearAllHistory()
                return true
            }
            "get_visible_contacts" -> {
                val frame = store.buildVisibleContactsFrame(seq)
                log("get_visible_contacts -> ${store.getVisibleContacts().size} contacts")
                bandSender(frame)
                return true
            }
            "get_connect_state" -> {
                bandSender(SyncStatePush.buildFrame())
                return true
            }
            else -> return false
        }
    }

    fun handleOneBotEvent(msg: OneBotMessage): String? {
        store.addMessage(
            msg.targetId,
            StoredMessage(
                messageType = msg.messageType,
                senderId = msg.senderId,
                senderName = msg.senderName,
                content = msg.content,
                time = msg.time,
                isSelf = msg.isSelf
            )
        )
        MessageBus.notify(msg.targetId)
        val visible = store.isVisibleContact(msg.targetId)
        val targetName = store.conversationName(msg.targetId, msg.messageType, msg.senderName)
        return parser.toHandBandFrame(msg, visible, targetName)
    }

    override fun onEvent(message: OneBotMessage) {
        val frame = handleOneBotEvent(message) ?: return
        bandSender(frame)
    }

    override fun onState(connected: Boolean) {
        SyncState.oneBotConnected = connected
        bandSender(SyncStatePush.buildFrame())
        if (connected && !autoFetchDone) {
            tryAutoFetch()
        }
    }

    private fun tryAutoFetch() {
        autoFetch?.invoke(store)
    }

    private fun log(msg: String) {
        try {
            android.util.Log.d("MessageBroker", msg)
        } catch (e: Throwable) {
            // JVM 单测环境下 android.util.Log 不可用，静默忽略
        }
    }
}
