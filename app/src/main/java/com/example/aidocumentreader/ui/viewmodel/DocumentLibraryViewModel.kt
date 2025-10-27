package com.example.aidocumentreader.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aidocumentreader.data.db.Document
import com.example.aidocumentreader.data.repository.RagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for managing the document library.
 *
 * KEY RESPONSIBILITIES:
 * - Handle PDF document uploads
 * - Track processing progress (extraction → chunking → embedding → storage)
 * - Display list of uploaded documents
 * - Handle document deletion
 * - Navigate to RAG chat for specific documents
 *
 * STATE MANAGEMENT:
 * - Uses StateFlow for reactive UI updates
 * - viewModelScope ensures coroutines tied to ViewModel lifecycle
 * - All operations are suspending functions running on background threads
 */
@HiltViewModel
class DocumentLibraryViewModel @Inject constructor(
    private val ragRepository: RagRepository
) : ViewModel() {

    /**
     * UI State for the document library screen.
     *
     * STATES:
     * - Loading: Fetching documents from database
     * - Success: Documents loaded, ready to display
     * - Error: Failed to load documents
     * - Uploading: Processing a new document (shows progress)
     */
    sealed class UiState {
        data object Loading : UiState()
        data class Success(val documents: List<Document>) : UiState()
        data class Error(val message: String) : UiState()
        data class Uploading(
            val fileName: String,
            val progress: Float,
            val statusMessage: String
        ) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "DocumentLibraryViewModel initialized")
        loadDocuments()
    }

    /**
     * Load all documents from the database.
     *
     * FLOW:
     * 1. Set state to Loading
     * 2. Fetch documents from RagRepository
     * 3. Update state to Success with documents list
     * 4. On error, update state to Error
     */
    fun loadDocuments() {
        viewModelScope.launch {
            Log.d(TAG, "Loading documents from database...")
            _uiState.value = UiState.Loading
            ragRepository.getAllDocuments()
                .onSuccess { documents ->
                    Log.d(TAG, "Loaded ${documents.size} documents")
                    _uiState.value = UiState.Success(documents)
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to load documents", error)
                    _uiState.value = UiState.Error(error.message ?: "Failed to load documents")
                }
        }
    }

    /**
     * Upload and process a PDF document.
     *
     * PROCESSING PIPELINE:
     * 1. Copy file from URI to app storage
     * 2. Extract text from PDF (~500ms per 10 pages)
     * 3. Chunk text into ~500 char pieces with overlap
     * 4. Generate embeddings for each chunk (~20ms each)
     * 5. Store chunks in ObjectBox with HNSW index
     *
     * PROGRESS TRACKING:
     * - "Extracting text..." (20% progress)
     * - "Chunking document..." (40% progress)
     * - "Generating embeddings... (chunk X/Y)" (40-90% progress)
     * - "Storing in database..." (95% progress)
     *
     * @param uri Content URI from file picker
     * @param fileName Display name of the file
     */
    fun uploadDocument(uri: Uri, fileName: String, file: File) {
        viewModelScope.launch {
            Log.d(TAG, "Starting document upload: $fileName")
            _uiState.value = UiState.Uploading(fileName, 0f, "Starting upload...")

            ragRepository.uploadAndProcessDocument(
                file = file,
                onProgress = { progress, statusMessage ->
                    Log.d(TAG, "Upload progress: ${(progress * 100).toInt()}% - $statusMessage")
                    _uiState.value = UiState.Uploading(fileName, progress, statusMessage)
                }
            ).onSuccess { documentId ->
                Log.d(TAG, "Document uploaded successfully: ID=$documentId")
                // Reload documents to show the new one
                loadDocuments()
            }.onFailure { error ->
                Log.e(TAG, "Failed to upload document: $fileName", error)
                _uiState.value = UiState.Error(
                    "Failed to process $fileName: ${error.message}"
                )
                // After showing error for a moment, reload the list
                kotlinx.coroutines.delay(3000)
                loadDocuments()
            }
        }
    }

    /**
     * Delete a document and all its chunks from the database.
     *
     * CLEANUP:
     * - Deletes all DocumentChunks associated with the document
     * - Deletes the Document entity
     * - Deletes the actual PDF file from storage
     * - ObjectBox automatically updates HNSW index
     *
     * @param documentId The ID of the document to delete
     */
    fun deleteDocument(documentId: Long) {
        viewModelScope.launch {
            Log.d(TAG, "Deleting document ID=$documentId")
            ragRepository.deleteDocument(documentId)
                .onSuccess {
                    Log.d(TAG, "Document deleted successfully")
                    loadDocuments()
                }
                .onFailure { error ->
                    Log.e(TAG, "Failed to delete document", error)
                    _uiState.value = UiState.Error(
                        "Failed to delete document: ${error.message}"
                    )
                    kotlinx.coroutines.delay(3000)
                    loadDocuments()
                }
        }
    }

    companion object {
        private const val TAG = "RAG"
    }

    /**
     * Get statistics about a specific document.
     *
     * METRICS:
     * - Total chunks
     * - Word count
     * - Page count
     * - Processing time
     * - Upload date
     *
     * @param documentId The ID of the document
     */
    fun getDocumentStats(documentId: Long) {
        viewModelScope.launch {
            ragRepository.getDocumentById(documentId)
                .onSuccess { document ->
                    // Could emit this to a separate StateFlow if needed
                    // For now, document stats are shown in the Success state
                }
        }
    }
}
