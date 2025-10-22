package com.example.aidocumentreader.data.service

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Service responsible for performing OCR (Optical Character Recognition) on images.
 *
 * This class demonstrates several key Kotlin concepts:
 * - Suspend functions for async operations
 * - Coroutine context switching with Dispatchers
 * - Kotlin's Result type for error handling
 */
class OcrService {

    // ML Kit's text recognizer - configured for Latin script
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Extracts text from a bitmap image.
     *
     * KEY CONCEPT: suspend function
     * - The 'suspend' keyword means this function can be paused and resumed
     * - It can only be called from a coroutine or another suspend function
     * - This allows async work without blocking the calling thread
     *
     * @param bitmap The image to extract text from
     * @return Result containing extracted text or error
     */
    suspend fun extractText(bitmap: Bitmap): Result<String> {
        // withContext(Dispatchers.IO) - KEY CONCEPT:
        // - Switches the coroutine to the IO dispatcher
        // - Dispatchers.IO is optimized for I/O operations (network, disk, ML processing)
        // - Other dispatchers: Main (UI updates), Default (CPU-intensive work)
        // - This ensures ML processing doesn't block the UI thread
        return withContext(Dispatchers.IO) {
            try {
                // Convert Android Bitmap to ML Kit's InputImage format
                val inputImage = InputImage.fromBitmap(bitmap, 0)

                // Process the image with ML Kit
                // .await() - KEY CONCEPT:
                // - ML Kit uses Google Play Services Tasks API (callback-based)
                // - .await() is an extension function that converts Task to suspend function
                // - This lets us write async code in a sequential, readable way
                val visionText = recognizer.process(inputImage).await()

                // Extract all text blocks and join them
                val extractedText = visionText.text

                if (extractedText.isBlank()) {
                    Result.failure(Exception("No text found in image"))
                } else {
                    Result.success(extractedText)
                }
            } catch (e: Exception) {
                // Kotlin's Result type - KEY CONCEPT:
                // - Explicit error handling without exceptions bubbling up
                // - Forces callers to handle both success and failure cases
                // - More functional approach than try-catch everywhere
                Result.failure(e)
            }
        }
    }

    /**
     * Cleanup method to release ML Kit resources
     */
    fun close() {
        recognizer.close()
    }
}
