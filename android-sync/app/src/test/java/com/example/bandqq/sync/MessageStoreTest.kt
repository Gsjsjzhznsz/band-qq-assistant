package com.example.bandqq.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageStoreTest {

    private val store = MessageStore()

    @Test
    fun `写入后能取回历史`() {
        store.addMessage("123", StoredMessage("group", "456", "张三", "你好", 1700000000L))
        val history = store.getHistory("123", 50)
        assertEquals(1, history.size)
        assertEquals("你好", history[0].content)
    }

    @Test
    fun `会话按时间倒序`() {
        store.addMessage("a", StoredMessage("group", "1", "A", "x", 100L))
        store.addMessage("b", StoredMessage("group", "2", "B", "y", 200L))
        val convs = store.getConversations()
        assertEquals("b", convs[0].id)
    }

    @Test
    fun `history_list 帧包含消息`() {
        store.addMessage("123", StoredMessage("group", "456", "张三", "你好", 1700000000L))
        val frame = store.buildHistoryFrame("123", 50, 4)
        assertTrue(frame.contains("\"type\":\"history_list\""))
        assertTrue(frame.contains("\"target_id\":\"123\""))
        assertTrue(frame.contains("你好"))
    }

    @Test
    fun `conversation_list 帧包含会话`() {
        store.addMessage("123", StoredMessage("group", "456", "张三", "你好", 1700000000L))
        val frame = store.buildConversationFrame(3)
        assertTrue(frame.contains("\"type\":\"conversation_list\""))
        assertTrue(frame.contains("\"id\":\"123\""))
    }
}
