package com.example.aidocumentreader.presentation.ocr.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/**
 * Full-screen dialog for viewing the document image.
 *
 * KEY CONCEPT: Dialog for Focus
 * - Dims background to focus attention
 * - Full-screen for better readability
 * - Easy to dismiss (tap anywhere or close button)
 *
 * COMPOSE PATTERNS:
 * - Dialog composable with custom properties
 * - Box with overlay for dimming
 * - Modifier.clickable for tap-to-dismiss
 *
 * UX PATTERN: Image Viewer
 * - Semi-transparent dark background
 * - Image fills available space
 * - Close button in top-right corner
 * - Tap anywhere to dismiss
 *
 * @param imageUri URI of the image to display
 * @param onDismiss Callback invoked when dialog should close
 */
@Composable
fun FullScreenImageDialog(
    imageUri: Uri,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false // Allow full-screen
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .clickable(onClick = onDismiss) // Tap to dismiss
        ) {
            // Full-screen image
            AsyncImage(
                model = imageUri,
                contentDescription = "Full-screen document view",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentScale = ContentScale.Fit
            )

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }
    }
}
