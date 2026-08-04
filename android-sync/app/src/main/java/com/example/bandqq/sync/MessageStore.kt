package com.example.bandqq.sync

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class StoredMessage(
    val messageType: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val time: Long,
    val isSelf: Boolean = false
)

data class ConversationInfo(
    val id: String,
    val type: String,
    val name: String,
    val lastMsg: String,
    val time: Long
)

data class VisibleContact(
    val id: String,
    val type: String,
    val name: String
)

interface KvStorage {
    fun get(key: String, default: String): String
    fun set(key: String, value: String)
    fun remove(key: String)
}

class InMemoryKv : KvStorage {
    private val map = LinkedHashMap<String, String>()
    override fun get(key: String, default: String) = map[key] ?: default
    override fun set(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}

class MessageStore(private val storage: KvStorage = InMemoryKv()) {

    private val MESSAGES_KEY = "chat_messages"
    private val CONVERSATIONS_KEY = "chat_conversations"
    private val VISIBLE_KEY = "visible_contacts"

    private val messagesByTarget = LinkedHashMap<String, MutableList<StoredMessage>>()
    private val MAX_MESSAGES = 200
    private val MAX_CONVERSATIONS = 100

    /** 联系人可见性：以手机端为主存储，持久化 */
    private var visibleContacts: MutableList<VisibleContact> = loadVisibleContacts()
    private val duplicates = HashSet<String>()

    init {
        visibleContacts = loadVisibleContacts()
        loadPersistedMessages()
    }

    /** 从持久化存储恢复全部消息与会话索引 */
    private fun loadPersistedMessages() {
        try {
            val raw = storage.get(MESSAGES_KEY, "{}")
            val obj = JsonParser.parseString(raw).asJsonObject
            for ((targetId, el) in obj.entrySet()) {
                val arr = el.asJsonArray
                val list = mutableListOf<StoredMessage>()
                for (e in arr) {
                    val o = e.asJsonObject
                    list.add(
                        StoredMessage(
                            messageType = o.get("message_type")?.asString ?: "private",
                            senderId = o.get("sender_id")?.asString ?: "",
                            senderName = o.get("sender_name")?.asString ?: "",
                            content = o.get("content")?.asString ?: "",
                            time = o.get("time")?.asLong ?: 0L,
                            isSelf = o.get("is_self")?.asBoolean ?: false
                        )
                    )
                }
                if (list.isNotEmpty()) messagesByTarget[targetId] = list
            }
        } catch (e: Exception) {
            // 存储损坏时忽略，从空开始
        }
    }

    /** 将全部消息写入持久化存储 */
    private fun persistMessages() {
        val root = JsonObject()
        for ((targetId, list) in messagesByTarget) {
            val arr = JsonArray()
            for (m in list) {
                val o = JsonObject()
                o.addProperty("message_type", m.messageType)
                o.addProperty("sender_id", m.senderId)
                o.addProperty("sender_name", m.senderName)
                o.addProperty("content", m.content)
                o.addProperty("time", m.time)
                o.addProperty("is_self", m.isSelf)
                arr.add(o)
            }
            root.add(targetId, arr)
        }
        storage.set(MESSAGES_KEY, root.toString())
    }

    fun addMessage(targetId: String, msg: StoredMessage) {
        val list = messagesByTarget.getOrPut(targetId) { mutableListOf() }
        val dedup = "$targetId|${msg.senderId}|${msg.time}|${msg.content}"
        if (duplicates.add(dedup) || !list.any { it.time == msg.time && it.content == msg.content }) {
            list.add(msg)
        }
        while (list.size > MAX_MESSAGES) list.removeAt(0)
        persistMessages()
    }

    fun getHistory(targetId: String, limit: Int): List<StoredMessage> {
        val list = messagesByTarget[targetId] ?: return emptyList()
        val from = (list.size - limit).coerceAtLeast(0)
        return list.subList(from, list.size)
    }

    /** 按会话列出全部历史消息（供手机端查看页） */
    fun getAllMessages(targetId: String): List<StoredMessage> {
        return messagesByTarget[targetId]?.toList() ?: emptyList()
    }

    fun getAllTargetIds(): List<String> {
        return messagesByTarget.keys.toList()
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

    fun clearHistory(targetId: String) {
        messagesByTarget.remove(targetId)
        persistMessages()
    }

    fun clearAllHistory() {
        messagesByTarget.clear()
        duplicates.clear()
        persistMessages()
    }

    private fun loadVisibleContacts(): MutableList<VisibleContact> {
        val raw = storage.get(VISIBLE_KEY, "[]")
        return try {
            val arr = JsonParser.parseString(raw).asJsonArray
            val out = mutableListOf<VisibleContact>()
            for (e in arr) {
                val o = e.asJsonObject
                out.add(
                    VisibleContact(
                        id = o.get("id")?.asString ?: "",
                        type = o.get("type")?.asString ?: "private",
                        name = o.get("name")?.asString ?: ""
                    )
                )
            }
            out
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun persistVisibleContacts() {
        val arr = JsonArray()
        for (c in visibleContacts) {
            val o = JsonObject()
            o.addProperty("id", c.id)
            o.addProperty("type", c.type)
            o.addProperty("name", c.name)
            arr.add(o)
        }
        storage.set(VISIBLE_KEY, arr.toString())
    }

    fun getVisibleContacts(): List<VisibleContact> = visibleContacts.toList()

    fun setVisibleContacts(list: List<VisibleContact>) {
        visibleContacts = list.distinctBy { it.id }.toMutableList()
        persistVisibleContacts()
    }

    fun isVisibleContact(id: String): Boolean = visibleContacts.any { it.id == id }

    fun buildVisibleContactsFrame(seq: Int): String {
        val obj = JsonObject()
        obj.addProperty("type", "visible_contacts")
        obj.addProperty("seq", seq)
        val arr = JsonArray()
        for (c in visibleContacts) {
            val o = JsonObject()
            o.addProperty("id", c.id)
            o.addProperty("type", c.type)
            o.addProperty("name", c.name)
            arr.add(o)
        }
        obj.add("contacts", arr)
        return obj.toString()
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
            o.addProperty("is_self", m.isSelf)
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
