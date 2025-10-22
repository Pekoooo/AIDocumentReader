package com.example.aidocumentreader.presentation.ocr.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.aidocumentreader.presentation.ocr.ChatMessage

/**
 * Displays a single chat message bubble.
 *
 * KEY CONCEPT: Conditional Styling
 * - User messages: aligned right, primary color background
 * - AI messages: aligned left, surface variant background
 *
 * COMPOSE PATTERNS:
 * - Box for alignment control
 * - Modifier.align() for positioning within parent
 * - Shape with rounded corners (AI has left corner sharp, User has right corner sharp)
 *
 * UX PATTERN: Chat Bubbles
 * - Different alignment signals sender identity
 * - Different colors reinforce who's speaking
 * - Asymmetric corner radius adds visual interest
 */
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    // Determine styling based on sender
    val alignment = if (message.isFromUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (message.isFromUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (message.isFromUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Different corner radius for visual distinction
    // User messages: sharp bottom-right corner
    // AI messages: sharp bottom-left corner
    val shape = if (message.isFromUser) {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 4.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 4.dp,
            bottomEnd = 16.dp
        )
    }

    // Box for alignment control
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        // Message bubble
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(shape)
                .background(backgroundColor)
                .padding(12.dp)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
            )
        }
    }
}
