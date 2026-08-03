package com.example.bandqq.sync

import com.example.bandqq.onebot.OneBotMessage
import com.example.bandqq.onebot.OneBotParser
import com.google.gson.JsonObject
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
    private val store: MessageStore
) : com.example.bandqq.onebot.OneBotListener {

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
                time = msg.time
            )
        )
        return parser.toHandBandFrame(msg)
    }

    override fun onEvent(message: OneBotMessage) {
        val frame = handleOneBotEvent(message) ?: return
        bandSender(frame)
    }

    override fun onState(connected: Boolean) {
        val obj = JsonObject()
        obj.addProperty("type", "connect_state")
        obj.addProperty("seq", 0)
        obj.addProperty("state", if (connected) "connected" else "disconnected")
        bandSender(obj.toString())
    }
}
