package com.example.aidocumentreader.presentation.ocr.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Text input field for asking questions about the document.
 *
 * KEY CONCEPT: Local State Management
 * - Uses remember { mutableStateOf() } for input field state
 * - State is local to this composable (doesn't need to be in ViewModel)
 * - Clear field after sending (better UX)
 *
 * COMPOSE PATTERNS:
 * - TextField with onValueChange callback
 * - Row layout with weight for responsive sizing
 * - IconButton for send action
 *
 * UX PATTERN: Chat Input
 * - TextField expands to fill available width
 * - Send button only enabled when text is not blank
 * - Clear field after successful send
 *
 * @param onSendMessage Callback invoked when user sends a message
 * @param enabled Whether input is enabled (false when loading)
 * @param placeholder Hint text to show when field is empty
 */
@Composable
fun ChatInputField(
    onSendMessage: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String = "Ask a question about this document..."
) {
    // Local state for text input
    // KEY: This doesn't need to be in ViewModel because:
    // - Only this composable needs to know about it
    // - It's cleared after sending
    // - No other part of the app needs this data
    var inputText by remember { mutableStateOf("") }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Text input field
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder) },
            enabled = enabled,
            singleLine = false,
            maxLines = 3
        )

        // Send button
        IconButton(
            onClick = {
                // Validate and send
                if (inputText.isNotBlank()) {
                    onSendMessage(inputText)
                    inputText = "" // Clear after sending
                }
            },
            enabled = enabled && inputText.isNotBlank()
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send message"
            )
        }
    }
}
