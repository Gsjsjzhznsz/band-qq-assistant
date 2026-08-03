package com.example.bandqq.sync

import com.google.gson.JsonArray
import com.google.gson.JsonObject

data class StoredMessage(
    val messageType: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val time: Long
)

data class ConversationInfo(
    val id: String,
    val type: String,
    val name: String,
    val lastMsg: String,
    val time: Long
)

class MessageStore {

    private val messagesByTarget = LinkedHashMap<String, MutableList<StoredMessage>>()
    private val MAX_MESSAGES = 200
    private val MAX_CONVERSATIONS = 100

    fun addMessage(targetId: String, msg: StoredMessage) {
        val list = messagesByTarget.getOrPut(targetId) { mutableListOf() }
        list.add(msg)
        while (list.size > MAX_MESSAGES) list.removeAt(0)
    }

    fun getHistory(targetId: String, limit: Int): List<StoredMessage> {
        val list = messagesByTarget[targetId] ?: return emptyList()
        val from = (list.size - limit).coerceAtLeast(0)
        return list.subList(from, list.size)
    }

    fun getConversations(): List<ConversationInfo> {
        val out = mutableListOf<ConversationInfo>()
        for ((id, list) in messagesByTarget) {
            if (list.isEmpty()) continue
            val last = list.last()
            out.add(
                ConversationInfo(
                    id = id,
                    type = last.messageType,
                    name = last.senderName.ifBlank { id },
                    lastMsg = last.content,
                    time = last.time
                )
            )
        }
        out.sortByDescending { it.time }
        return out.subList(0, out.size.coerceAtMost(MAX_CONVERSATIONS))
    }

    fun buildHistoryFrame(targetId: String, limit: Int, seq: Int): String {
        val obj = JsonObject()
        obj.addProperty("type", "history_list")
        obj.addProperty("seq", seq)
        obj.addProperty("target_id", targetId)
        val arr = JsonArray()
        for (m in getHistory(targetId, limit)) {
            val o = JsonObject()
            o.addProperty("message_type", m.messageType)
            o.addProperty("sender_id", m.senderId)
            o.addProperty("sender_name", m.senderName)
            o.addProperty("content", m.content)
            o.addProperty("time", m.time)
            arr.add(o)
        }
        obj.add("list", arr)
        return obj.toString()
    }

    fun buildConversationFrame(seq: Int): String {
        val obj = JsonObject()
        obj.addProperty("type", "conversation_list")
        obj.addProperty("seq", seq)
        val arr = JsonArray()
        for (c in getConversations()) {
            val o = JsonObject()
            o.addProperty("id", c.id)
            o.addProperty("type", c.type)
            o.addProperty("name", c.name)
            o.addProperty("last_msg", c.lastMsg)
            o.addProperty("time", c.time)
            arr.add(o)
        }
        obj.add("list", arr)
        return obj.toString()
    }
}
