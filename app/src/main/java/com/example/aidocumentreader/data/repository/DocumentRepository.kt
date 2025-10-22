package com.example.aidocumentreader.data.repository

import android.graphics.Bitmap
import com.example.aidocumentreader.presentation.ocr.ChatMessage

/**
 * Repository interface for document-related operations.
 *
 * KEY CONCEPT: Repository Pattern
 * - Acts as a single source of truth for data operations
 * - Abstracts the data layer from the domain/presentation layers
 * - Can combine multiple data sources (OCR service, LLM service, database, etc.)
 * - Makes testing easier (can swap implementations with mocks)
 *
 * WHY USE AN INTERFACE?
 * - Dependency Inversion Principle: Depend on abstractions, not concrete implementations
 * - Easy to create fake/mock implementations for testing
 * - Can swap implementations without changing ViewModels
 *
 * EVOLUTION:
 * - v1: Just OCR (extractTextFromImage)
 * - v2: Added LLM Q&A (askQuestionAboutDocument, initializeLlm)
 */
interface DocumentRepository {
    /**
     * Extracts text from an image using OCR.
     *
     * @param bitmap The image to process
     * @return Result containing extracted text or error
     */
    suspend fun extractTextFromImage(bitmap: Bitmap): Result<String>

    /**
     * Initializes the LLM (loads model into memory).
     *
     * KEY CONCEPT: Explicit Initialization
     * - LLM loading is heavy (2-5 seconds)
     * - ViewModel can call this proactively (e.g., after OCR completes)
     * - Shows loading state to user
     * - Lazy loaded: only happens once
     *
     * @return Result indicating success or failure
     */
    suspend fun initializeLlm(): Result<Unit>

    /**
     * Asks a question about the document using the LLM.
     *
     * KEY CONCEPT: Context-Aware Q&A
     * - Uses extracted OCR text as context
     * - Includes conversation history for coherent multi-turn dialogue
     * - LLM generates answer based on document content
     *
     * @param documentText The OCR-extracted text (context)
     * @param conversationHistory Previous chat messages
     * @param question User's current question
     * @return Result with AI's response or error
     */
    suspend fun askQuestionAboutDocument(
        documentText: String,
        conversationHistory: List<ChatMessage>,
        question: String
    ): Result<String>
}
