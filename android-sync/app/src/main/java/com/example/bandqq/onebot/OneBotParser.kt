package com.example.bandqq.onebot

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class OneBotMessage(
    val messageType: String,  // "private" | "group"
    val targetId: String,     // group_id 或 user_id
    val senderId: String,
    val senderName: String,
    val content: String,
    val time: Long
)

class OneBotParser {

    fun parseMessageEvent(json: String): OneBotMessage? {
        val obj = try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: Exception) {
            return null
        }
        if (obj.get("post_type")?.asString != "message") return null
        val messageType = obj.get("message_type")?.asString ?: return null
        val sender = obj.getAsJsonObject("sender")
        val senderId = obj.get("user_id")?.asLong?.toString() ?: return null
        val targetId = when (messageType) {
            "group" -> obj.get("group_id")?.asLong?.toString()
            "private" -> senderId
            else -> return null
        } ?: return null
        val content = degradeContent(obj.get("message"))
        return OneBotMessage(
            messageType = messageType,
            targetId = targetId,
            senderId = senderId,
            senderName = sender?.get("nickname")?.asString ?: senderId,
            content = content,
            time = obj.get("time")?.asLong ?: 0L
        )
    }

    fun degradeContent(message: com.google.gson.JsonElement?): String {
        if (message == null) return ""
        if (message.isJsonPrimitive && message.asJsonPrimitive.isString) return message.asString
        if (!message.isJsonArray) return ""
        val arr: JsonArray = message.asJsonArray
        val sb = StringBuilder()
        for (elem in arr) {
            val seg = if (elem.isJsonObject) elem.asJsonObject else continue
            when (seg.get("type")?.asString) {
                "text" -> {
                    val text = seg.getAsJsonObject("data")?.get("text")?.asString ?: ""
                    sb.append(text)
                }
                "image" -> sb.append("[图片]")
                "record", "voice" -> sb.append("[语音]")
                "video" -> sb.append("[视频]")
                "file" -> sb.append("[文件]")
                else -> sb.append("[其他]")
            }
        }
        return sb.toString()
    }

    fun toHandBandFrame(msg: OneBotMessage): String {
        val obj = JsonObject()
        obj.addProperty("type", "push_message")
        obj.addProperty("seq", 0)
        obj.addProperty("message_type", msg.messageType)
        obj.addProperty("target_id", msg.targetId)
        obj.addProperty("sender_id", msg.senderId)
        obj.addProperty("sender_name", msg.senderName)
        obj.addProperty("content", msg.content)
        obj.addProperty("time", msg.time)
        return obj.toString()
    }

    fun buildSendRequest(messageType: String, targetId: String, content: String): String {
        val body = JsonObject()
        val params = JsonObject()
        if (messageType == "group") {
            body.addProperty("action", "send_group_msg")
            params.addProperty("group_id", targetId.toLongOrNull() ?: targetId)
        } else {
            body.addProperty("action", "send_private_msg")
            params.addProperty("user_id", targetId.toLongOrNull() ?: targetId)
        }
        params.addProperty("message", content)
        body.add("params", params)
        return body.toString()
    }
}
