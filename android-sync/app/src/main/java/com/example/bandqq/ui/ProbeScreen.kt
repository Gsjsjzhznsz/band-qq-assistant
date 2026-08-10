package com.example.bandqq.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ProbeScreen() {
    MiuixTheme {
        Scaffold { contentPadding ->
            Button(
                onClick = {},
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("Miuix ${MiuixIcons.Home.hashCode()}")
            }
        }
    }
}