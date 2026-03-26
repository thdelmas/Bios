package com.bios.app.ui.journal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.bios.app.model.ActionItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActionItemList(
    items: List<ActionItem>,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: ((String) -> Unit)? = null
) {
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val now = remember { System.currentTimeMillis() }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            ActionItemRow(
                item = item,
                dateFormat = dateFormat,
                now = now,
                onToggle = { onToggle(item.id, it) },
                onDelete = { onDelete(item.id) }
            )
        }

        if (onAdd != null) {
            AddActionItemRow(onAdd = onAdd)
        }
    }
}

@Composable
private fun ActionItemRow(
    item: ActionItem,
    dateFormat: SimpleDateFormat,
    now: Long,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val isOverdue = !item.completed && item.dueAt != null && item.dueAt < now

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(
            checked = item.completed,
            onCheckedChange = onToggle,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = item.description,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            textDecoration = if (item.completed) TextDecoration.LineThrough else null,
            color = if (item.completed) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
        )
        item.dueAt?.let { due ->
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isOverdue) MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = dateFormat.format(Date(due)),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOverdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddActionItemRow(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Add action item...") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.width(4.dp))
        TextButton(
            onClick = {
                if (text.isNotBlank()) {
                    onAdd(text.trim())
                    text = ""
                }
            },
            enabled = text.isNotBlank()
        ) {
            Text("Add")
        }
    }
}
