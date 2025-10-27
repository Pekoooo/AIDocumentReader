package com.example.aidocumentreader.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.aidocumentreader.ui.viewmodel.RagChatViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * RAG Chat Screen - Ask questions about uploaded documents.
 *
 * FEATURES:
 * - Chat interface with user questions and AI answers
 * - Source citations showing which documents were used
 * - Typing indicator during processing
 * - Automatic scroll to latest message
 * - Error handling for various failure cases
 *
 * RAG PIPELINE (triggered on each question):
 * 1. Embed question (20ms)
 * 2. Vector search across all chunks (8ms)
 * 3. Retrieve top-3 most relevant chunks
 * 4. Generate answer with LLM (2-5 seconds)
 * 5. Display answer with sources
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagChatScreen(
    viewModel: RagChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    var questionText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Initialize services when screen is first loaded
    LaunchedEffect(Unit) {
        viewModel.initializeServices()
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Document Chat") },
                actions = {
                    if (messages.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearChat() }) {
                            Text("Clear")
                        }
                    }
                }
            )
        },
        bottomBar = {
            QuestionInputBar(
                questionText = questionText,
                onQuestionChange = { questionText = it },
                onSendClick = {
                    if (questionText.isNotBlank()) {
                        viewModel.askQuestion(questionText)
                        questionText = ""
                    }
                },
                enabled = uiState !is RagChatViewModel.UiState.Processing
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is RagChatViewModel.UiState.Processing -> {
                    if (messages.isEmpty()) {
                        // Initial loading
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Initializing AI models...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        // Show chat with typing indicator
                        ChatMessageList(
                            messages = messages,
                            isTyping = isTyping,
                            listState = listState
                        )
                    }
                }

                is RagChatViewModel.UiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.initializeServices() }) {
                            Text("Retry")
                        }
                    }
                }

                else -> {
                    // Idle state - show chat
                    if (messages.isEmpty()) {
                        EmptyChatMessage()
                    } else {
                        ChatMessageList(
                            messages = messages,
                            isTyping = isTyping,
                            listState = listState
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyChatMessage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome!",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Ask me anything about your uploaded documents.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Example questions:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "• What are the main topics in the documents?\n• Summarize the key findings.\n• What does the document say about X?",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ChatMessageList(
    messages: List<RagChatViewModel.ChatMessage>,
    isTyping: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            ChatMessageBubble(message = message)
        }

        if (isTyping) {
            item {
                TypingIndicator()
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: RagChatViewModel.ChatMessage
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (message.isUser) {
                MaterialTheme.colorScheme.primary
            } else if (message.isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else if (message.isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )

                // Show sources for assistant messages
                if (!message.isUser && !message.isError && !message.sources.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sources:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    message.sources.forEach { source ->
                        SourceCitation(source = source)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        // Timestamp
        Spacer(modifier = Modifier.height(4.dp))
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        Text(
            text = timeFormat.format(Date(message.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SourceCitation(
    source: RagChatViewModel.SourceInfo
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${source.documentTitle} - Page ${source.pageNumber}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${source.relevance}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = source.chunkText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }

        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.padding(0.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = if (expanded) "Show less" else "Show more",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(8.dp)
            ) {}
        }
    }
}

@Composable
fun QuestionInputBar(
    questionText: String,
    onQuestionChange: (String) -> Unit,
    onSendClick: () -> Unit,
    enabled: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = questionText,
                onValueChange = onQuestionChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask a question...") },
                enabled = enabled,
                maxLines = 3
            )

            IconButton(
                onClick = onSendClick,
                enabled = enabled && questionText.isNotBlank()
            ) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = "Send",
                    tint = if (enabled && questionText.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
