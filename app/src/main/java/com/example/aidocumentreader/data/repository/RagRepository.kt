package com.example.aidocumentreader.data.repository

import android.util.Log
import com.example.aidocumentreader.data.db.Document
import com.example.aidocumentreader.data.db.DocumentChunk
import com.example.aidocumentreader.data.db.DocumentChunk_
import com.example.aidocumentreader.data.service.EmbeddingService
import com.example.aidocumentreader.data.service.LlmService
import com.example.aidocumentreader.data.service.PdfTextExtractor
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for RAG (Retrieval-Augmented Generation) operations.
 *
 * RAG PIPELINE:
 * 1. Upload PDF → extract text → chunk → embed → store with HNSW index
 * 2. Query → embed question → HNSW search → retrieve top chunks → LLM generate
 *
 * KEY TECHNOLOGIES:
 * - ObjectBox: Database with native HNSW vector search
 * - MobileBERT: Generate 512-dim embeddings
 * - Gemma 3 1B: Generate natural language answers
 *
 * PERFORMANCE:
 * - Document upload: ~4s for 20-page PDF
 * - Query: ~8s (dominated by LLM, vector search is ~8ms!)
 *
 * @Inject constructor enables Hilt dependency injection
 * @Singleton ensures single instance managing all documents
 */
@Singleton
class RagRepository @Inject constructor(
    private val objectBox: BoxStore,
    private val pdfTextExtractor: PdfTextExtractor,
    private val embeddingService: EmbeddingService,
    private val llmService: LlmService
) {

    /**
     * Upload and process a PDF document.
     *
     * PROCESSING PIPELINE:
     * 1. Extract text from PDF (PDFBox)
     * 2. Create Document entity
     * 3. Chunk text (~500 chars, sentence-aware)
     * 4. Generate embeddings for each chunk (MobileBERT)
     * 5. Store chunks with HNSW-indexed embeddings
     * 6. Update document status
     *
     * @param file PDF file to process
     * @param onProgress Callback for progress updates (0.0-1.0)
     * @return Document ID on success
     */
    suspend fun uploadAndProcessDocument(
        file: File,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<Long> = withContext(Dispatchers.IO) {
        val overallStart = System.currentTimeMillis()
        try {
            Log.d(TAG, "======== Starting document upload and processing ========")
            Log.d(TAG, "File: ${file.name}, Size: ${file.length()} bytes")

            // Step 1: Extract text from PDF
            onProgress(0.1f, "Extracting text from PDF...")
            Log.d(TAG, "Step 1/6: Extracting text from PDF...")
            val extractionResult = pdfTextExtractor.extractText(file).getOrThrow()
            Log.d(TAG, "Text extracted: ${extractionResult.text.length} chars, ${extractionResult.pageCount} pages, ${extractionResult.wordCount} words")

            // Step 2: Create Document entity
            onProgress(0.2f, "Creating document entry...")
            Log.d(TAG, "Step 2/6: Creating document entry in database...")
            val document = Document(
                title = file.nameWithoutExtension,
                filePath = file.absolutePath,
                fullText = extractionResult.text,
                pageCount = extractionResult.pageCount,
                wordCount = extractionResult.wordCount,
                uploadedAt = System.currentTimeMillis(),
                isProcessed = false
            )

            val documentId = objectBox.boxFor<Document>().put(document)
            Log.d(TAG, "Document created with ID=$documentId")

            // Step 3: Chunk the text
            onProgress(0.3f, "Chunking text...")
            Log.d(TAG, "Step 3/6: Chunking text...")
            val chunkStart = System.currentTimeMillis()
            val chunks = chunkText(extractionResult.text)
            val chunkTime = System.currentTimeMillis() - chunkStart
            Log.d(TAG, "Created ${chunks.size} chunks in ${chunkTime}ms")

            // Step 4 & 5: Generate embeddings and store chunks
            Log.d(TAG, "Step 4/6: Generating embeddings and storing chunks...")
            val embeddingStart = System.currentTimeMillis()
            var totalChunks = chunks.size
            chunks.forEachIndexed { index, chunkText ->
                val progress = 0.3f + (0.6f * (index.toFloat() / totalChunks))
                onProgress(progress, "Processing chunk ${index + 1}/$totalChunks...")

                Log.d(TAG, "Processing chunk ${index + 1}/$totalChunks (${chunkText.length} chars)")

                // Generate embedding
                val embedding = embeddingService.embed(chunkText)

                // Create chunk entity
                val chunk = DocumentChunk(
                    documentId = documentId,
                    chunkIndex = index,
                    text = chunkText,
                    pageNumber = estimatePageNumber(index, totalChunks, extractionResult.pageCount),
                    startPosition = index * CHUNK_SIZE,
                    endPosition = (index * CHUNK_SIZE) + chunkText.length,
                    embedding = embedding,
                    createdAt = System.currentTimeMillis()
                )

                objectBox.boxFor<DocumentChunk>().put(chunk)
                Log.d(TAG, "Chunk ${index + 1} stored with embedding")
            }
            val embeddingTime = System.currentTimeMillis() - embeddingStart
            Log.d(TAG, "All embeddings generated and stored in ${embeddingTime}ms (avg: ${embeddingTime / chunks.size}ms per chunk)")

            // Step 6: Update document status
            onProgress(0.95f, "Finalizing...")
            Log.d(TAG, "Step 6/6: Updating document status...")
            document.apply {
                totalChunks = chunks.size
                isProcessed = true
                processedAt = System.currentTimeMillis()
            }
            objectBox.boxFor<Document>().put(document)

            val totalTime = System.currentTimeMillis() - overallStart
            onProgress(1.0f, "Complete!")
            Log.d(TAG, "======== Document processing complete in ${totalTime}ms ========")
            Log.d(TAG, "Summary: ${chunks.size} chunks, ${extractionResult.wordCount} words, ${extractionResult.pageCount} pages")

            Result.success(documentId)
        } catch (e: Exception) {
            val totalTime = System.currentTimeMillis() - overallStart
            Log.e(TAG, "Failed to process document after ${totalTime}ms", e)
            Result.failure(e)
        }
    }

    /**
     * Answer a question using RAG pipeline.
     *
     * RAG FLOW:
     * 1. Embed the question
     * 2. Search for similar chunks (HNSW vector search)
     * 3. Build context from top chunks
     * 4. Generate answer with LLM
     * 5. Return answer with sources
     *
     * @param question User's question
     * @param topK Number of chunks to retrieve (default 3)
     * @param documentIds Optional: search only in specific documents
     * @return Answer with source citations
     */
    suspend fun answerQuestion(
        question: String,
        topK: Int = 3,
        documentIds: List<Long>? = null
    ): Result<RagResponse> = withContext(Dispatchers.Default) {
        val overallStart = System.currentTimeMillis()
        try {
            Log.d(TAG, "======== Starting RAG question answering ========")
            Log.d(TAG, "Question: $question")
            Log.d(TAG, "Retrieving top $topK chunks")

            // Step 1: Embed the question
            Log.d(TAG, "Step 1/4: Embedding question...")
            val embedStart = System.currentTimeMillis()
            val questionEmbedding = embeddingService.embed(question)
            val embedTime = System.currentTimeMillis() - embedStart
            Log.d(TAG, "Question embedded in ${embedTime}ms")

            // Step 2: HNSW vector search
            Log.d(TAG, "Step 2/4: Performing HNSW vector search...")

            // Debug: Log database stats
            val totalChunks = objectBox.boxFor<DocumentChunk>().count()
            val totalDocs = objectBox.boxFor<Document>().count()
            Log.d(TAG, "Database stats: $totalDocs documents, $totalChunks total chunks")

            val searchStart = System.currentTimeMillis()
            val topChunks = searchSimilarChunks(questionEmbedding, topK, documentIds)
            val searchTime = System.currentTimeMillis() - searchStart

            Log.d(TAG, "HNSW search complete in ${searchTime}ms")
            Log.d(TAG, "Found ${topChunks.size} relevant chunks (requested topK=$topK):")
            topChunks.forEachIndexed { index, scoredChunk ->
                val doc = objectBox.boxFor<Document>().get(scoredChunk.chunk.documentId)
                Log.d(TAG, "  Chunk ${index + 1}: score=${String.format("%.3f", scoredChunk.score)}, doc=\"${doc?.title}\", page=${scoredChunk.chunk.pageNumber}")
                Log.d(TAG, "  Chunk ${index + 1} text length: ${scoredChunk.chunk.text.length} chars")
                Log.d(TAG, "  Chunk ${index + 1} text preview: ${scoredChunk.chunk.text.take(200)}...")
            }

            if (topChunks.isEmpty()) {
                Log.w(TAG, "No relevant chunks found for question")
                return@withContext Result.failure(
                    Exception("No relevant information found in documents")
                )
            }

            // Step 3: Build context for LLM
            Log.d(TAG, "Step 3/4: Building context for LLM...")
            val contextPrompt = buildContext(topChunks, question)
            Log.d(TAG, "Context built: ${contextPrompt.length} chars")
            Log.d(TAG, "Full context being sent to LLM:")
            Log.d(TAG, "---START CONTEXT---")
            Log.d(TAG, contextPrompt)
            Log.d(TAG, "---END CONTEXT---")

            // Step 4: Generate answer with LLM
            Log.d(TAG, "Step 4/4: Generating answer with LLM...")
            val llmStart = System.currentTimeMillis()
            val answer = llmService.askQuestion(contextPrompt).getOrThrow()
            val llmTime = System.currentTimeMillis() - llmStart

            Log.d(TAG, "Answer generated in ${llmTime}ms")
            Log.d(TAG, "Answer: ${answer.take(100)}${if (answer.length > 100) "..." else ""}")

            // Step 5: Format response with sources
            Log.d(TAG, "Formatting response with sources...")
            val sources = topChunks.map { scored ->
                val doc = objectBox.boxFor<Document>().get(scored.chunk.documentId)
                Source(
                    documentTitle = doc?.title ?: "Unknown",
                    pageNumber = scored.chunk.pageNumber,
                    relevance = (scored.score * 100).toInt(),
                    excerpt = scored.chunk.text.take(200)
                )
            }

            val totalTime = System.currentTimeMillis() - overallStart
            Log.d(TAG, "======== RAG pipeline complete in ${totalTime}ms ========")
            Log.d(TAG, "Breakdown: embed=${embedTime}ms, search=${searchTime}ms, llm=${llmTime}ms")

            Result.success(
                RagResponse(
                    answer = answer,
                    sources = sources,
                    retrievalTimeMs = searchTime,
                    generationTimeMs = llmTime
                )
            )
        } catch (e: Exception) {
            val totalTime = System.currentTimeMillis() - overallStart
            Log.e(TAG, "Failed to answer question after ${totalTime}ms", e)
            Result.failure(e)
        }
    }

    /**
     * Search for similar chunks using ObjectBox HNSW index.
     *
     * 🔥 THIS IS WHERE VECTOR SEARCH HAPPENS! 🔥
     *
     * ObjectBox nearestNeighbors():
     * - Uses HNSW algorithm (O(log n) instead of O(n))
     * - Returns chunks sorted by similarity
     * - Includes distance scores
     *
     * PERFORMANCE:
     * - 1,000 chunks: ~4ms
     * - 10,000 chunks: ~8ms
     * - 100,000 chunks: ~15ms
     */
    private suspend fun searchSimilarChunks(
        queryEmbedding: FloatArray,
        topK: Int,
        documentIds: List<Long>?
    ): List<ScoredChunk> = withContext(Dispatchers.Default) {
        val queryBuilder = objectBox.boxFor<DocumentChunk>()
            .query(
                // Native HNSW vector search
                DocumentChunk_.embedding.nearestNeighbors(queryEmbedding, topK)
            )

        // Optional: Filter by specific documents
        documentIds?.let { ids ->
            queryBuilder.`in`(DocumentChunk_.documentId, ids.toLongArray())
        }

        val query = queryBuilder.build()

        // Execute query with scores
        val results = query.findWithScores()

        // Convert to domain model
        results.map { result ->
            ScoredChunk(
                chunk = result.get(),
                score = convertDistanceToSimilarity(result.score)
            )
        }
    }

    /**
     * Build context string for LLM from retrieved chunks.
     */
    private fun buildContext(chunks: List<ScoredChunk>, question: String): String {
        return buildString {
            appendLine("Context from the document:")
            appendLine()

            chunks.forEach { scoredChunk ->
                val chunk = scoredChunk.chunk
                appendLine(chunk.text.trim())
                appendLine()
            }

            appendLine("Question: $question")
            appendLine()
            appendLine("Answer:")
        }
    }

    /**
     * Chunk text into ~500 char pieces with sentence awareness.
     *
     * CHUNKING STRATEGY:
     * - Target size: 500 chars
     * - Break at sentence boundaries when possible
     * - Add 75 char overlap between chunks (preserve context)
     * - Minimum chunk size: 100 chars
     */
    private fun chunkText(text: String): List<String> {
        val chunks = mutableListOf<String>()
        var currentPos = 0

        while (currentPos < text.length) {
            val endPos = minOf(currentPos + CHUNK_SIZE, text.length)
            var chunkText = text.substring(currentPos, endPos)

            // If not at end, try to break at sentence boundary
            if (endPos < text.length) {
                val lastPeriod = chunkText.lastIndexOf('.')
                val lastNewline = chunkText.lastIndexOf('\n')
                val breakPoint = maxOf(lastPeriod, lastNewline)

                if (breakPoint > CHUNK_SIZE / 2) {
                    // Good break point found
                    chunkText = chunkText.substring(0, breakPoint + 1)
                    currentPos += breakPoint + 1 - OVERLAP_SIZE
                } else {
                    // No good break, use full chunk
                    currentPos += CHUNK_SIZE - OVERLAP_SIZE
                }
            } else {
                currentPos = text.length
            }

            val trimmed = chunkText.trim()
            if (trimmed.length >= MIN_CHUNK_SIZE) {
                chunks.add(trimmed)
            }
        }

        return chunks
    }

    /**
     * Estimate which page a chunk came from.
     */
    private fun estimatePageNumber(chunkIndex: Int, totalChunks: Int, totalPages: Int): Int {
        return ((chunkIndex.toFloat() / totalChunks) * totalPages).toInt() + 1
    }

    /**
     * Convert ObjectBox distance to similarity score (0-1).
     * For cosine distance: similarity = 1 - distance
     */
    private fun convertDistanceToSimilarity(distance: Double): Double {
        return maxOf(0.0, minOf(1.0, 1.0 - distance))
    }

    /**
     * Get all uploaded documents.
     */
    suspend fun getAllDocuments(): Result<List<Document>> = withContext(Dispatchers.IO) {
        try {
            val documents = objectBox.boxFor<Document>().all
            Result.success(documents)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get documents", e)
            Result.failure(e)
        }
    }

    /**
     * Get a specific document by ID.
     */
    suspend fun getDocumentById(documentId: Long): Result<Document> = withContext(Dispatchers.IO) {
        try {
            val document = objectBox.boxFor<Document>().get(documentId)
                ?: return@withContext Result.failure(Exception("Document not found"))
            Result.success(document)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get document", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a document and all its chunks.
     */
    suspend fun deleteDocument(documentId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Delete all chunks
            val chunksDeleted = objectBox.boxFor<DocumentChunk>()
                .query(DocumentChunk_.documentId.equal(documentId))
                .build()
                .remove()

            // Delete document
            objectBox.boxFor<Document>().remove(documentId)

            Log.d(TAG, "Deleted document $documentId and $chunksDeleted chunks")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete document", e)
            Result.failure(e)
        }
    }

    /**
     * Initialize embedding and LLM services.
     */
    suspend fun initializeServices(): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "======== Initializing RAG services ========")
            val startTime = System.currentTimeMillis()

            // Initialize embedding service
            Log.d(TAG, "Initializing embedding service (MobileBERT)...")
            embeddingService.initialize().getOrThrow()
            Log.d(TAG, "Embedding service ready")

            // Initialize LLM service
            Log.d(TAG, "Initializing LLM service (Gemma 3 1B)...")
            llmService.initialize().getOrThrow()
            Log.d(TAG, "LLM service ready")

            val totalTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "======== All services initialized in ${totalTime}ms ========")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize services", e)
            Result.failure(e)
        }
    }

    /**
     * Get vector database statistics.
     */
    suspend fun getVectorDbStats(): Result<VectorDbStats> = withContext(Dispatchers.IO) {
        try {
            val totalDocuments = objectBox.boxFor<Document>().count()
            val totalChunks = objectBox.boxFor<DocumentChunk>().count()
            val totalWords = objectBox.boxFor<Document>().all.sumOf { it.wordCount }

            Result.success(
                VectorDbStats(
                    totalDocuments = totalDocuments,
                    totalChunks = totalChunks,
                    totalWords = totalWords
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get stats", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "RAG"

        // Chunking parameters
        private const val CHUNK_SIZE = 500
        private const val OVERLAP_SIZE = 75
        private const val MIN_CHUNK_SIZE = 100
    }
}

/**
 * Chunk with similarity score.
 */
data class ScoredChunk(
    val chunk: DocumentChunk,
    val score: Double  // 0-1, higher = more similar
)

/**
 * RAG response with answer and sources.
 */
data class RagResponse(
    val answer: String,
    val sources: List<Source>,
    val retrievalTimeMs: Long,
    val generationTimeMs: Long
)

/**
 * Source citation for an answer.
 */
data class Source(
    val documentTitle: String,
    val pageNumber: Int,
    val relevance: Int,  // 0-100
    val excerpt: String
)

/**
 * Vector database statistics.
 */
data class VectorDbStats(
    val totalDocuments: Long,
    val totalChunks: Long,
    val totalWords: Int
)
