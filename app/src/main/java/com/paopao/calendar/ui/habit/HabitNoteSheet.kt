package com.paopao.calendar.ui.habit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import com.paopao.calendar.ui.common.CommonFormatters

/**
 * 某一天完成记录的备注弹层。
 *
 * 备注不是打卡的附属字段，而是它的全部意义所在：半年后回看「跑步」那一串勾，
 * 能让人想起那天的只有这一句话。所以日历里点已完成习惯、习惯页点今天，走的都是这一个弹层 ——
 * 两个入口一份实现，改备注的口径才不会在两处漂移。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitNoteSheet(
    habitName: String,
    date: LocalDate,
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialNote) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(habitName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = date.format(CommonFormatters.chineseDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("备注") },
                placeholder = { Text("今天做了什么、做到什么程度") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onSave(text.trim()) },
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }
                if (initialNote.isNotBlank()) {
                    OutlinedButton(
                        onClick = { onSave("") },
                        modifier = Modifier.weight(1f),
                    ) { Text("清空备注") }
                } else {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
