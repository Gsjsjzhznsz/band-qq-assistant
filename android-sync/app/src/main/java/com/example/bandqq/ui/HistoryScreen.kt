package com.example.bandqq.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
fun HistoryScreen() {
    SmallTitle(
        text = "聊天记录：会话列表与清空",
        modifier = Modifier.fillMaxSize(),
    )
}