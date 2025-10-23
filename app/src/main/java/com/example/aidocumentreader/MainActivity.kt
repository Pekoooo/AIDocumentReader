package com.example.aidocumentreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.aidocumentreader.ui.screen.MainScreen
import com.example.aidocumentreader.ui.theme.AIDocumentReaderTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main Activity - Entry point of the RAG-powered document reader app.
 *
 * KEY CONCEPT: @AndroidEntryPoint
 * - Marks an Android component (Activity, Fragment, Service, etc.) for dependency injection
 * - Hilt will generate code to inject dependencies into this Activity
 * - Enables ViewModels to receive injected dependencies via @HiltViewModel
 *
 * KEY CONCEPT: setContent { }
 * - Sets the Compose UI hierarchy
 * - Everything inside is composable UI code
 * - Replaces traditional XML layouts
 *
 * APP ARCHITECTURE:
 * - MainScreen contains bottom navigation with 2 tabs: Library and Chat
 * - Library: Upload PDFs, view processing progress, manage documents
 * - Chat: Ask questions about any uploaded document using RAG pipeline
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        /**
         * KEY CONCEPT: Compose UI Setup
         * - setContent { } defines the entire UI in code
         * - AIDocumentReaderTheme applies Material Design theming
         * - MainScreen is our root composable with navigation
         */
        setContent {
            AIDocumentReaderTheme {
                MainScreen()
            }
        }
    }
}