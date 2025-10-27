package com.example.aidocumentreader.data.service

import android.content.Context
import android.icu.util.TimeUnit
import android.util.Log
import com.example.aidocumentreader.presentation.ocr.ChatMessage
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * Service for interacting with the local LLM (Gemma 3 1B via MediaPipe).
 *
 * KEY CONCEPT: MediaPipe Session-Based Architecture
 * - Engine (LlmInference): Loads the model once
 * - Session (LlmInferenceSession): Handles individual conversations with specific parameters
 * - This separation allows efficient multi-conversation support
 *
 * KEY FIX FROM GOOGLE SAMPLE:
 * - We were calling generateResponse() directly on the engine (WRONG!)
 * - Correct approach: Create session, configure it, then generate
 * - Session manages token budget and conversation context
 *
 * MEMORY MANAGEMENT (Gemma 3 1B - 8-bit quantized):
 * - MAX_TOKENS = 2048 (total budget for input + output)
 * - DECODE_TOKEN_OFFSET = 512 (reserved for model output)
 * - Effective input limit = 2048 - 512 = 1536 tokens
 * - Smaller 1B model allows larger context windows than 2B
 */
class LlmService(
    private val context: Context
) {
    private var llmInference: LlmInference? = null
    private var llmInferenceSession: LlmInferenceSession? = null
    private var isInitialized = false

    /**
     * Initializes the LLM engine.
     *
     * KEY CHANGE: Only creates the ENGINE here, not the session.
     * Session is created per-conversation with specific parameters.
     *
     * MEDIAPIPE ENGINE SETUP:
     * - Copies model from assets to cache directory (if needed)
     * - Configures ONLY model path and max tokens (engine-level config)
     * - Temperature, topK, topP go on SESSION (not here)
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        if (isInitialized) {
            Log.d(TAG, "LLM already initialized, skipping")
            return@withContext Result.success(Unit)
        }

        try {
            Log.d(TAG, "Starting LLM initialization")

            // Copy model from assets to cache if not already there
            Log.d(TAG, "Copying model to cache if needed")
            val modelFile = copyModelToCache()
            Log.d(TAG, "Model file ready at: ${modelFile.absolutePath}")
            Log.d(TAG, "Model file size: ${modelFile.length() / (1024 * 1024)} MB")

            // Create LlmInference ENGINE options
            // KEY: Only model path and max tokens go here
            Log.d(TAG, "Creating LlmInference options")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(MAX_TOKENS)  // Total budget for conversation
                .build()
            Log.d(TAG, "LlmInference options created successfully")

            // Initialize the inference ENGINE
            // This loads the model into memory (takes 1-3 seconds for 1B model)
            Log.d(TAG, "Loading Gemma 3 1B model into memory - this may take 1-3 seconds")
            Log.d(TAG, "WARNING: Loading ~800MB model may fail on devices with limited RAM")
            val startTime = System.currentTimeMillis()

            try {
                llmInference = LlmInference.createFromOptions(context, options)
                val loadTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "LLM model loaded successfully in ${loadTime}ms")
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "Out of memory while loading LLM model", e)
                throw Exception("Device does not have enough memory to load LLM model (requires ~1.5GB free RAM)", e)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create LLM inference engine", e)
                throw Exception("Failed to load LLM model: ${e.message}", e)
            }

            isInitialized = true
            Log.d(TAG, "LLM initialization complete")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LLM", e)
            Result.failure(Exception("Failed to initialize LLM: ${e.message}", e))
        }
    }

    /**
     * Copies the model file from assets to cache directory.
     *
     * WHY COPY?
     * - MediaPipe needs a file path, not an asset stream
     * - Assets are compressed in APK, need to extract
     * - Cache directory is writable and app-private
     *
     * OPTIMIZATION:
     * - Only copies if file doesn't exist in cache
     * - File persists across app launches (until cache is cleared)
     */
    private fun copyModelToCache(): File {
        val cacheFile = File(context.cacheDir, MODEL_FILE_NAME)

        // If already copied, return it
        if (cacheFile.exists()) {
            Log.d(TAG, "Model already exists in cache, skipping copy")
            return cacheFile
        }

        // Copy from assets to cache
        Log.d(TAG, "Model not in cache, copying from assets (this is a one-time operation)")
        Log.d(TAG, "Copying $MODEL_FILE_NAME from assets to ${cacheFile.absolutePath}")

        val startTime = System.currentTimeMillis()
        context.assets.open(MODEL_FILE_NAME).use { input ->
            cacheFile.outputStream().use { output ->
                val bytesWritten = input.copyTo(output)
                val copyTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Model copied successfully: ${bytesWritten / (1024 * 1024)} MB in ${copyTime}ms")
            }
        }

        return cacheFile
    }

    /**
     * Creates a new inference session.
     *
     * KEY CONCEPT: Session-Based Conversations
     * - Each session has its own parameters (temperature, topK, topP)
     * - Session maintains conversation context
     * - Must be closed and recreated between documents
     *
     * GOOGLE'S APPROACH:
     * - Temperature, topK, topP configured HERE (not on engine)
     * - Session handles token budget management
     * - Session can be reset without reloading model
     */
    private fun createSession(): Result<Unit> {
        return try {
            Log.d(TAG, "Creating new LlmInferenceSession")

            val sessionOptions = LlmInferenceSessionOptions.builder()
                .setTemperature(TEMPERATURE)
                .setTopK(TOP_K)
                .setTopP(TOP_P)
                .build()

            llmInferenceSession = LlmInferenceSession.createFromOptions(
                llmInference,
                sessionOptions
            )

            Log.d(TAG, "LlmInferenceSession created successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create LlmInferenceSession", e)
            Result.failure(Exception("Failed to create inference session: ${e.message}", e))
        }
    }

    /**
     * Closes and resets the current session.
     * Call this when starting a new document or clearing conversation.
     */
    fun resetSession() {
        llmInferenceSession?.close()
        llmInferenceSession = null
        Log.d(TAG, "Session reset")
    }

    companion object {
        private const val TAG = "LlmService"
        private const val MODEL_FILE_NAME = "Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task"

        // Token budget configuration (optimized for speed on Gemma 3 1B)
        private const val MAX_TOKENS = 1024  // Total tokens for input + output (reduced for speed)
        private const val DECODE_TOKEN_OFFSET = 200  // Reserved for model output (shorter answers = faster)
        private const val EFFECTIVE_INPUT_LIMIT = MAX_TOKENS - DECODE_TOKEN_OFFSET  // 824 tokens

        // Session parameters (tuned for factual Q&A)
        private const val TEMPERATURE = 0.3f  // Lower for more factual, less creative
        private const val TOP_K = 25         // More focused token selection
        private const val TOP_P = 0.8f       // More deterministic sampling
    }

    /**
     * Asks a question about the document.
     *
     * KEY CHANGES FROM ORIGINAL:
     * 1. Create session if needed
     * 2. Truncate prompt to fit token budget
     * 3. Use session.addQueryChunk() + generateResponseAsync()
     * 4. Return synchronous result (convert async to sync for simplicity)
     *
     * GOOGLE'S FLOW:
     * - addQueryChunk(prompt) - Add user input to session
     * - generateResponseAsync() - Get model response
     * - Session manages context window automatically
     */
    suspend fun askQuestion(
        prompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Received prompt for LLM inference")

            // Ensure LLM is initialized
            if (!isInitialized) {
                Log.d(TAG, "LLM not initialized, initializing now")
                initialize().onFailure {
                    Log.e(TAG, "Failed to initialize LLM")
                    return@withContext Result.failure(it)
                }
            }

            // Create session if needed
            if (llmInferenceSession == null) {
                Log.d(TAG, "No active session, creating new one")
                createSession().onFailure {
                    return@withContext Result.failure(it)
                }
            }

            Log.d(TAG, "Prompt length: ${prompt.length} characters (~${prompt.length / 4} tokens estimate)")

            // Add query to session
            Log.d(TAG, "Adding query chunk to session")
            llmInferenceSession?.addQueryChunk(prompt)

            // Generate response synchronously
            // Note: Google uses generateResponseAsync with progress callbacks
            // For simplicity, we use synchronous generation here
            Log.d(TAG, "Generating LLM response - this may take 5-15 seconds on CPU")
            val startTime = System.currentTimeMillis()

            val response = llmInferenceSession?.generateResponse()
                ?: return@withContext Result.failure(Exception("Session not initialized"))

            val inferenceTime = (System.currentTimeMillis() - startTime) / 1000

            Log.d(TAG, "LLM response generated in ${inferenceTime}s")
            Log.d(TAG, "Response length: ${response.length} characters")

            // Return the generated text
            Result.success(response.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate response", e)
            Result.failure(Exception("Failed to generate response: ${e.message}", e))
        }
    }


    /**
     * Cleans up resources.
     * Should be called when service is no longer needed.
     */
    fun close() {
        llmInferenceSession?.close()
        llmInference?.close()
        llmInferenceSession = null
        llmInference = null
        isInitialized = false
        Log.d(TAG, "LlmService closed and cleaned up")
    }
}
