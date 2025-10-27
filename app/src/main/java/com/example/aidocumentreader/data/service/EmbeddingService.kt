package com.example.aidocumentreader.data.service

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Service for generating text embeddings using MobileBERT.
 *
 * EMBEDDINGS EXPLAINED:
 * - Converts text into high-dimensional vectors (512 dimensions)
 * - Similar meaning → similar vectors
 * - Enables semantic search (not just keyword matching)
 *
 * MOBILEBERT MODEL:
 * - Size: ~25MB
 * - Output: FloatArray[512]
 * - Speed: ~20ms per text chunk
 * - Trained on semantic similarity tasks
 *
 * RAG PIPELINE ROLE:
 * - Generate embeddings for document chunks
 * - Generate embedding for user questions
 * - Compare via cosine similarity (handled by ObjectBox HNSW)
 *
 * @Inject constructor enables Hilt dependency injection
 * @Singleton ensures only one instance (model loaded once)
 */
@Singleton
class EmbeddingService @Inject constructor(
    private val context: Context
) {
    private var textEmbedder: TextEmbedder? = null
    private var isInitialized = false

    /**
     * Initialize the embedding model.
     * Must be called before embed().
     *
     * INITIALIZATION:
     * - Loads mobile_bert.tflite from assets
     * - Takes ~100ms (much faster than LLM!)
     * - Model stays in memory for fast inference
     *
     * DELEGATE OPTIONS:
     * - CPU: Works on all devices, consistent performance
     * - GPU: Faster but not all devices support it
     * - We use CPU for reliability
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        if (isInitialized) {
            Log.d(TAG, "EmbeddingService already initialized")
            return@withContext Result.success(Unit)
        }

        try {
            Log.d(TAG, "Initializing MobileBERT embedding model")
            val startTime = System.currentTimeMillis()

            val baseOptions = BaseOptions.builder()
                .setDelegate(Delegate.CPU)  // Use CPU for reliability
                .setModelAssetPath(MODEL_PATH)
                .build()

            val options = TextEmbedderOptions.builder()
                .setBaseOptions(baseOptions)
                .build()

            textEmbedder = TextEmbedder.createFromOptions(context, options)

            val loadTime = System.currentTimeMillis() - startTime
            isInitialized = true

            Log.d(TAG, "MobileBERT loaded successfully in ${loadTime}ms")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize embedding model", e)
            Result.failure(
                EmbeddingException(
                    "Failed to initialize embedding model: ${e.message}",
                    e
                )
            )
        }
    }

    /**
     * Generate embedding vector for text.
     *
     * @param text The text to embed (typically 200-500 chars)
     * @return FloatArray[512] - the embedding vector
     *
     * EMBEDDING PROCESS:
     * 1. Tokenize text
     * 2. Run through MobileBERT
     * 3. Extract embedding layer output
     * 4. L2 normalize (for cosine similarity)
     *
     * PERFORMANCE:
     * - ~20ms per call
     * - Can process ~50 chunks per second
     * - For 130 chunks: ~2.6 seconds total
     *
     * IMPORTANT:
     * - Must call initialize() first!
     * - Returns normalized vector (ready for cosine similarity)
     * - Thread-safe (can call from multiple coroutines)
     */
    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        // Lazy initialization
        if (!isInitialized) {
            Log.d(TAG, "Lazy initialization triggered")
            initialize().getOrThrow()
        }

        val embedder = textEmbedder
            ?: throw EmbeddingException("Embedding model not initialized")

        try {
            val startTime = System.currentTimeMillis()
            val textPreview = text.take(50) + if (text.length > 50) "..." else ""
            Log.d(TAG, "Generating embedding for text: $textPreview")

            // Generate embedding
            val result = embedder.embed(text)

            // Extract the embedding vector
            val embedding = result.embeddingResult()
                .embeddings()
                .firstOrNull()
                ?.floatEmbedding()
                ?: throw EmbeddingException("Failed to extract embedding from result")

            // MobileBERT already returns normalized vectors,
            // but we ensure it here for consistency
            val normalized = normalizeVector(embedding)

            val embedTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Embedding generated in ${embedTime}ms (dimensions: ${normalized.size})")

            normalized
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate embedding", e)
            throw EmbeddingException("Failed to generate embedding: ${e.message}", e)
        }
    }

    /**
     * Calculate cosine similarity between two vectors.
     *
     * COSINE SIMILARITY:
     * - Measures angle between vectors
     * - Range: -1 (opposite) to 1 (identical)
     * - For semantic similarity, typically 0.6-0.9 is relevant
     *
     * FORMULA:
     * similarity = (A · B) / (||A|| × ||B||)
     *
     * NOTE: ObjectBox HNSW does this automatically!
     * This method is here for testing/debugging.
     *
     * @param a First embedding vector
     * @param b Second embedding vector
     * @return Similarity score (0-1, higher = more similar)
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size) { "Vectors must have same dimensions" }

        // Dot product: A · B
        val dotProduct = a.zip(b).sumOf { (x, y) -> (x * y).toDouble() }

        // Magnitudes: ||A|| and ||B||
        val magnitudeA = sqrt(a.sumOf { (it * it).toDouble() })
        val magnitudeB = sqrt(b.sumOf { (it * it).toDouble() })

        // Cosine similarity
        return dotProduct / (magnitudeA * magnitudeB)
    }

    /**
     * L2 normalize a vector (make length = 1).
     * This enables cosine similarity to be computed as simple dot product.
     *
     * @param vector The vector to normalize (modified in place!)
     * @return The same vector (for chaining)
     */
    private fun normalizeVector(vector: FloatArray): FloatArray {
        val magnitude = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()

        if (magnitude > 0) {
            for (i in vector.indices) {
                vector[i] /= magnitude
            }
        }

        return vector
    }

    /**
     * Clean up resources.
     * Call when service is no longer needed.
     */
    fun close() {
        textEmbedder?.close()
        textEmbedder = null
        isInitialized = false
        Log.d(TAG, "EmbeddingService closed")
    }

    companion object {
        private const val TAG = "RAG"
        private const val MODEL_PATH = "models/mobile_bert.tflite"

        // MobileBERT embedding dimensions
        const val EMBEDDING_DIMENSIONS = 512
    }
}

/**
 * Exception thrown when embedding operations fail.
 */
class EmbeddingException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
