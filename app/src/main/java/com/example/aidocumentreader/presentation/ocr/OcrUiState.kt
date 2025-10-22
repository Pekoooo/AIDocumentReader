package com.example.aidocumentreader.presentation.ocr

import android.net.Uri

/**
 * UI State for the OCR + LLM Q&A screen.
 *
 * KEY CONCEPT: Complex UI State Management
 * - Represents EVERYTHING the UI needs to display
 * - Now manages both OCR and chat state
 * - Multiple loading states for different operations
 * - Immutable for predictable updates
 *
 * DESIGN EVOLUTION:
 * - v1: Simple OCR state (extractedText, isLoading)
 * - v2: Added chat messages, document image, separate loading states
 *
 * COMPOSE BEST PRACTICE: "State flows down, events flow up"
 * - ViewModel holds this state
 * - UI observes and recomposes on changes
 * - UI sends events (pickDocument, askQuestion, etc.)
 */
data class OcrUiState(
    /**
     * URI of the selected document image
     * Used to display image preview in UI
     * null = no document selected
     */
    val documentImageUri: Uri? = null,

    /**
     * The extracted text from OCR.
     * NOT shown to user, used as context for LLM
     * null = no text extracted yet
     */
    val extractedText: String? = null,

    /**
     * List of chat messages (user + AI)
     * Ordered chronologically (oldest first)
     * Empty list = no conversation yet
     */
    val chatMessages: List<ChatMessage> = emptyList(),

    /**
     * Whether OCR processing is currently running
     * Shows loading state when picking new document
     */
    val isLoadingOcr: Boolean = false,

    /**
     * Whether LLM is processing a question
     * Shows "AI is typing..." indicator
     */
    val isLoadingLlm: Boolean = false,

    /**
     * Error message to display to user
     * null = no error
     */
    val errorMessage: String? = null,

    /**
     * Whether to show full-screen image dialog
     * User taps on image preview to view full-screen
     */
    val showFullScreenImage: Boolean = false
) {
    /**
     * Derived: Whether a document has been selected and processed
     */
    val hasDocument: Boolean
        get() = documentImageUri != null && extractedText != null

    /**
     * Derived: Whether there are any chat messages
     */
    val hasMessages: Boolean
        get() = chatMessages.isNotEmpty()

    /**
     * Derived: Any loading operation in progress
     */
    val isAnyLoading: Boolean
        get() = isLoadingOcr || isLoadingLlm
}
