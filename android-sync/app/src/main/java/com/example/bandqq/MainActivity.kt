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
import com.example.bandqq.config.ConfigManager
import com.example.bandqq.config.ConfigHolder
import com.example.bandqq.databinding.ActivityMainBinding
import com.example.bandqq.onebot.NapCatDetector
import com.example.bandqq.sync.SyncService
import com.example.bandqq.sync.SyncState
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
            binding.statusText.text = "状态：正在探测 NapCat..."
            val detected = NapCatDetector.detect(binding.httpInput.text.toString().trim())
            if (detected != null) {
                binding.wsInput.setText(detected.wsUrl)
                binding.httpInput.setText(detected.httpUrl)
                val cfg = ConfigHolder.config.copy(
                    wsUrl = detected.wsUrl,
                    httpUrl = detected.httpUrl,
                    token = binding.tokenInput.text.toString().trim()
                )
                configManager.save(cfg)
                binding.statusText.text = "状态：NapCat 在线（${detected.httpUrl}）"
                toast("已自动探测到 NapCat，配置已保存")
            } else {
                binding.statusText.text = "状态：未检测到 NapCat，请检查是否已安装并启动"
                promptInstallNapCat()
            }
        }
    }

    private fun promptInstallNapCat() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("未检测到 NapCat")
                .setMessage("未在本机发现可用的 NapCat 服务。\n\nNapCat 是 QQ 机器人协议端，需先安装 Termux 再装 NapCat：\n\n1. 前往 F-Droid 官网下载安装 Termux（勿用 Google Play 版）\n2. 在 Termux 内执行：\n    curl -o install.sh https://ncat.wiki/binary/install_script/install.sh && bash install.sh\n3. 打开 NapCat WebUI（http://127.0.0.1:6099）扫码登录 QQ\n4. 回到本页点击“自动探测 NapCat”\n\n详细图文教程见项目 docs/napcat-usage.md")
                .setPositiveButton("查看安装教程") { _, _ -> openTutorial() }
                .setNegativeButton("关闭", null)
                .show()
        }
    }

    private fun openTutorial() {
        toast("请参照项目 docs/napcat-usage.md 的安装教程")
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
