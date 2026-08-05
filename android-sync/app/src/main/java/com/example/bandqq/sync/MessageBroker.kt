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

    fun onBandFrame(json: String): Boolean {
        val obj = try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: Exception) {
            return false
        }
        val type = obj.get("type")?.asString ?: return false
        val seq = obj.get("seq")?.asInt ?: 0
        when (type) {
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
                bandSender(store.buildVisibleContactsFrame(seq))
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
        val visible = store.isVisibleContact(msg.targetId)
        return parser.toHandBandFrame(msg, visible)
    }

    override fun onEvent(message: OneBotMessage) {
        val frame = handleOneBotEvent(message) ?: return
        bandSender(frame)
    }

    override fun onState(connected: Boolean) {
        SyncState.oneBotConnected = connected
        bandSender(SyncStatePush.buildFrame())
        if (connected && !autoFetchDone) {
            autoFetchDone = true
            tryAutoFetch()
        }
    }

    private fun tryAutoFetch() {
        autoFetch?.invoke(store)
    }
}
