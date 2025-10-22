package com.example.aidocumentreader.presentation.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aidocumentreader.data.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for the OCR + LLM Q&A screen.
 *
 * KEY EVOLUTION:
 * - v1: Simple OCR (processImage, clearText)
 * - v2: Added LLM chat (askQuestion, initializeLlm, document image handling)
 *
 * RESPONSIBILITIES:
 * - Manage document state (image URI, extracted text)
 * - Coordinate OCR and LLM services via repository
 * - Manage chat conversation state
 * - Handle multiple loading states (OCR vs LLM)
 * - Provide methods for UI events
 *
 * KEY CONCEPTS DEMONSTRATED:
 * - Complex state management with multiple concerns
 * - Sequential async operations (OCR → LLM init)
 * - List state management (chat messages)
 * - Error handling for multiple services
 */
@HiltViewModel
class OcrViewModel @Inject constructor(
    private val repository: DocumentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrUiState())
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    /**
     * Processes a new document image.
     *
     * WORKFLOW:
     * 1. Reset all state (clear previous document & chat)
     * 2. Save image URI for display
     * 3. Decode bitmap on background thread (prevents UI jank)
     * 4. Run OCR to extract text
     * 5. Initialize LLM proactively (for faster first question)
     * 6. Add welcome message from AI
     *
     * KEY PATTERN: Sequential Async Operations
     * - Bitmap decoding on IO thread to avoid blocking UI
     * - OCR must complete before LLM can answer questions
     * - LLM initialization happens proactively (not blocking)
     * - Each step updates UI state appropriately
     */
    fun processImage(imageUri: Uri, context: Context) {
        viewModelScope.launch {
            // Reset state for new document
            _uiState.update {
                OcrUiState(
                    documentImageUri = imageUri,
                    isLoadingOcr = true
                )
            }

            // Decode bitmap on background thread to avoid UI jank
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(
                            ImageDecoder.createSource(context.contentResolver, imageUri)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                    }
                } catch (e: Exception) {
                    null
                }
            }

            if (bitmap == null) {
                _uiState.update {
                    it.copy(
                        isLoadingOcr = false,
                        errorMessage = "Failed to load image"
                    )
                }
                return@launch
            }

            // Extract text with OCR
            val ocrResult = repository.extractTextFromImage(bitmap)

            ocrResult.fold(
                onSuccess = { extractedText ->
                    // OCR succeeded - update state with text
                    _uiState.update { it.copy(
                        extractedText = extractedText,
                        isLoadingOcr = false
                    )}

                    // Initialize LLM proactively (for faster responses later)
                    initializeLlmInBackground(extractedText)
                },
                onFailure = { error ->
                    // OCR failed - show error
                    _uiState.update { it.copy(
                        isLoadingOcr = false,
                        errorMessage = "Failed to extract text: ${error.message}"
                    )}
                }
            )
        }
    }

    /**
     * Initializes the LLM in the background.
     *
     * KEY PATTERN: Proactive Loading
     * - Called after OCR succeeds
     * - Shows loading state while initializing
     * - Adds welcome message when ready
     * - Errors are handled gracefully
     *
     * WHY PROACTIVE?
     * - LLM initialization takes 2-5 seconds
     * - Doing it early means first question answers faster
     * - User doesn't wait when they ask first question
     */
    private fun initializeLlmInBackground(extractedText: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLlm = true) }

            val initResult = repository.initializeLlm()

            initResult.fold(
                onSuccess = {
                    // LLM ready - add welcome message
                    val welcomeMessage = ChatMessage(
                        text = "Hi! I've analyzed your document. What would you like to know about it?",
                        isFromUser = false
                    )

                    _uiState.update { it.copy(
                        isLoadingLlm = false,
                        chatMessages = listOf(welcomeMessage)
                    )}
                },
                onFailure = { error ->
                    // LLM init failed - show error
                    _uiState.update { it.copy(
                        isLoadingLlm = false,
                        errorMessage = "Failed to initialize AI: ${error.message}"
                    )}
                }
            )
        }
    }

    /**
     * Asks a question about the document.
     *
     * KEY PATTERN: Optimistic UI Updates
     * - Add user message immediately (optimistic)
     * - Show "AI is typing..." indicator
     * - Get AI response from LLM
     * - Add AI message to chat
     *
     * CONVERSATION FLOW:
     * 1. User message added to state
     * 2. LLM loading starts
     * 3. Repository called with: document text + history + question
     * 4. LLM generates response
     * 5. AI message added to state
     * 6. Loading stops
     *
     * ERROR HANDLING:
     * - If no document: show error
     * - If LLM fails: show error message in chat
     */
    fun askQuestion(question: String) {
        val currentState = _uiState.value

        // Validation: must have document
        if (currentState.extractedText == null) {
            _uiState.update { it.copy(
                errorMessage = "Please select a document first"
            )}
            return
        }

        // Validation: question must not be empty
        if (question.isBlank()) return

        viewModelScope.launch {
            // Add user message immediately (optimistic update)
            val userMessage = ChatMessage(
                text = question,
                isFromUser = true
            )

            _uiState.update { it.copy(
                chatMessages = it.chatMessages + userMessage,
                isLoadingLlm = true,
                errorMessage = null
            )}

            // Get AI response
            val result = repository.askQuestionAboutDocument(
                documentText = currentState.extractedText,
                conversationHistory = currentState.chatMessages,
                question = question
            )

            // Handle response
            result.fold(
                onSuccess = { aiResponse ->
                    // Add AI response to chat
                    val aiMessage = ChatMessage(
                        text = aiResponse,
                        isFromUser = false
                    )

                    _uiState.update { it.copy(
                        chatMessages = it.chatMessages + aiMessage,
                        isLoadingLlm = false
                    )}
                },
                onFailure = { error ->
                    // Show error as AI message
                    val errorMessage = ChatMessage(
                        text = "Sorry, I encountered an error: ${error.message}",
                        isFromUser = false
                    )

                    _uiState.update { it.copy(
                        chatMessages = it.chatMessages + errorMessage,
                        isLoadingLlm = false
                    )}
                }
            )
        }
    }

    /**
     * Shows or hides the full-screen image dialog.
     */
    fun setFullScreenImage(show: Boolean) {
        _uiState.update { it.copy(showFullScreenImage = show) }
    }

    /**
     * Clears the current document and resets all state.
     * Used when user wants to pick a new document.
     */
    fun clearDocument() {
        _uiState.value = OcrUiState()
    }

    /**
     * Clears only the error message.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
