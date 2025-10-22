package com.example.aidocumentreader.presentation.ocr

/**
 * Represents a single message in the chat conversation.
 *
 * KEY CONCEPT: Data Class for Chat Messages
 * - Immutable representation of a chat message
 * - Used in a List to build conversation history
 * - Timestamp helps with ordering and display
 *
 * DESIGN CHOICE: Why separate from UI state?
 * - ChatMessage is a domain model (represents data)
 * - Can be reused across different screens
 * - Easy to serialize/save to database later
 * - Clear separation: data vs presentation
 */
data class ChatMessage(
    /**
     * The message content/text
     */
    val text: String,

    /**
     * Whether this message is from the user (true) or AI (false)
     * Used to style messages differently in UI
     */
    val isFromUser: Boolean,

    /**
     * Timestamp when message was created
     * Could be used for:
     * - Sorting messages
     * - Displaying "sent at" time
     * - Unique ID generation
     */
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Convenience property for readability in code
     */
    val isFromAi: Boolean
        get() = !isFromUser
}
