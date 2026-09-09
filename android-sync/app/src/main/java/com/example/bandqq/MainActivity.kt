package com.example.bandqq

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.bandqq.config.ConfigManager
import com.example.bandqq.sync.SyncService
import com.example.bandqq.ui.BandQQApp
import com.example.bandqq.ui.BandQQTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BandQQTheme { BandQQApp() }
            BluetoothPermissionRequester()
            AutoStartLauncher()
        }
    }
}

/** 若配置开启「启动时自动同步」，进入应用即拉起同步服务。 */
@Composable
private fun AutoStartLauncher() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val autoStart = runCatching { ConfigManager(context).load().autoStart }.getOrDefault(false)
        if (autoStart) SyncService.start(context)
    }
}

@Composable
private fun BluetoothPermissionRequester() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
}