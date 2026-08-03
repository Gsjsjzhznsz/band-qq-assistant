package com.example.bandqq.sync

import com.example.bandqq.onebot.OneBotMessage
import com.example.bandqq.onebot.OneBotParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageBrokerTest {

    private val parser = OneBotParser()

    @Test
    fun `handshake 发送消息帧被转发到 onebot`() {
        val sent = mutableListOf<Triple<String, String, String>>()
        val oneBot = FakeOneBot { t, id, c -> sent.add(Triple(t, id, c)); true }
        val broker = MessageBroker(parser, oneBot, MessageStore())
        val handled = broker.onBandFrame("""{"type":"send_message","message_type":"group","target_id":"123","content":"收到"}""")
        assertTrue(handled)
        assertEquals("group", sent[0].first)
        assertEquals("123", sent[0].second)
        assertEquals("收到", sent[0].third)
    }

    @Test
    fun `onebot 事件转手环帧`() {
        val broker = MessageBroker(parser, FakeOneBot { _, _, _ -> true }, MessageStore())
        val frame = broker.handleOneBotEvent(
            OneBotMessage("group", "123", "456", "张三", "你好", 1700000000L)
        )
        assertTrue(frame!!.contains("\"target_id\":\"123\""))
        assertTrue(frame.contains("你好"))
    }

    @Test
    fun `get_history 返回存储的最近消息`() {
        val store = MessageStore()
        store.addMessage("123", StoredMessage("group", "456", "张三", "你好", 1700000000L))
        val broker = MessageBroker(parser, FakeOneBot { _, _, _ -> true }, store)
        val out = mutableListOf<String>()
        broker.bandSender = { out.add(it) }
        val handled = broker.onBandFrame("""{"type":"get_history","seq":9,"target_id":"123","limit":20}""")
        assertTrue(handled)
        assertTrue(out[0].contains("\"type\":\"history_list\""))
        assertTrue(out[0].contains("你好"))
    }

    @Test
    fun `get_conversations 返回会话列表帧`() {
        val store = MessageStore()
        store.addMessage("123", StoredMessage("group", "456", "张三", "你好", 1700000000L))
        val broker = MessageBroker(parser, FakeOneBot { _, _, _ -> true }, store)
        val out = mutableListOf<String>()
        broker.bandSender = { out.add(it) }
        val handled = broker.onBandFrame("""{"type":"get_conversations","seq":9}""")
        assertTrue(handled)
        assertTrue(out[0].contains("\"type\":\"conversation_list\""))
        assertTrue(out[0].contains("\"id\":\"123\""))
    }

    @Test
    fun `未知手环帧返回 false`() {
        val broker = MessageBroker(parser, FakeOneBot { _, _, _ -> true }, MessageStore())
        assertNull(broker.handleOneBotEvent(OneBotMessage("group", "1", "2", "n", "x", 0)))
        assertTrue(!broker.onBandFrame("""{"type":"unknown"}"""))
    }
}

class FakeOneBot(private val onSend: (String, String, String) -> Boolean) : MessageSender {
    override fun sendMessage(messageType: String, targetId: String, content: String, callback: (Boolean) -> Unit) {
        callback(onSend(messageType, targetId, content))
    }
}
