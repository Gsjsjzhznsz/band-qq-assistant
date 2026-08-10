package com.example.bandqq.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
fun SettingsScreen() {
    SmallTitle(
        text = "设置：SnowLuma WS/HTTP 配置",
        modifier = Modifier.fillMaxSize(),
    )
}