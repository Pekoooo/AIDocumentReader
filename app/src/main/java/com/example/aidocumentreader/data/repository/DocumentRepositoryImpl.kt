package com.example.aidocumentreader.data.repository

import android.graphics.Bitmap
import com.example.aidocumentreader.data.service.LlmService
import com.example.aidocumentreader.data.service.OcrService
import com.example.aidocumentreader.presentation.ocr.ChatMessage
import javax.inject.Inject

/**
 * Implementation of DocumentRepository.
 *
 * KEY CONCEPT: Multi-Service Coordination
 * - Now manages both OCR and LLM services
 * - Orchestrates workflow: Image → OCR → Text → LLM → Answer
 * - Hides implementation details from ViewModel
 *
 * DEPENDENCY INJECTION:
 * - @Inject tells Hilt to provide both services
 * - OcrService and LlmService provided by AppModule
 * - All dependencies injected automatically
 *
 * WHY CONSTRUCTOR INJECTION?
 * - Makes dependencies explicit and visible
 * - Easy to test (can pass mock dependencies in tests)
 * - Immutable dependencies (they can't be changed after construction)
 */
class DocumentRepositoryImpl @Inject constructor(
    private val ocrService: OcrService,
    private val llmService: LlmService
) : DocumentRepository {

    /**
     * Extracts text from an image by delegating to OcrService.
     *
     * REPOSITORY PATTERN IN ACTION:
     * - Simple pass-through to OcrService
     * - Could add caching, transformation, error recovery
     * - ViewModel doesn't know how OCR works
     */
    override suspend fun extractTextFromImage(bitmap: Bitmap): Result<String> {
        return ocrService.extractText(bitmap)
    }

    /**
     * Initializes the LLM by delegating to LlmService.
     *
     * KEY PATTERN: Lazy Initialization
     * - LLM only initialized when needed
     * - Called proactively after OCR to reduce latency
     * - Initialization is idempotent (safe to call multiple times)
     */
    override suspend fun initializeLlm(): Result<Unit> {
        return llmService.initialize()
    }

    /**
     * Asks a question about the document using LLM.
     *
     * REPOSITORY COORDINATION:
     * - Takes document text (from OCR)
     * - Takes conversation history (from UI state)
     * - Passes to LLM service
     * - Returns AI's response
     *
     * FUTURE ENHANCEMENTS could include:
     * - Caching responses to identical questions
     * - Combining LLM with web search
     * - Saving Q&A to database
     * - Analytics on question patterns
     */
    override suspend fun askQuestionAboutDocument(
        documentText: String,
        conversationHistory: List<ChatMessage>,
        question: String
    ): Result<String> {
        return llmService.askQuestion(documentText)
    }
}
