package com.example.bandqq.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.bandqq.config.AppConfig
import com.example.bandqq.config.ConfigManager
import com.example.bandqq.config.EndpointConfig
import com.example.bandqq.onebot.GameProtocolDetector
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configManager = remember { ConfigManager(context) }
    var wsUrl by remember { mutableStateOf("") }
    var wsToken by remember { mutableStateOf("") }
    var httpUrl by remember { mutableStateOf("") }
    var httpToken by remember { mutableStateOf("") }
    var autoStart by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(loaded) { if (loaded) entered = true }

    LaunchedEffect(Unit) {
        val cfg = configManager.load()
        wsUrl = cfg.endpoint.wsUrl
        wsToken = cfg.endpoint.wsToken
        httpUrl = cfg.endpoint.httpUrl
        httpToken = cfg.endpoint.httpToken
        autoStart = cfg.autoStart
        loaded = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!loaded) {
            SmallTitle(text = "正在读取配置…")
            return@Column
        }

        SmallTitle(text = "SnowLuma 服务器")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .listItemReveal(entered, 0),
            colors = CardDefaults.defaultColors(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextField(
                    value = wsUrl, onValueChange = { wsUrl = it }, label = "WS 地址",
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = wsToken, onValueChange = { wsToken = it }, label = "WS Token",
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = httpUrl, onValueChange = { httpUrl = it }, label = "HTTP 地址",
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = httpToken, onValueChange = { httpToken = it }, label = "HTTP Token",
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "与 SnowLuma WebUI 中开启的 WS 服务端 / HTTP API 保持一致",
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SmallTitle(text = "通用")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .listItemReveal(entered, 1),
            colors = CardDefaults.defaultColors(),
        ) {
            SwitchPreference(
                title = "启动时自动同步",
                summary = "打开应用后自动启动同步服务",
                checked = autoStart,
                onCheckedChange = { checked ->
                    autoStart = checked
                    scope.launch {
                        configManager.save(
                            AppConfig(
                                EndpointConfig(
                                    wsUrl = wsUrl.trim(),
                                    wsToken = wsToken.trim(),
                                    httpUrl = httpUrl.trim(),
                                    httpToken = httpToken.trim(),
                                ),
                                autoStart = checked,
                            ),
                        )
                    }
                },
            )
        }

        SmallTitle(text = "操作")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .listItemReveal(entered, 2),
            colors = CardDefaults.defaultColors(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            configManager.save(
                                AppConfig(
                                    EndpointConfig(
                                        wsUrl = wsUrl.trim(),
                                        wsToken = wsToken.trim(),
                                        httpUrl = httpUrl.trim(),
                                        httpToken = httpToken.trim(),
                                    ),
                                    autoStart = autoStart,
                                ),
                            )
                            toast(context, "配置已保存")
                        }
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("保存") }

                Button(
                    onClick = {
                        scope.launch {
                            val result = GameProtocolDetector.testConnection(
                                wsUrl.trim(), wsToken.trim(), httpUrl.trim(), httpToken.trim(),
                            )
                            val msg = if (result.wsReachable && result.httpReachable) {
                                "SnowLuma 连接正常"
                            } else {
                                "WS:${if (result.wsReachable) "可连" else "不可连"} " +
                                    "HTTP:${if (result.httpReachable) "可连" else "不可连"}"
                            }
                            toast(context, msg)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("测试连接") }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
