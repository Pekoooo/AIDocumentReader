package com.example.aidocumentreader.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aidocumentreader.data.repository.RagRepository
import com.example.aidocumentreader.data.repository.RagResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the RAG-powered chat interface.
 *
 * KEY RESPONSIBILITIES:
 * - Handle user questions
 * - Perform vector search across ALL uploaded documents
 * - Generate answers using LLM with retrieved context
 * - Display chat history with source citations
 * - Show typing indicators during processing
 *
 * RAG PIPELINE (triggered on each question):
 * 1. Embed question using MobileBERT (~20ms)
 * 2. HNSW vector search across all chunks (~8ms for 10k chunks)
 * 3. Retrieve top-K most relevant chunks (default: 3)
 * 4. Build context prompt with retrieved text
 * 5. Generate answer with Gemma 3 1B (~2-5 seconds)
 * 6. Display answer with source citations
 *
 * PERFORMANCE:
 * - Vector search: ~8ms for 10,000 chunks (HNSW magic!)
 * - LLM inference: ~2-5 seconds for 100-200 token answers
 * - Total latency: ~2-5 seconds per question
 */
@HiltViewModel
class RagChatViewModel @Inject constructor(
    private val ragRepository: RagRepository
) : ViewModel() {

    /**
     * Represents a single message in the chat.
     *
     * TYPES:
     * - User: Question from user
     * - Assistant: Answer from LLM with optional sources
     * - Error: Error message shown in chat
     *
     * SOURCES:
     * - List of document chunks used to generate the answer
     * - Each source shows: document name, page number, chunk text
     * - User can expand/collapse sources to see context
     */
    data class ChatMessage(
        val id: String = java.util.UUID.randomUUID().toString(),
        val content: String,
        val isUser: Boolean,
        val timestamp: Long = System.currentTimeMillis(),
        val sources: List<SourceInfo>? = null,
        val isError: Boolean = false
    )

    /**
     * Information about a source chunk used for answering.
     *
     * DISPLAY FORMAT:
     * "Document Name - Page X"
     * "...relevant chunk text..."
     * "(Relevance: 89%)"
     */
    data class SourceInfo(
        val documentTitle: String,
        val pageNumber: Int,
        val chunkText: String,
        val relevance: Int  // 0-100
    )

    /**
     * UI State for the chat screen.
     */
    sealed class UiState {
        data object Idle : UiState()
        data object Processing : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    /**
     * Ask a question and get an answer from the RAG pipeline.
     *
     * FLOW:
     * 1. Add user question to chat
     * 2. Set typing indicator
     * 3. Embed question → Vector search → Retrieve chunks
     * 4. Build prompt with context
     * 5. Generate answer with LLM
     * 6. Add answer to chat with sources
     * 7. Clear typing indicator
     *
     * ERROR HANDLING:
     * - No documents uploaded: "Please upload documents first"
     * - Embedding service not initialized: "Initializing AI models..."
     * - LLM error: Display error in chat
     * - Vector search returns no results: "No relevant information found"
     *
     * @param question User's question text
     * @param topK Number of chunks to retrieve (default: 3)
     */
    fun askQuestion(question: String, topK: Int = 10) {
        if (question.isBlank()) return

        viewModelScope.launch {
            Log.d(TAG, "User asked question: $question")

            // Add user message
            val userMessage = ChatMessage(
                content = question,
                isUser = true
            )
            _messages.value = _messages.value + userMessage

            // Set processing state
            _uiState.value = UiState.Processing
            _isTyping.value = true
            Log.d(TAG, "Processing question with topK=$topK")

            try {
                // Perform RAG pipeline
                ragRepository.answerQuestion(question, topK)
                    .onSuccess { ragResponse ->
                        Log.d(TAG, "RAG pipeline successful, answer received")
                        // Convert sources to SourceInfo
                        val sources = ragResponse.sources.map { source ->
                            SourceInfo(
                                documentTitle = source.documentTitle,
                                pageNumber = source.pageNumber,
                                chunkText = source.excerpt,
                                relevance = source.relevance
                            )
                        }

                        // Add assistant message with sources
                        val assistantMessage = ChatMessage(
                            content = ragResponse.answer,
                            isUser = false,
                            sources = sources
                        )
                        _messages.value = _messages.value + assistantMessage
                        Log.d(TAG, "Answer added to chat with ${sources.size} sources")
                    }
                    .onFailure { error ->
                        Log.e(TAG, "RAG pipeline failed", error)
                        // Add error message to chat
                        val errorMessage = ChatMessage(
                            content = "Sorry, I encountered an error: ${error.message}",
                            isUser = false,
                            isError = true
                        )
                        _messages.value = _messages.value + errorMessage
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in askQuestion", e)
                val errorMessage = ChatMessage(
                    content = "Unexpected error: ${e.message}",
                    isUser = false,
                    isError = true
                )
                _messages.value = _messages.value + errorMessage
            } finally {
                _isTyping.value = false
                _uiState.value = UiState.Idle
                Log.d(TAG, "Question processing complete")
            }
        }
    }

    /**
     * Clear the entire chat history.
     * Useful for starting fresh or debugging.
     */
    fun clearChat() {
        _messages.value = emptyList()
    }

    /**
     * Get statistics about the vector database.
     *
     * METRICS:
     * - Total documents uploaded
     * - Total chunks in database
     * - Total words indexed
     * - Database size on disk
     *
     * Useful for debugging and showing to user.
     */
    fun getVectorDbStats() {
        viewModelScope.launch {
            ragRepository.getVectorDbStats()
                .onSuccess { stats ->
                    // Could emit to a separate StateFlow if needed
                    // For now, just logging
                    println("Vector DB Stats: $stats")
                }
        }
    }

    /**
     * Initialize the embedding service and LLM service.
     *
     * INITIALIZATION:
     * - Load MobileBERT model (~100ms)
     * - Load Gemma 3 1B model (~1-3 seconds)
     * - Show loading indicator during initialization
     *
     * Called when user first opens the chat screen.
     */
    fun initializeServices() {
        viewModelScope.launch {
            Log.d(TAG, "RagChatViewModel: Initializing services...")
            _uiState.value = UiState.Processing

            ragRepository.initializeServices()
                .onSuccess {
                    Log.d(TAG, "Services initialized successfully")
                    _uiState.value = UiState.Idle
                    // Add welcome message
                    val welcomeMessage = ChatMessage(
                        content = "Hi! I'm ready to answer questions about your uploaded documents. Ask me anything!",
                        isUser = false
                    )
                    _messages.value = listOf(welcomeMessage)
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to initialize services", error)
                    _uiState.value = UiState.Error(
                        "Failed to initialize AI models: ${error.message}"
                    )
                }
        }
    }

    companion object {
        private const val TAG = "RAG"
    }
}
