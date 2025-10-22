package com.example.aidocumentreader.presentation.ocr

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aidocumentreader.presentation.ocr.components.ChatInputField
import com.example.aidocumentreader.presentation.ocr.components.ChatMessageItem
import com.example.aidocumentreader.presentation.ocr.components.DocumentImagePreview
import com.example.aidocumentreader.presentation.ocr.components.EmptyStateView
import com.example.aidocumentreader.presentation.ocr.components.FullScreenImageDialog

/**
 * Main OCR + LLM Q&A Screen.
 *
 * KEY EVOLUTION FROM v1:
 * - v1: Simple OCR with text display
 * - v2: Document image preview + chat interface
 * - v2: LLM integration for Q&A about document
 *
 * NEW COMPOSE CONCEPTS:
 * - LazyColumn for efficient scrolling lists
 * - reverseLayout pattern for chat UIs
 * - imePadding for keyboard handling
 * - FloatingActionButton for primary actions
 * - Dialog composable for full-screen image
 * - SnackbarHost for error messages
 *
 * UX FLOW:
 * 1. Empty state → User picks image
 * 2. OCR extracts text (loading indicator)
 * 3. LLM initializes (progress bar)
 * 4. Welcome message appears
 * 5. User asks questions in chat
 * 6. Can tap image for full-screen view
 * 7. FAB to pick new document (resets everything)
 */
@Composable
fun OcrScreen(
    viewModel: OcrViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // KEY CONCEPT: SnackbarHostState
    // - Manages showing snackbars (temporary messages)
    // - remember ensures same instance across recompositions
    val snackbarHostState = remember { SnackbarHostState() }

    // KEY PATTERN: Side Effect for Error Handling
    // - LaunchedEffect runs when key changes (errorMessage)
    // - Shows snackbar when error occurs
    // - Clears error after showing
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            // Pass URI directly to ViewModel
            // ViewModel will handle bitmap decoding on background thread
            viewModel.processImage(imageUri = selectedUri, context = context)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // KEY: Only show FAB when document is loaded
            // Allows user to pick new document
            if (uiState.hasDocument) {
                FloatingActionButton(
                    onClick = {
                        viewModel.clearDocument()
                        imagePickerLauncher.launch("image/*")
                    }
                ) {
                    Icon(Icons.Default.Add, "Select new document")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // CONDITIONAL RENDERING: Empty state vs Document + Chat
            if (!uiState.hasDocument && !uiState.isLoadingOcr) {
                // Empty state - no document selected
                EmptyStateView(
                    onSelectImageClick = {
                        imagePickerLauncher.launch("image/*")
                    }
                )
            } else {
                // Document loaded - show image + chat
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // TOP: Document image preview
                    uiState.documentImageUri?.let { imageUri ->
                        DocumentImagePreview(
                            imageUri = imageUri,
                            onClick = { viewModel.setFullScreenImage(true) }
                        )
                    }

                    // MIDDLE: Loading states
                    if (uiState.isLoadingOcr) {
                        // OCR in progress
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = "Extracting text from document...",
                                    modifier = Modifier.padding(top = 8.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    if (uiState.isLoadingLlm && !uiState.isLoadingOcr && uiState.chatMessages.isEmpty()) {
                        // LLM initialization in progress (after OCR completes)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "Initializing AI assistant...",
                                    modifier = Modifier.padding(top = 8.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // MIDDLE-BOTTOM: Chat messages
                    // KEY PATTERN: LazyColumn for Chat
                    // - weight(1f) makes it fill remaining space
                    // - Efficiently handles long message lists
                    val listState = rememberLazyListState()

                    // Auto-scroll to bottom when new messages arrive
                    LaunchedEffect(uiState.chatMessages.size) {
                        if (uiState.chatMessages.isNotEmpty()) {
                            listState.animateScrollToItem(uiState.chatMessages.size - 1)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        reverseLayout = false  // Normal order (top to bottom)
                    ) {
                        items(
                            items = uiState.chatMessages,
                            key = { it.timestamp }  // Stable key for performance
                        ) { message ->
                            ChatMessageItem(message = message)
                        }

                        // Show typing indicator when AI is responding
                        if (uiState.isLoadingLlm && uiState.chatMessages.isNotEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Text(
                                        text = "AI is thinking...",
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // BOTTOM: Chat input field
                    // KEY: imePadding pushes UI up when keyboard appears
                    ChatInputField(
                        onSendMessage = { question ->
                            viewModel.askQuestion(question)
                        },
                        enabled = uiState.hasDocument && !uiState.isLoadingLlm,
                        modifier = Modifier.imePadding()
                    )
                }
            }
        }
    }

    // Full-screen image dialog
    // KEY: Conditional composition - only rendered when needed
    if (uiState.showFullScreenImage) {
        uiState.documentImageUri?.let { imageUri ->
            FullScreenImageDialog(
                imageUri = imageUri,
                onDismiss = { viewModel.setFullScreenImage(false) }
            )
        }
    }
}
