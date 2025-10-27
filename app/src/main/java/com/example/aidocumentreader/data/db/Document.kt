package com.example.aidocumentreader.data.db

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

/**
 * Document entity - Represents an uploaded PDF document in the RAG system.
 *
 * OBJECTBOX CONCEPT:
 * - @Entity marks this as a database table
 * - @Id creates auto-incrementing primary key
 * - ObjectBox generates code at compile time for CRUD operations
 *
 * RAG PIPELINE ROLE:
 * - Stores metadata about uploaded PDFs
 * - One document → many chunks (one-to-many relationship)
 * - Tracks processing status for async operations
 */
@Entity
data class Document(
    @Id
    var id: Long = 0,

    // Document identification
    var title: String,                      // Display name (e.g., "Financial Report Q3")
    var filePath: String,                   // Path to original PDF file
    var fileType: String = "PDF",           // For future: PDF, IMAGE, etc.

    // Content metadata
    var fullText: String,                   // Complete extracted text
    var pageCount: Int,                     // Number of pages in PDF
    var wordCount: Int = 0,                 // Approximate word count

    // Processing status
    var totalChunks: Int = 0,               // How many chunks created
    var isProcessed: Boolean = false,       // Has chunking + embedding completed?
    var processingError: String? = null,    // Error message if processing failed

    // Timestamps
    var uploadedAt: Long,                   // When user uploaded
    var processedAt: Long? = null,          // When processing completed

    // Optional metadata
    var tags: String? = null,               // Comma-separated tags for categorization
    var notes: String? = null               // User notes about the document
)
