package com.example.bandqq

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.bandqq.config.AppConfig
import com.example.bandqq.config.ConfigManager
import com.example.bandqq.config.ConfigHolder
import com.example.bandqq.config.EndpointConfig
import com.example.bandqq.databinding.ActivityMainBinding
import com.example.bandqq.onebot.GameProtocolDetector
import com.example.bandqq.sync.BandStateBus
import com.example.bandqq.sync.InterconnectBridge
import com.example.bandqq.sync.SyncService
import com.example.bandqq.sync.SyncState
import com.example.bandqq.sync.StoreHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var configManager: ConfigManager
    private val scope = CoroutineScope(Dispatchers.Main)

    private val stateListener: (Boolean) -> Unit = { _ -> refreshStatus() }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configManager = ConfigManager(this)

        BandStateBus.add(stateListener)
        requestPermissions()
        loadConfig()
        bindButtons()
    }

    override fun onDestroy() {
        BandStateBus.remove(stateListener)
        super.onDestroy()
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val perms = arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(perms)
            }
        }
    }

    private fun loadConfig() {
        scope.launch {
            val config = configManager.load()
            binding.wsInput.setText(config.endpoint.wsUrl)
            binding.wsTokenInput.setText(config.endpoint.wsToken)
            binding.httpInput.setText(config.endpoint.httpUrl)
            binding.httpTokenInput.setText(config.endpoint.httpToken)
            refreshStatus()
        }
    }

    private fun bindButtons() {
        binding.saveBtn.setOnClickListener {
            scope.launch {
                val cfg = AppConfig(
                    EndpointConfig(
                        wsUrl = binding.wsInput.text.toString().trim(),
                        wsToken = binding.wsTokenInput.text.toString().trim(),
                        httpUrl = binding.httpInput.text.toString().trim(),
                        httpToken = binding.httpTokenInput.text.toString().trim()
                    )
                )
                configManager.save(cfg)
                Toast.makeText(this@MainActivity, "配置已保存", Toast.LENGTH_SHORT).show()
                refreshStatus()
            }
        }

        binding.probeBtn.setOnClickListener { probe() }

        binding.testBtn.setOnClickListener { testConnection() }

        binding.startBtn.setOnClickListener {
            SyncService.start(this)
            toast("同步服务已启动")
            refreshStatus()
        }

        binding.stopBtn.setOnClickListener {
            SyncService.stop(this)
            toast("同步服务已停止")
            refreshStatus()
        }

        binding.checkBandBtn.setOnClickListener {
            checkBand()
        }

        binding.chatHistoryBtn.setOnClickListener {
            startActivity(Intent(this, ChatHistoryActivity::class.java))
        }

        binding.contactManagerBtn.setOnClickListener {
            startActivity(Intent(this, ContactManagerActivity::class.java))
        }

        binding.clearHistoryBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("清空全部聊天记录")
                .setMessage("确定要清空手机端保存的全部聊天记录吗？\n（将同步清空手环端缓存）")
                .setPositiveButton("清空") { _, _ ->
                    StoreHolder.store?.clearAllHistory()
                    InterconnectBridge.sendToBand("""{"type":"clear_all_history","seq":0}""")
                    toast("聊天记录已清空")
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun probe() {
        scope.launch {
            binding.statusText.text = "状态：正在局域网探测 SnowLuma..."
            val detected = GameProtocolDetector.detect()
            if (detected != null) {
                binding.wsInput.setText(detected.wsUrl)
                binding.httpInput.setText(detected.httpUrl)
                // 保留用户已填的 token,不覆盖
                val cfg = ConfigHolder.config.copy(
                    endpoint = detected.copy(
                        wsToken = binding.wsTokenInput.text.toString().trim(),
                        httpToken = binding.httpTokenInput.text.toString().trim()
                    )
                )
                configManager.save(cfg)
                binding.statusText.text = "状态：SnowLuma 在线（${detected.httpUrl}）"
                toast("已探测到 SnowLuma,配置已保存")
            } else {
                binding.statusText.text = "状态：未检测到 SnowLuma,请检查协议端是否已启动"
            }
        }
    }

    private fun testConnection() {
        scope.launch {
            binding.statusText.text = "状态：正在测试 SnowLuma..."
            val wsText = binding.wsInput.text.toString().trim()
            val httpText = binding.httpInput.text.toString().trim()
            val wsToken = binding.wsTokenInput.text.toString().trim()
            val httpToken = binding.httpTokenInput.text.toString().trim()
            val result = GameProtocolDetector.testConnection(wsText, wsToken, httpText, httpToken)
            val parts = mutableListOf<String>()
            parts += if (result.wsReachable) "WS 可连接" else "WS 不可连接"
            parts += if (result.httpReachable) "HTTP 可连接" else "HTTP 不可连接"
            val all = result.wsReachable && result.httpReachable
            binding.statusText.text = "状态：${if (all) "SnowLuma 在线" else "SnowLuma 连接失败"}（${parts.joinToString("，")}）"
            toast(if (all) "SnowLuma 连接正常" else "连接失败，请检查地址与协议端是否已启动")
        }
    }

    private fun refreshStatus() {
        binding.statusText.text = when {
            SyncState.oneBotConnected && SyncState.bandConnected -> "状态：互联已连接,SnowLuma 在线"
            SyncState.oneBotConnected -> "状态：SnowLuma 在线,等待手环连接"
            SyncState.bandConnected -> "状态：手环已连接,等待 SnowLuma"
            else -> "状态：未连接（请启动同步服务）"
        }
    }

    /** 检查手环互联：主动触发 SDK 连接（若已连接会触发授权/拉起手环应用） */
    private fun checkBand() {
        binding.statusText.text = "状态：正在检查手环连接..."
        if (SyncState.bandConnected) {
            toast("手环已连接")
            refreshStatus()
            return
        }
        InterconnectBridge.connect()
        toast("已发起手环连接检查，请留意运动健康的授权提示")
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }
}
