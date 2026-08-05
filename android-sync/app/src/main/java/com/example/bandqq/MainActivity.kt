package com.example.bandqq

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.bandqq.config.AppConfig
import com.example.bandqq.config.ConfigManager
import com.example.bandqq.config.ConfigHolder
import com.example.bandqq.config.EndpointConfig
import com.example.bandqq.config.ProtocolType
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

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
            binding.wsInput.setText(config.napcat.wsUrl)
            binding.httpInput.setText(config.napcat.httpUrl)
            binding.tokenInput.setText(config.napcat.token)
            binding.snowWsInput.setText(config.snowluma.wsUrl)
            binding.snowHttpInput.setText(config.snowluma.httpUrl)
            binding.snowTokenInput.setText(config.snowluma.token)
            when (config.activeType) {
                ProtocolType.NAPCAT -> binding.napcatRadio.isChecked = true
                ProtocolType.SNOWLUMA -> binding.snowlumaRadio.isChecked = true
            }
            refreshStatus()
        }
    }

    private fun bindButtons() {
        binding.saveBtn.setOnClickListener {
            scope.launch {
                val active = if (binding.napcatRadio.isChecked) ProtocolType.NAPCAT else ProtocolType.SNOWLUMA
                val cfg = ConfigHolder.config.copy(
                    napcat = EndpointConfig(
                        binding.wsInput.text.toString().trim(),
                        binding.httpInput.text.toString().trim(),
                        binding.tokenInput.text.toString().trim()
                    ),
                    snowluma = EndpointConfig(
                        binding.snowWsInput.text.toString().trim(),
                        binding.snowHttpInput.text.toString().trim(),
                        binding.snowTokenInput.text.toString().trim()
                    ),
                    activeType = active
                )
                configManager.save(cfg)
                Toast.makeText(this@MainActivity, "配置已保存", Toast.LENGTH_SHORT).show()
                refreshStatus()
            }
        }

        binding.probeBtn.setOnClickListener { probeNapCat() }

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

        binding.testNapBtn.setOnClickListener { testConnection(ProtocolType.NAPCAT) }
        binding.testSnowBtn.setOnClickListener { testConnection(ProtocolType.SNOWLUMA) }
        binding.napcatRadio.setOnClickListener { refreshStatus() }
        binding.snowlumaRadio.setOnClickListener { refreshStatus() }
    }

    private fun probeNapCat() {
        scope.launch {
            val type = if (binding.napcatRadio.isChecked) ProtocolType.NAPCAT else ProtocolType.SNOWLUMA
            val name = if (type == ProtocolType.NAPCAT) "NapCat" else "SnowLuma"
            binding.statusText.text = "状态：正在局域网探测 $name..."
            val detected = GameProtocolDetector.detect(
                type = type,
                preferred = if (type == ProtocolType.NAPCAT) binding.httpInput.text.toString().trim() else binding.snowHttpInput.text.toString().trim()
            )
            if (detected != null) {
                if (type == ProtocolType.NAPCAT) {
                    binding.wsInput.setText(detected.wsUrl)
                    binding.httpInput.setText(detected.httpUrl)
                } else {
                    binding.snowWsInput.setText(detected.wsUrl)
                    binding.snowHttpInput.setText(detected.httpUrl)
                }
                val cfg = ConfigHolder.config.copy(
                    napcat = if (type == ProtocolType.NAPCAT) detected else ConfigHolder.config.napcat,
                    snowluma = if (type == ProtocolType.SNOWLUMA) detected else ConfigHolder.config.snowluma
                )
                configManager.save(cfg)
                binding.statusText.text = "状态：$name 在线（${detected.httpUrl}）"
                toast("已探测到 $name,配置已保存")
            } else {
                binding.statusText.text = "状态：未检测到 $name,请检查协议端是否已启动"
            }
        }
    }

    private fun testConnection(type: ProtocolType) {
        scope.launch {
            binding.statusText.text = "状态：正在测试 ${if (type == ProtocolType.NAPCAT) "NapCat" else "SnowLuma"}..."
            val wsText = if (type == ProtocolType.NAPCAT) binding.wsInput.text.toString().trim() else binding.snowWsInput.text.toString().trim()
            val httpText = if (type == ProtocolType.NAPCAT) binding.httpInput.text.toString().trim() else binding.snowHttpInput.text.toString().trim()
            val cfg = GameProtocolDetector.detect(type, preferred = httpText.ifBlank { wsText }, hosts = listOf("127.0.0.1"), ports = intArrayOf())
            if (cfg != null) {
                if (type == ProtocolType.NAPCAT) {
                    binding.wsInput.setText(cfg.wsUrl)
                    binding.httpInput.setText(cfg.httpUrl)
                } else {
                    binding.snowWsInput.setText(cfg.wsUrl)
                    binding.snowHttpInput.setText(cfg.httpUrl)
                }
                binding.statusText.text = "状态：${if (type == ProtocolType.NAPCAT) "NapCat" else "SnowLuma"} 在线"
                toast("${if (type == ProtocolType.NAPCAT) "NapCat" else "SnowLuma"} 连接正常")
            } else {
                binding.statusText.text = "状态：${if (type == ProtocolType.NAPCAT) "NapCat" else "SnowLuma"} 连接失败"
                toast("连接失败,请检查协议端是否已启动")
            }
        }
    }

    private fun refreshStatus() {
        val name = if (ConfigHolder.config.activeType == ProtocolType.NAPCAT) "NapCat" else "SnowLuma"
        binding.statusText.text = when {
            SyncState.oneBotConnected && SyncState.bandConnected -> "状态：互联已连接,$name 在线"
            SyncState.oneBotConnected -> "状态：$name 在线,等待手环连接"
            SyncState.bandConnected -> "状态：手环已连接,等待 $name"
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
