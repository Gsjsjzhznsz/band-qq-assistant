package com.example.bandqq.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.example.bandqq.sync.BandStateBus
import com.example.bandqq.sync.SyncState

@Composable
fun useBandConnected(): State<Boolean> {
    val state = remember { mutableStateOf(SyncState.bandConnected) }
    DisposableEffect(Unit) {
        val listener: (Boolean) -> Unit = { connected -> state.value = connected }
        BandStateBus.add(listener)
        onDispose { BandStateBus.remove(listener) }
    }
    return state
}

fun toast(context: Context, msg: String) {
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}