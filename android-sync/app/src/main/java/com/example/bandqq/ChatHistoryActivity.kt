package com.example.bandqq

import android.os.Bundle
import android.widget.ListView
import android.widget.SimpleAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.bandqq.sync.ConversationInfo
import com.example.bandqq.sync.MessageStore
import com.example.bandqq.sync.StoreHolder
import com.example.bandqq.sync.SyncPreferencesKv
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatHistoryActivity : AppCompatActivity() {

    private val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_history)
        ensureStore()
        setupList()
    }

    override fun onResume() {
        super.onResume()
        ensureStore()
        setupList()
    }

    private fun ensureStore() {
        if (StoreHolder.store == null) {
            StoreHolder.setStore(MessageStore(SyncPreferencesKv(this)))
        }
    }

    private fun setupList() {
        val listView = findViewById<ListView>(R.id.conversationList)
        val convs = StoreHolder.store?.getConversations() ?: emptyList()

        val data = convs.map { c ->
            mapOf(
                "name" to c.name,
                "last" to c.lastMsg,
                "time" to timeFmt.format(Date(c.time))
            )
        }

        val adapter = SimpleAdapter(
            this,
            data,
            android.R.layout.simple_list_item_2,
            arrayOf("name", "last"),
            intArrayOf(android.R.id.text1, android.R.id.text2)
        )
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            if (position < convs.size) showConversationDetail(convs[position])
        }
    }

    private fun showConversationDetail(conv: ConversationInfo) {
        val store = StoreHolder.store ?: return
        val msgs = store.getAllMessages(conv.id)
        if (msgs.isEmpty()) return

        val lines = msgs.map { m ->
            val who = if (m.isSelf) "我" else (m.senderName.ifBlank { m.senderId })
            "[${timeFmt.format(Date(m.time))}] $who：${m.content}"
        }

        AlertDialog.Builder(this)
            .setTitle("${conv.name}（${msgs.size} 条）")
            .setItems(lines.toTypedArray(), null)
            .setPositiveButton("关闭", null)
            .show()
    }
}