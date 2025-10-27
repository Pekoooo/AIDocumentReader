package com.example.aidocumentreader.data.service

import android.content.Context
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for extracting text from PDF files using PDFBox.
 *
 * PDFBOX ANDROID:
 * - Android port of Apache PDFBox library
 * - Extracts text, preserves formatting
 * - Handles various PDF versions and encodings
 *
 * PERFORMANCE:
 * - ~500ms per 10 pages (typical)
 * - Much faster than OCR (no image processing needed)
 * - Text is already embedded in PDF
 *
 * @Inject constructor enables Hilt dependency injection
 * @Singleton ensures only one instance per app
 */
@Singleton
class PdfTextExtractor @Inject constructor(
    private val context: Context
) {

    init {
        // Initialize PDFBox for Android
        // Must be called before any PDFBox operations
        PDFBoxResourceLoader.init(context)
    }

    /**
     * Extract text from a PDF file.
     *
     * @param file The PDF file to extract from
     * @return Result containing extracted text or error
     *
     * EXTRACTION PROCESS:
     * 1. Load PDF document
     * 2. Iterate through all pages
     * 3. Extract text from each page
     * 4. Concatenate with page boundaries preserved
     *
     * TEXT FORMATTING:
     * - Preserves paragraphs and line breaks
     * - Removes excessive whitespace
     * - Handles multi-column layouts reasonably
     */
    suspend fun extractText(file: File): Result<PdfExtractionResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting PDF text extraction: ${file.name}")
            val startTime = System.currentTimeMillis()

            // Load the PDF document
            val document = PDDocument.load(file)

            try {
                val pageCount = document.numberOfPages
                Log.d(TAG, "PDF has $pageCount pages")

                // Configure text stripper
                val stripper = PDFTextStripper().apply {
                    // Sort text by position for better readability
                    sortByPosition = true

                    // Add line separators between pages
                    lineSeparator = "\n"
                }

                // Extract text from all pages
                val fullText = stripper.getText(document)

                // Clean up the text
                val cleanedText = cleanText(fullText)

                val extractionTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Extraction complete: ${cleanedText.length} chars in ${extractionTime}ms")

                // Count words (approximate)
                val wordCount = cleanedText.split(Regex("\\s+")).size

                Result.success(
                    PdfExtractionResult(
                        text = cleanedText,
                        pageCount = pageCount,
                        wordCount = wordCount,
                        extractionTimeMs = extractionTime
                    )
                )
            } finally {
                // Always close the document
                document.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract text from PDF", e)
            Result.failure(
                PdfExtractionException(
                    "Failed to extract text from PDF: ${e.message}",
                    e
                )
            )
        }
    }

    /**
     * Clean extracted text.
     *
     * CLEANING OPERATIONS:
     * - Remove excessive blank lines (3+ → 2)
     * - Normalize whitespace
     * - Remove form feed characters
     * - Trim leading/trailing whitespace
     */
    private fun cleanText(text: String): String {
        return text
            // Remove form feed characters
            .replace("\u000C", "")
            // Normalize multiple spaces to single space
            .replace(Regex(" {2,}"), " ")
            // Limit consecutive newlines to 2 max
            .replace(Regex("\n{3,}"), "\n\n")
            // Trim whitespace from each line
            .lines()
            .joinToString("\n") { it.trim() }
            // Final trim
            .trim()
    }

    /**
     * Extract metadata from PDF without extracting all text.
     * Useful for quick validation.
     */
    suspend fun getMetadata(file: File): Result<PdfMetadata> = withContext(Dispatchers.IO) {
        try {
            val document = PDDocument.load(file)
            try {
                val info = document.documentInformation

                Result.success(
                    PdfMetadata(
                        title = info.title ?: file.nameWithoutExtension,
                        author = info.author,
                        pageCount = document.numberOfPages,
                        creationDate = info.creationDate?.timeInMillis
                    )
                )
            } finally {
                document.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract PDF metadata", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "RAG"
    }
}

/**
 * Result of PDF text extraction.
 */
data class PdfExtractionResult(
    val text: String,
    val pageCount: Int,
    val wordCount: Int,
    val extractionTimeMs: Long
)

/**
 * PDF metadata without full text extraction.
 */
data class PdfMetadata(
    val title: String,
    val author: String?,
    val pageCount: Int,
    val creationDate: Long?
)

/**
 * Exception thrown when PDF extraction fails.
 */
class PdfExtractionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
