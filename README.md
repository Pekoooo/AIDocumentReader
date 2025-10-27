# AI Document Reader

An on-device RAG (Retrieval-Augmented Generation) Android application that lets you upload PDF documents and ask questions using natural language - completely offline, no cloud required.

## Overview

AI Document Reader implements a complete RAG pipeline on Android devices, combining semantic search with local LLM inference to provide accurate, context-aware answers from your documents.

```
┌─────────────────────────────────────────────────────────────────┐
│                    AI DOCUMENT READER                           │
│                   RAG Pipeline Architecture                      │
└─────────────────────────────────────────────────────────────────┘

┌───────────┐      ┌──────────────┐      ┌─────────────┐
│    PDF    │──1──>│ Text Extract │──2──>│   Chunking  │
│ Documents │      │   (PDFBox)   │      │  (~500 chr) │
└───────────┘      └──────────────┘      └──────┬──────┘
                                                 │
                                                 v
                                          ┌──────────────┐
                                       3  │   MobileBERT │
                                    ┌────>│  Embeddings  │
                                    │     │  (512-dim)   │
                                    │     └──────┬───────┘
                                    │            │
                                    │            v
                                    │     ┌──────────────┐
                                    │  4  │  ObjectBox   │
                                    │────>│ Vector Store │
                                    │     │ (HNSW Index) │
                                    │     └──────────────┘
                                    │
                                    │     ┌─────────────┐
┌──────────┐                        │  5  │    Query    │
│   User   │─────────────────────────────>│   Embedding │
│ Question │                              └──────┬──────┘
└──────────┘                                     │
                                                 v
      ┌──────────────────────────────────────────┘
      │                           6
      │                    ┌──────────────┐
      │                    │ HNSW Search  │
      │                    │    (~8ms)    │
      │                    └──────┬───────┘
      │                           │
      │                           v
      │                    ┌──────────────┐
      │                    │  Top K=3     │
      │                 7  │   Chunks     │
      └───────────────────>│   + Query    │
                           └──────┬───────┘
                                  │
                                  v
                           ┌──────────────┐
                        8  │   Gemma 3    │
                           │   1B LLM     │
                           └──────┬───────┘
                                  │
                                  v
                           ┌──────────────┐
                           │   Answer     │
                           │  + Sources   │
                           └──────────────┘
```

## System Architecture

### Document Processing Pipeline

```
Upload PDF Document
        │
        ├─> Extract Text (PDFBox)
        │   └─> ~4s for 20-page PDF
        │
        ├─> Chunk Text
        │   ├─> Target: 500 characters
        │   ├─> Sentence-aware splitting
        │   └─> 75-char overlap for context
        │
        ├─> Generate Embeddings (MobileBERT)
        │   ├─> 512-dimensional vectors
        │   ├─> ~20ms per chunk
        │   └─> L2 normalized
        │
        └─> Store in ObjectBox
            ├─> HNSW vector index
            ├─> Cosine similarity ready
            └─> ~O(log n) search time

Total Time: ~4s for 20-page PDF
```

### Query Answering Pipeline

```
User Question
     │
     ├─> Embed Question (MobileBERT)
     │   └─> ~20ms
     │
     ├─> HNSW Vector Search (ObjectBox)
     │   ├─> Search space: All document chunks
     │   ├─> Algorithm: Hierarchical NSW
     │   ├─> Returns: Top K=3 chunks
     │   └─> ~8ms
     │
     ├─> Build Context Prompt
     │   ├─> Concatenate retrieved chunks
     │   ├─> Add user question
     │   └─> Token budget: 824 tokens
     │
     └─> Generate Answer (Gemma 3 1B)
         ├─> Session-based inference
         ├─> Temperature: 0.3 (factual)
         ├─> Max output: 200 tokens
         └─> ~5-8s generation time

Total Time: ~8s end-to-end
```

## Features

- **100% On-Device Processing**: No internet required, complete privacy
- **PDF Document Upload**: Extract and process PDF documents
- **Semantic Search**: Find relevant information by meaning, not just keywords
- **Natural Language Q&A**: Ask questions in plain English
- **Source Citations**: See which document pages answers came from
- **Fast Vector Search**: Sub-10ms retrieval using HNSW indexing
- **Efficient Chunking**: Sentence-aware text splitting with overlap
- **Context-Aware Answers**: LLM generates responses from retrieved chunks

## Tech Stack

### Core AI/ML Libraries

| Library | Version | Purpose |
|---------|---------|---------|
| **MediaPipe Tasks GenAI** | 0.10.29 | On-device LLM (Gemma 3 1B) |
| **MediaPipe Tasks Text** | 0.10.29 | Text embeddings (MobileBERT) |
| **ObjectBox** | 4.0.3 | Vector database with HNSW |
| **ML Kit Text Recognition** | 16.0.1 | OCR for images |
| **PDFBox Android** | 2.0.27.0 | PDF text extraction |

### Android Stack

- **Kotlin** - Primary language
- **Jetpack Compose** - Modern UI toolkit
- **Hilt** (2.51.1) - Dependency injection
- **Coroutines** (1.9.0) - Async operations
- **WorkManager** (2.9.0) - Background processing
- **Coil** (2.7.0) - Image loading

## Performance Metrics

```
┌──────────────────────────────────────────────────────┐
│                  Performance Profile                 │
├──────────────────────────────────────────────────────┤
│ Document Upload (20 pages)               ~4s         │
│  ├─ Text Extraction                      ~1s         │
│  ├─ Chunking                             ~50ms       │
│  └─ Embedding Generation                 ~2.6s       │
│                                                      │
│ Query Answering                          ~8s         │
│  ├─ Question Embedding                   ~20ms       │
│  ├─ HNSW Vector Search                   ~8ms        │
│  └─ LLM Generation                       ~5-8s       │
│                                                      │
│ Memory Usage                                         │
│  ├─ MobileBERT Model                     ~25 MB      │
│  ├─ Gemma 3 1B Model (8-bit)            ~800 MB      │
│  └─ ObjectBox Database                   ~2-5 MB     │
│                                                      │
│ Vector Search Scaling (HNSW)                         │
│  ├─ 1,000 chunks                         ~4ms        │
│  ├─ 10,000 chunks                        ~8ms        │
│  └─ 100,000 chunks                       ~15ms       │
└──────────────────────────────────────────────────────┘
```

## Requirements

- **Android SDK**: API 24+ (Android 7.0 Nougat)
- **Target SDK**: API 36
- **RAM**: Minimum 2GB free (for LLM model)
- **Storage**: ~1GB for models
- **CPU**: ARMv8 or x86_64

## Setup Instructions

### 1. Clone Repository

```bash
git clone https://github.com/yourusername/AIDocumentReader.git
cd AIDocumentReader
```

### 2. Download Models

#### MobileBERT Embedding Model

Download the embedding model and place it in the correct location:

```bash
# Download mobile_bert.tflite
curl -o app/src/main/assets/models/mobile_bert.tflite \
  https://storage.googleapis.com/mediapipe-models/text_embedder/bert_embedder/float32/1/bert_embedder.tflite
```

Or manually:
1. Download from: https://storage.googleapis.com/mediapipe-models/text_embedder/bert_embedder/float32/1/bert_embedder.tflite
2. Create directory: `app/src/main/assets/models/`
3. Place file as: `app/src/main/assets/models/mobile_bert.tflite`

#### Gemma 3 1B LLM Model

Download the Gemma 3 1B model:

```bash
# Download Gemma 3 1B (8-bit quantized)
# Place in: app/src/main/assets/Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task
```

Get the model from [Google AI Edge](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference)

### 3. Build Project

```bash
./gradlew assembleDebug
```

### 4. Install on Device

```bash
./gradlew installDebug
```

## Usage

### 1. Upload Document

1. Open the app
2. Tap "Upload PDF"
3. Select a PDF document
4. Wait for processing (~4s for 20 pages)

### 2. Ask Questions

1. Type your question in natural language
2. Tap "Ask"
3. View answer with source citations
4. Check which pages the answer came from

### Example Questions

```
"What is the main topic of this document?"
"Who are the authors mentioned?"
"What are the key findings?"
"Summarize the conclusion section"
"What methodology was used in the study?"
```

## Architecture Details

### RAG Pipeline Components

#### 1. Text Extraction (`PdfTextExtractor.kt`)
- Uses PDFBox Android library
- Extracts text, page count, word count
- Handles multi-page documents

#### 2. Text Chunking (`RagRepository.kt`)
- Chunk size: 500 characters
- Overlap: 75 characters
- Sentence-aware splitting
- Minimum chunk: 100 characters

#### 3. Embedding Generation (`EmbeddingService.kt`)
- Model: MobileBERT (512 dimensions)
- Processing: ~20ms per chunk
- Output: L2-normalized float vectors
- Device: CPU

#### 4. Vector Storage (`ObjectBox`)
- Database: ObjectBox with HNSW index
- Similarity: Cosine distance
- Search time: O(log n)
- Index type: Hierarchical Navigable Small World

#### 5. LLM Inference (`LlmService.kt`)
- Model: Gemma 3 1B (8-bit quantized)
- Session-based architecture
- Token budget: 1024 total (824 input, 200 output)
- Temperature: 0.3 (factual answers)
- Generation: ~5-8s on CPU

### Project Structure

```
app/src/main/java/com/example/aidocumentreader/
├── data/
│   ├── db/                    # ObjectBox entities
│   │   ├── Document.kt        # Document metadata
│   │   └── DocumentChunk.kt   # Chunk with embedding
│   ├── repository/
│   │   ├── RagRepository.kt   # RAG pipeline orchestration
│   │   └── DocumentRepositoryImpl.kt
│   └── service/
│       ├── EmbeddingService.kt    # MobileBERT embeddings
│       ├── LlmService.kt          # Gemma 3 LLM
│       └── PdfTextExtractor.kt    # PDF processing
├── ui/
│   ├── screen/               # Compose UI screens
│   └── viewmodel/            # ViewModels
└── presentation/             # UI components

app/src/main/assets/
├── models/
│   └── mobile_bert.tflite   # Embedding model
└── Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task  # LLM model
```

## Key Algorithms

### Cosine Similarity

```
Measures angle between vectors

Formula: similarity = (A · B) / (||A|| × ||B||)

Range: -1 (opposite) to 1 (identical)
Typical relevant scores: 0.6 - 0.9

Why cosine?
✓ Normalized (handles vector magnitude)
✓ Fast computation (dot product)
✓ Standard for semantic similarity

## References

- [MediaPipe LLM Inference](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference)
- [ObjectBox Vector Search](https://docs.objectbox.io/ann-vector-search)
- [RAG Architecture](https://arxiv.org/abs/2005.11401)
- [HNSW Algorithm](https://arxiv.org/abs/1603.09320)

---
