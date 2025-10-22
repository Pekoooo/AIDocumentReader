package com.example.aidocumentreader.di

import android.content.Context
import com.example.aidocumentreader.data.repository.DocumentRepository
import com.example.aidocumentreader.data.repository.DocumentRepositoryImpl
import com.example.aidocumentreader.data.service.LlmService
import com.example.aidocumentreader.data.service.OcrService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt Module for providing application-wide dependencies.
 *
 * KEY CONCEPTS:
 * - @Module: Tells Hilt this class provides dependencies
 * - @InstallIn: Defines the lifecycle/scope of these dependencies
 * - SingletonComponent: Lives for the entire application lifetime
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provides a singleton instance of OcrService.
     *
     * KEY CONCEPTS:
     * - @Provides: Tells Hilt how to create an instance
     * - @Singleton: Only one instance exists for the app lifetime
     * - Hilt will call this function when OcrService is needed
     *
     * WHY USE @Provides instead of @Inject constructor?
     * - For classes you don't own (third-party libraries)
     * - When construction requires complex logic
     * - When you need to provide interfaces (we'll use this for Repository soon!)
     */
    @Provides
    @Singleton
    fun provideOcrService(): OcrService {
        return OcrService()
    }

    /**
     * Provides a singleton instance of LlmService.
     *
     * KEY CONCEPT: @ApplicationContext
     * - Hilt provides the Application Context
     * - Safe to hold in singletons (lives entire app lifetime)
     * - Used by LlmService to access assets and cache
     *
     * WHY SINGLETON?
     * - LLM model is heavy (~1.3GB in memory)
     * - Loading takes 2-5 seconds
     * - Want to initialize once and reuse
     * - Single instance ensures model loaded only once
     */
    @Provides
    @Singleton
    fun provideLlmService(
        @ApplicationContext context: Context
    ): LlmService {
        return LlmService(context)
    }
}

/**
 * Separate module for binding interfaces to implementations.
 *
 * KEY CONCEPT: @Binds vs @Provides
 * - @Binds: More efficient for simple interface → implementation binding
 * - @Provides: Used when you need custom creation logic
 * - @Binds can only be in abstract classes/interfaces
 *
 * WHY SEPARATE MODULE?
 * - @Binds requires abstract class, @Provides requires object/class
 * - Can't mix both in same module (Dagger limitation)
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * Binds DocumentRepositoryImpl to DocumentRepository interface.
     *
     * WHAT THIS DOES:
     * - Tells Hilt: "When someone asks for DocumentRepository, give them DocumentRepositoryImpl"
     * - @Singleton ensures one instance for the app lifetime
     * - Hilt will automatically inject OcrService into DocumentRepositoryImpl constructor
     *
     * DEPENDENCY CHAIN:
     * 1. ViewModel requests DocumentRepository
     * 2. Hilt sees this @Binds and creates DocumentRepositoryImpl
     * 3. DocumentRepositoryImpl needs OcrService (from @Inject constructor)
     * 4. Hilt provides OcrService from AppModule.provideOcrService()
     */
    @Binds
    @Singleton
    abstract fun bindDocumentRepository(
        documentRepositoryImpl: DocumentRepositoryImpl
    ): DocumentRepository
}
