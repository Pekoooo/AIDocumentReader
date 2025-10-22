package com.example.aidocumentreader

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for the Document Reader app.
 *
 * KEY CONCEPT: @HiltAndroidApp
 * - This annotation triggers Hilt's code generation
 * - Creates a Dagger component attached to the Application lifecycle
 * - This is the "root" of the dependency injection graph
 * - MUST be added to your Application class for Hilt to work
 */
@HiltAndroidApp
class DocumentReaderApplication : Application()
