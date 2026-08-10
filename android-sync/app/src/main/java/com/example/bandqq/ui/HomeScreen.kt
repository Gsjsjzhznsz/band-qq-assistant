package com.example.bandqq.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
fun HomeScreen() {
    SmallTitle(
        text = "主页：手环连接 / SnowLuma 状态 / 同步开关",
        modifier = Modifier.fillMaxSize(),
    )
}