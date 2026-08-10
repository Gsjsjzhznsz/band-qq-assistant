package com.example.bandqq.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.bandqq.config.ConfigManager
import com.example.bandqq.onebot.GameProtocolDetector
import com.example.bandqq.sync.InterconnectBridge
import com.example.bandqq.sync.SyncService
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bandConnected by useBandConnected()
    var oneBotRefreshKey by remember { mutableStateOf(0) }
    val oneBotConnected by useOneBotConnected(refreshKey = oneBotRefreshKey)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(),
        ) {
            StatusColumn(
                title = if (bandConnected) "手环：已连接" else "手环：未连接",
                summary = "小米运动健康互联通道",
                detail = if (bandConnected) "在线" else "离线",
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(),
        ) {
            StatusColumn(
                title = if (oneBotConnected) "SnowLuma：在线" else "SnowLuma：离线",
                summary = "OneBot 协议端",
                detail = if (oneBotConnected) "在线" else "离线",
            )
        }

        SmallTitle(text = "操作")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    SyncService.start(context)
                    toast(context, "同步服务已启动")
                },
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.weight(1f),
            ) { Text("启动服务") }
            Button(
                onClick = {
                    SyncService.stop(context)
                    toast(context, "同步服务已停止")
                },
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.weight(1f),
            ) { Text("停止服务") }
        }
        Button(
            onClick = {
                InterconnectBridge.init(context)
                InterconnectBridge.connect()
                toast(context, "已发起手环连接检查")
            },
            colors = ButtonDefaults.buttonColors(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("检查手环连接") }
        Button(
            onClick = {
                scope.launch {
                    val cfg = ConfigManager(context).load()
                    val result = GameProtocolDetector.testConnection(
                        cfg.endpoint.wsUrl, cfg.endpoint.wsToken,
                        cfg.endpoint.httpUrl, cfg.endpoint.httpToken,
                    )
                    oneBotRefreshKey++
                    val msg = if (result.wsReachable && result.httpReachable) {
                        "SnowLuma 在线（WS/HTTP 可连接）"
                    } else {
                        "WS:${if (result.wsReachable) "可连" else "不可连"} " +
                            "HTTP:${if (result.httpReachable) "可连" else "不可连"}"
                    }
                    toast(context, msg)
                }
            },
            colors = ButtonDefaults.buttonColors(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("测试 SnowLuma 连接") }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "当前状态：${if (bandConnected && oneBotConnected) "互联已连接，SnowLuma 在线" else if (oneBotConnected) "SnowLuma 在线，等待手环" else if (bandConnected) "手环已连接，等待 SnowLuma" else "未连接（请先启动同步服务）"}",
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
        )
    }
}

/** 状态卡内容：标题 + 摘要 + 细节点（Miuix 0.9.3 无 ListItem，用 Card+Column 组合）。 */
@Composable
private fun StatusColumn(title: String, summary: String, detail: String) {
    Column {
        Text(text = title)
        Text(
            text = summary,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
        )
        Text(
            text = detail,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
        )
    }
}