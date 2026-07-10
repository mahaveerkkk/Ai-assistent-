# LiteRT and LiteRT-LM Android (Kotlin) Research Summary

## 1. What LiteRT-LM is & Differences from TensorFlow Lite / Google AI Edge

*   **Google AI Edge**: This is Google's comprehensive ecosystem and suite of tools, runtimes, and libraries (including LiteRT, AI Edge Torch, and MediaPipe) for deploying machine learning models on edge devices.
*   **TensorFlow Lite (TFLite)**: In September 2024, Google rebranded TensorFlow Lite to **LiteRT** (Lite Runtime) to reflect its growth into a framework-neutral execution engine that runs models exported from PyTorch (via AI Edge Torch), JAX, Keras, and TensorFlow.
*   **LiteRT**: The core, low-level on-device inference runtime under Google AI Edge. It handles raw model execution, graph execution, and hardware acceleration via CPU, GPU, or NPU delegates.
*   **LiteRT-LM**: A specialized, production-ready orchestration layer built *on top* of the core LiteRT runtime, designed specifically for running Large Language Models (LLMs) on-device (e.g., Gemma). While LiteRT processes raw mathematical tensor operations, LiteRT-LM manages high-level NLP/conversational tasks:
    *   **Text Tokenization**: Bundles and manages tokenizers (SentencePiece/BPE).
    *   **Chat Templates**: Formats prompts correctly for specific LLMs.
    *   **Conversation State**: Manages multi-turn conversation history and system instructions.
    *   **Token Generation**: Handles token-by-token generation logic.

---

## 2. Initialization & Configuration in Android (Kotlin)

### Dependency Integration
Add the Android-specific artifact from the Google Maven Repository to your `build.gradle.kts` file:
```kotlin
dependencies {
    // Android LiteRT-LM dependency
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.13.1") // Or latest.release
}
```

### Standard Initialization and Chat Pattern
Model loading and backend configuration are heavy operations that can block threads for up to 10 seconds. You must execute initialization asynchronously on a background dispatcher (e.g., `Dispatchers.IO`).

```kotlin
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

suspend fun initializeAndChat(modelPath: String, cachePath: String) = withContext(Dispatchers.IO) {
    // 1. Configure the engine
    val engineConfig = EngineConfig(
        modelPath = modelPath,
        backend = Backend.GPU(), // GPU acceleration (or Backend.CPU() / Backend.NPU())
        cacheDir = cachePath      // Directory to cache compiled kernels for faster subsequent startups
    )
    
    // 2. Instantiate the engine and manage native lifecycle
    Engine(engineConfig).use { engine ->
        // 3. Initialize the model (blocks the calling thread; must run on background thread)
        engine.initialize()
        
        // 4. Configure conversation session settings
        val conversationConfig = ConversationConfig(
            systemInstruction = Contents("You are a helpful assistant."),
            samplerConfig = SamplerConfig(
                temperature = 0.7f,
                topK = 40,
                topP = 0.95f
            )
        )
        
        // 5. Open conversation session and stream tokens
        engine.createConversation(conversationConfig).use { conversation ->
            conversation.sendMessageAsync("Explain quantum computing in one sentence.")
                .collect { token ->
                    // Stream token responses to UI thread
                    withContext(Dispatchers.Main) {
                        print(token)
                    }
                }
        }
    }
}
```

### Core Configuration Classes

#### `EngineConfig` Properties
*   `modelPath` (`String`): Absolute path to the `.litertlm` model file.
*   `backend` (`Backend`): Hardware target for execution (e.g., `Backend.CPU()`, `Backend.GPU()`, `Backend.NPU()`).
*   `cacheDir` (`String?`): Optional path to a writable application cache directory (e.g., `context.cacheDir.absolutePath`) to speed up subsequent load times via pre-compiled kernels.
*   `visionBackend` / `audioBackend` (`Backend?`): Tailored backend settings for multimodal execution.
*   `maxNumTokens` / `maxNumImages` (`Int?`): Bounds configuration for the context length.

#### `ConversationConfig` Properties
*   `systemInstruction` (`Contents?`): Initial system prompt defining model behavior/persona.
*   `samplerConfig` (`SamplerConfig?`): Customizes response creativity/determinism (`temperature`, `topK`, `topP`).
*   `tools` (`List<ToolProvider>`): Custom Kotlin functions registered as tools for function calling.
*   `automaticToolCalling` (`Boolean`): Automated tool invocation flag.
*   `initialMessages` (`List<Message>`): Conversational seeding history.

---

## 3. Threading Requirements and Best Practices

1.  **Background Thread Initialization**: The `Engine.initialize()` method is computationally expensive (often taking 5–10+ seconds for 1B–2B parameter models). Never run initialization or model loading on the main thread to avoid Application Not Responding (ANR) warnings. Always use coroutines (`Dispatchers.IO`) or a background executor.
2.  **Thread Safety Restrictions**: The `Engine` and `Conversation` classes wrap native C++ resources that are **not thread-safe**. Calling methods (such as running inference or altering configuration) concurrently from multiple threads on the same instance will lead to state corruption or native crashes. Access must be serialized (e.g., using a single-threaded coroutine dispatcher or serializing calls through a mutex lock).
3.  **Resource Cleanup**: Native JNI memory handles are allocated when creating the engine. Both `Engine` and `Conversation` implement `AutoCloseable`. Always wrap them in Kotlin's `.use { ... }` block or call `.close()` explicitly in teardown methods (e.g., `onDestroy()` / ViewModel `onCleared()`) to prevent native memory leaks or subsequent resource locks.
4.  **Asynchronous Flow Generation**: Use `sendMessageAsync()` to obtain a Kotlin `Flow<String>` representing the generated text tokens. Collecting this flow allows you to update the UI progressively and smoothly.

---

## 4. Kotlin Version & Metadata Compatibility Issues

*   **The Issue**: The `litertlm-android` artifact is built using newer versions of the Kotlin compiler (e.g., Kotlin 2.x). If your Android project uses an older version of Kotlin (such as 1.9.x), the compiler may fail during build with Kotlin metadata version mismatch errors (specifically regarding metadata serialization format).
*   **Workarounds & Solutions**:
    1.  **Bypass Version Check**: Force the Gradle build to bypass the metadata check by adding the compiler argument inside your project's `build.gradle` or `build.gradle.kts`:
        ```kotlin
        kotlinOptions {
            freeCompilerArgs += "-Xskip-metadata-version-check"
        }
        ```
    2.  **Upgrade Kotlin**: Upgrade the project's Kotlin plugin and compiler version to match or exceed the library's metadata requirements (ideally upgrading to Kotlin 2.0+).
    3.  **Local AAR Import**: If resolution conflicts persist due to transitive dependencies (such as conflicts with `kotlin-reflect`), download the `.aar` manually from the Google Maven Repository and import it directly as a local classpath reference.
