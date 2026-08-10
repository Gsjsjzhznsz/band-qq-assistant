package com.example.bandqq.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
fun ContactScreen() {
    SmallTitle(
        text = "联系人：私聊 / 群聊列表与可见性管理",
        modifier = Modifier.fillMaxSize(),
    )
}