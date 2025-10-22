package com.example.aidocumentreader.presentation.ocr.components

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Displays a clickable preview of the document image.
 *
 * KEY CONCEPT: Async Image Loading with Coil
 * - AsyncImage loads images asynchronously
 * - Handles loading, error, and success states automatically
 * - Efficient memory management (doesn't load full resolution unnecessarily)
 *
 * COMPOSE PATTERNS:
 * - Card for elevation and shape
 * - Modifier.clickable for tap handling
 * - ContentScale.Crop for consistent sizing
 *
 * UX PATTERN: Tappable Preview
 * - Fixed height (200dp) for consistency
 * - Rounded corners for modern look
 * - Card elevation provides depth
 * - Tap opens full-screen view
 *
 * @param imageUri URI of the document image to display
 * @param onClick Callback invoked when image is tapped
 */
@Composable
fun DocumentImagePreview(
    imageUri: Uri,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(200.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            // Coil's AsyncImage handles async loading
            // KEY BENEFITS:
            // - Loads in background thread
            // - Caches images automatically
            // - Handles URI, File, Resource, etc.
            AsyncImage(
                model = imageUri,
                contentDescription = "Document preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}
