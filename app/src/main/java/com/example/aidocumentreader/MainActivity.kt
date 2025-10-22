package com.example.aidocumentreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.aidocumentreader.presentation.ocr.OcrScreen
import com.example.aidocumentreader.ui.theme.AIDocumentReaderTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main Activity - Entry point of the app.
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
         * - OcrScreen is our main screen composable
         */
        setContent {
            AIDocumentReaderTheme {
                OcrScreen()
            }
        }
    }
}