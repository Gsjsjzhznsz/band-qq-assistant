package com.example.bandqq.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bandqq.sync.ConversationInfo
import com.example.bandqq.sync.StoreHolder
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.window.WindowDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val detailTimeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

@Composable
fun HistoryDetailDialog(conv: ConversationInfo, onDismiss: () -> Unit) {
    val msgs = StoreHolder.store?.getAllMessages(conv.id) ?: emptyList()
    val lines = msgs.map { m ->
        val who = if (m.isSelf) "我" else (m.senderName.ifBlank { m.senderId })
        "[${detailTimeFmt.format(Date(m.time))}] $who：${m.content}"
    }
    WindowDialog(
        show = true,
        title = "${conv.name}（${msgs.size} 条）",
        onDismissRequest = onDismiss,
        content = {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.defaultColors(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    if (lines.isEmpty()) {
                        Text(
                            "暂无消息",
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                    lines.forEach { Text(it) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            ) {
                val dismiss = LocalDismissState.current
                TextButton(text = "关闭", onClick = { dismiss?.invoke() })
            }
        },
    )
}
