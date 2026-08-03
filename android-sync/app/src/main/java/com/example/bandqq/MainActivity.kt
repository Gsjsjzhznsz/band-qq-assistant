package com.example.bandqq

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.bandqq.config.ConfigManager
import com.example.bandqq.config.ConfigHolder
import com.example.bandqq.databinding.ActivityMainBinding
import com.example.bandqq.sync.SyncService
import com.example.bandqq.sync.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var configManager: ConfigManager
    private val scope = CoroutineScope(Dispatchers.Main)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configManager = ConfigManager(this)

        requestPermissions()
        loadConfig()
        bindButtons()
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
            binding.wsInput.setText(config.wsUrl)
            binding.httpInput.setText(config.httpUrl)
            binding.tokenInput.setText(config.token)
            refreshStatus()
        }
    }

    private fun bindButtons() {
        binding.saveBtn.setOnClickListener {
            scope.launch {
                val cfg = ConfigHolder.config.copy(
                    wsUrl = binding.wsInput.text.toString().trim(),
                    httpUrl = binding.httpInput.text.toString().trim(),
                    token = binding.tokenInput.text.toString().trim()
                )
                configManager.save(cfg)
                Toast.makeText(this@MainActivity, "配置已保存", Toast.LENGTH_SHORT).show()
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
    }

    private fun probeNapCat() {
        scope.launch {
            val httpUrl = binding.httpInput.text.toString().trim().ifBlank { "http://127.0.0.1:3000" }
            val ok = probeHttp(httpUrl)
            if (ok) {
                binding.statusText.text = "状态：NapCat 在线"
                toast("NapCat 在线")
            } else {
                binding.statusText.text = "状态：NapCat 未响应，请检查是否已安装并登录 NapCat APK"
                promptInstallNapCat()
            }
        }
    }

    private suspend fun probeHttp(url: String): Boolean {
        val client = OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).build()
        return try {
            val resp = client.newCall(Request.Builder().url(url).get().build()).execute()
            resp.use { it.isSuccessful }
        } catch (e: IOException) {
            false
        }
    }

    private fun promptInstallNapCat() {
        runOnUiThread {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/linger-su/astrbot-termux/releases"))
            try {
                startActivity(intent)
            } catch (e: Exception) {
                toast("请手动访问 astrbot-termux releases 下载 NapCat APK")
            }
        }
    }

    private fun refreshStatus() {
        binding.statusText.text = when {
            SyncState.oneBotConnected && SyncState.bandConnected -> "状态：互联已连接，NapCat 在线"
            SyncState.oneBotConnected -> "状态：NapCat 在线，等待手环连接"
            else -> "状态：未连接（请启动同步服务）"
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }
}
