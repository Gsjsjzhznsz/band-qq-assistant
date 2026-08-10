package com.example.bandqq.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Settings

enum class AppTab(val label: String) {
    Home("主页"),
    Contacts("联系人"),
    History("聊天记录"),
    Settings("设置"),
}

@Composable
fun BandQQApp() {
    var selected by rememberSaveable { mutableStateOf(AppTab.Home) }
    Scaffold(
        topBar = { SmallTopAppBar(title = selected.label) },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = when (tab) {
                            AppTab.Home -> MiuixIcons.Home
                            AppTab.Contacts -> MiuixIcons.Contacts
                            AppTab.History -> MiuixIcons.Messages
                            AppTab.Settings -> MiuixIcons.Settings
                        },
                        label = tab.label,
                    )
                }
            }
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            when (selected) {
                AppTab.Home -> HomeScreen()
                AppTab.Contacts -> ContactScreen()
                AppTab.History -> HistoryScreen()
                AppTab.Settings -> SettingsScreen()
            }
        }
    }
}