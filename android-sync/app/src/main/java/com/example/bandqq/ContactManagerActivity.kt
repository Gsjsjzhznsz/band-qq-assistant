package com.example.bandqq

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.bandqq.config.ConfigHolder
import com.example.bandqq.onebot.OneBotClient
import com.example.bandqq.onebot.OneBotParser
import com.example.bandqq.sync.InterconnectBridge
import com.example.bandqq.sync.MessageStore
import com.example.bandqq.sync.StoreHolder
import com.example.bandqq.sync.SyncPreferencesKv
import com.example.bandqq.sync.VisibleContact
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class ContactManagerActivity : AppCompatActivity() {

    private data class Row(val contact: VisibleContact)

    private val client = OneBotClient(OneBotParser())
    private var friends = mutableListOf<VisibleContact>()
    private var groups = mutableListOf<VisibleContact>()
    private var currentRows = mutableListOf<Row>()
    private var currentType = "private"
    private lateinit var listView: ListView
    private val adapter = ContactAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_manager)
        listView = findViewById(R.id.contactList)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        findViewById<View>(R.id.friendTab).setOnClickListener { loadTab("private") }
        findViewById<View>(R.id.groupTab).setOnClickListener { loadTab("group") }
        findViewById<View>(R.id.saveContactsBtn).setOnClickListener { saveSelection() }
        findViewById<View>(R.id.refreshContactsBtn).setOnClickListener { loadContacts() }

        ensureStore()
        loadCachedIntoCurrentTab()
        loadContacts()
    }

    override fun onResume() {
        super.onResume()
        ensureStore()
        loadCachedIntoCurrentTab()
        loadContacts()
    }

    private fun ensureStore() {
        if (StoreHolder.store == null) {
            StoreHolder.setStore(MessageStore(SyncPreferencesKv(this)))
        }
    }

    private fun loadCachedIntoCurrentTab() {
        val cached = StoreHolder.store?.getCachedContacts() ?: return
        if (currentType == "private") friends = cached.filter { it.type == "private" }.toMutableList()
        else groups = cached.filter { it.type == "group" }.toMutableList()
        refreshCurrentTab()
    }

    private fun loadContacts() {
        val http = ConfigHolder.config.endpoint.httpUrl
        client.configure(ConfigHolder.config.endpoint)
        client.requestApi("get_friend_list", http) { raw -> runOnUiThread { applyList("private", raw) } }
        client.requestApi("get_group_list", http) { raw -> runOnUiThread { applyList("group", raw) } }
    }

    private fun applyList(type: String, raw: String?) {
        val rows = parseContacts(type, raw)
        if (rows.isNotEmpty()) {
            if (type == "private") friends = rows else groups = rows
        }
        val store = StoreHolder.store
        if (store != null && rows.isNotEmpty()) {
            val cached = store.getCachedContacts().filter { it.type != type }
            store.setCachedContacts(cached + rows)
        }
        refreshCurrentTab()
    }

    private fun parseContacts(type: String, raw: String?): MutableList<VisibleContact> {
        val out = mutableListOf<VisibleContact>()
        if (raw == null) return out
        try {
            val data = JsonParser.parseString(raw).asJsonObject.get("data") ?: return out
            if (!data.isJsonArray) return out
            val arr: JsonArray = data.asJsonArray
            val stored = StoreHolder.store?.getVisibleContacts() ?: emptyList()
            for (e in arr) {
                val o = e.asJsonObject
                val id: String
                val name: String
                if (type == "private") {
                    id = o.get("user_id")?.asLong?.toString() ?: continue
                    name = o.get("nickname")?.asString ?: id
                } else {
                    id = o.get("group_id")?.asLong?.toString() ?: continue
                    name = o.get("group_name")?.asString ?: id
                }
                out.add(VisibleContact(id, type, name))
            }
            out.addAll(stored.filter { s -> s.type == type && out.none { it.id == s.id } })
        } catch (e: Exception) {
            // 解析失败，返回已存储联系人
            out.addAll(StoreHolder.store?.getVisibleContacts()?.filter { it.type == type } ?: emptyList())
        }
        return out
    }

    private fun loadTab(type: String) {
        currentType = type
        refreshCurrentTab()
    }

    private fun refreshCurrentTab() {
        val source = if (currentType == "private") friends else groups
        currentRows = source.map { Row(it) }.toMutableList()
        adapter.notifyDataSetChanged()
        val stored = StoreHolder.store?.getVisibleContacts() ?: emptyList()
        for (i in currentRows.indices) {
            listView.setItemChecked(i, stored.any { it.id == currentRows[i].contact.id })
        }
    }

    private fun saveSelection() {
        val store = StoreHolder.store
        if (store == null) {
            toast("同步服务尚未启动，请先启动同步")
            return
        }
        val checked = mutableListOf<VisibleContact>()
        for (i in currentRows.indices) {
            if (listView.isItemChecked(i)) checked.add(currentRows[i].contact)
        }
        val merged = (store.getVisibleContacts().filter { it.type != currentType } + checked)
        store.setVisibleContacts(merged)
        InterconnectBridge.sendToBand(store.buildVisibleContactsFrame(0))
        toast("已保存并同步到手环")
        finish()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private inner class ContactAdapter : BaseAdapter() {
        override fun getCount(): Int = currentRows.size
        override fun getItem(position: Int): Any = currentRows[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(this@ContactManagerActivity)
                .inflate(R.layout.item_contact_checkable, parent, false)
            val name = view.findViewById<TextView>(R.id.contactName)
            name.text = currentRows[position].contact.name
            val check = view.findViewById<CheckBox>(R.id.contactCheck)
            check.isChecked = listView.isItemChecked(position)
            return view
        }
    }
}