package com.bios.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bios.app.model.UserFeedback

/**
 * Lightweight "Was this useful?" feedback prompt.
 *
 * Designed to be non-intrusive:
 * - Appears inline after content, not as a popup
 * - Two taps max: thumbs up/down + optional comment
 * - Disappears after submission
 * - No nagging, no repeat prompts for the same item
 */
@Composable
fun FeedbackPrompt(
    surface: String,
    itemId: String? = null,
    question: String = "Was this useful?",
    onSubmit: (UserFeedback) -> Unit
) {
    var submitted by remember { mutableStateOf(false) }
    var showComment by remember { mutableStateOf(false) }
    var comment by remember { mutableStateOf("") }
    var selectedRating by remember { mutableIntStateOf(0) }

    if (submitted) {
        Text(
            "Thanks for the feedback.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                question,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    selectedRating = 1
                    onSubmit(UserFeedback(
                        surface = surface,
                        surfaceItemId = itemId,
                        rating = 1
                    ))
                    submitted = true
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.ThumbUp,
                    contentDescription = "Useful",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = {
                    selectedRating = -1
                    showComment = true
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.ThumbDown,
                    contentDescription = "Not useful",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (showComment) {
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("What could be better?") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    onSubmit(UserFeedback(
                        surface = surface,
                        surfaceItemId = itemId,
                        rating = -1,
                        comment = comment.ifBlank { null }
                    ))
                    submitted = true
                }) {
                    Text("Submit", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
