package com.example.aidocumentreader.presentation.ocr.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Displays when no document is selected.
 *
 * KEY CONCEPT: Empty State Design
 * - Clear visual hierarchy (icon → title → description → action)
 * - Guides user toward primary action
 * - Friendly, helpful tone
 *
 * COMPOSE PATTERNS:
 * - Column with Arrangement.Center for vertical centering
 * - Alignment.CenterHorizontally for horizontal centering
 * - Spacer for consistent spacing
 *
 * UX PATTERN: Empty State
 * - Large icon draws attention
 * - Clear title explains what to do
 * - Button makes action obvious
 * - Reduces friction for first-time users
 *
 * @param onSelectImageClick Callback invoked when "Select Image" button is clicked
 */
@Composable
fun EmptyStateView(
    onSelectImageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Large icon
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Title
        Text(
            text = "No Document Selected",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Description
        Text(
            text = "Select an image to extract text and start asking questions about it",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Action button
        Button(onClick = onSelectImageClick) {
            Text("Select Image")
        }
    }
}
