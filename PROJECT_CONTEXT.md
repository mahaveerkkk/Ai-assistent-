# 🤖 MyAI Assistant — Project Context & Knowledge Base
> **Last Updated:** 2026-07-10
> **Purpose:** Ye file saara project context store karti hai taki baar baar poura code read na karna pare.

---

## 📁 COMPLETE FILE MAP (38 Kotlin files)

### Root
```
app/src/main/java/com/myai/assistant/
├── MyAIApp.kt                          — @HiltAndroidApp Application class
├── accessibility/
│   ├── ActionPerformer.kt              — (281 lines) Click, type, scroll, swipe, global actions (back/home/recents)
│   ├── AppAutomator.kt                 — (420 lines) Open apps, WhatsApp/Telegram/Instagram messaging, Maps, Play Store
│   ├── MyAccessibilityService.kt       — (352 lines) Central hub, singleton, 35+ action dispatcher via executeAiCommand()
│   └── ScreenReader.kt                 — (200 lines) Read screen text, UI tree structure, clickable elements
├── ai/
│   ├── AIClient.kt                     — (204 lines) Multi-engine orchestrator: LiteRT → Ollama → Gemini → Offline fallback
│   ├── AgentLoop.kt                    — (260 lines) Multi-step autonomous execution (10 steps max)
│   ├── GeminiClient.kt                 — (153 lines) Google Gemini 2.0 Flash API client
│   ├── LocalInferenceClient.kt         — (114 lines) Google LiteRT on-device AI (Gemma/Qwen models)
│   ├── OllamaClient.kt                 — (148 lines) Local Ollama HTTP server client (LLaMA3)
│   └── models/
│       └── AiModels.kt                 — (280 lines) System prompt (35+ actions), JSON parser, data classes
├── data/
│   ├── db/
│   │   ├── ChatDao.kt                  — Room DAO: getAllMessages(Flow), insert, deleteAll, getRecentMessages
│   │   └── ChatDatabase.kt             — Room DB v1, single table chat_messages
│   ├── model/
│   │   └── ChatMessage.kt              — Entity: id, content, sender, timestamp, messageType, actionType, actionData
│   └── repository/
│       ├── ChatRepository.kt           — Chat message CRUD + loading state pattern
│       └── SettingsRepository.kt       — SharedPreferences: ollama/gemini/litert settings, voice, overlay, boot
├── di/
│   └── AppModule.kt                    — Hilt @Module: ChatDatabase, ChatDao, ChatRepository, SettingsRepository, SystemControlManager
├── features/
│   ├── camera/
│   │   └── CameraManager.kt           — (147 lines) CameraX + ML Kit OCR + image labeling
│   ├── contacts/
│   │   └── ContactsHelper.kt          — (117 lines) Contacts read, find, call, open dialer
│   ├── device/
│   │   ├── AlarmHelper.kt             — (84 lines) Set alarm, timer, show alarms
│   │   ├── CalendarHelper.kt          — (136 lines) Create event, get upcoming, open calendar
│   │   ├── ClipboardHelper.kt         — (67 lines) Copy, get, clear clipboard
│   │   └── DeviceInfoManager.kt       — (177 lines) Battery, storage, RAM, device details
│   ├── files/
│   │   └── FileManager.kt            — (121 lines) List, read, write, search files
│   ├── location/
│   │   └── LocationHelper.kt         — (93 lines) GPS + reverse geocoding (Hindi locale)
│   ├── media/
│   │   └── MediaHelper.kt            — (83 lines) Play music, compose email
│   ├── messages/
│   │   ├── SmsHelper.kt              — (110 lines) Send SMS (multi-part), read recent, get from number
│   │   └── SmsReceiver.kt            — (34 lines) BroadcastReceiver for SMS_RECEIVED
│   ├── system/
│   │   └── SystemControlManager.kt   — (281 lines) WiFi, BT, volume, brightness, flash, DND, data, airplane, hotspot
│   └── voice/
│       ├── GeminiTTSManager.kt       — (521 lines) Gemini Live API WebSocket audio + offline TTS fallback
│       ├── VoiceManager.kt           — (161 lines) STT Hindi speech recognition
│       └── WakeWordDetector.kt       — (306 lines) 9 wake words, continuous listening loop
├── overlay/
│   ├── OverlayManager.kt            — (120 lines) Floating draggable bubble via WindowManager
│   └── OverlayService.kt            — (62 lines) Foreground service for overlay
├── permissions/
│   └── PermissionManager.kt         — (446 lines) 9 runtime groups + 6 special permissions
├── service/
│   ├── AssistantForegroundService.kt — (150 lines) Background service with wake word → voice → TODO: send to AI
│   ├── BootReceiver.kt              — (37 lines) BOOT_COMPLETED handler
│   ├── LocationTrackingService.kt   — (61 lines) ⚠️ STUB — no actual tracking
│   └── MyNotificationListener.kt   — (225 lines) Notification capture + reply (WhatsApp/Telegram)
├── ui/
│   ├── MainActivity.kt             — (40 lines) @AndroidEntryPoint, edge-to-edge, dark theme
│   ├── components/
│   │   ├── MessageBubble.kt        — (274 lines) User/AI/System bubbles with action badges
│   │   ├── TypingIndicator.kt      — (63 lines) Bouncing dots animation
│   │   └── VoiceButton.kt          — (127 lines) Animated mic with pulse ring
│   ├── navigation/
│   │   └── AppNavigation.kt        — (58 lines) Routes: PERMISSION → CHAT → SETTINGS (+ CAMERA defined)
│   ├── screens/
│   │   ├── ChatScreen.kt           — (500 lines) Full chat UI + diagnostics panel
│   │   ├── PermissionScreen.kt     — (733 lines) Animated permission flow with progress
│   │   └── SettingsScreen.kt       — (334 lines) AI engine, voice, overlay settings
│   └── theme/
│       ├── Color.kt                — Custom color palette (gradients, chat colors)
│       ├── Theme.kt                — Material 3 dark/light themes
│       └── Type.kt                 — Typography scale
└── viewmodel/
    └── AssistantViewModel.kt       — (749 lines) THE BRAIN: zero-latency shortcuts, AI pipeline, 20+ action executor, agent loop

```

---

## 🏗️ ARCHITECTURE

```
┌─────────────────────────────────────────────────┐
│                    UI LAYER                       │
│  ChatScreen ← ViewModel → PermissionScreen       │
│  SettingsScreen   OverlayManager                  │
├─────────────────────────────────────────────────┤
│                 VIEWMODEL                         │
│  AssistantViewModel (749 lines)                   │
│  ├── Zero-Latency Shortcut Parser                 │
│  ├── AI Processing Pipeline                       │
│  ├── Action Executor (20+ actions)                │
│  └── Agent Loop Integration                       │
├─────────────────────────────────────────────────┤
│               AI ENGINE LAYER                     │
│  AIClient → LiteRT / Ollama / Gemini (fallback)   │
│  AgentLoop → Multi-step autonomous execution      │
│  AiModels → System prompt + JSON parser           │
├─────────────────────────────────────────────────┤
│              FEATURE LAYER                        │
│  Contacts │ SMS │ SystemControl │ DeviceInfo       │
│  Alarm │ Calendar │ Clipboard │ Camera+ML Kit      │
│  Files │ Location │ Media │ Notifications          │
│  Voice (STT + TTS + WakeWord)                     │
├─────────────────────────────────────────────────┤
│            ACCESSIBILITY LAYER                    │
│  MyAccessibilityService → ActionPerformer          │
│  AppAutomator → ScreenReader                       │
├─────────────────────────────────────────────────┤
│               DATA LAYER                          │
│  Room DB (ChatMessage) │ SharedPreferences         │
│  ChatRepository │ SettingsRepository               │
├─────────────────────────────────────────────────┤
│            INFRASTRUCTURE                         │
│  Hilt DI │ WorkManager │ Foreground Services       │
│  Boot Receiver │ SMS Receiver                      │
└─────────────────────────────────────────────────┘
```

---

## 🔧 TECH STACK

| Category | Technology | Version |
|----------|-----------|---------|
| Language | Kotlin | 2.1.0 |
| UI | Jetpack Compose + Material 3 | BOM 2024.12.01 |
| DI | Hilt | 2.53.1 |
| Database | Room | 2.6.1 |
| Camera | CameraX | 1.4.1 |
| ML | ML Kit (OCR, Object Detection, Image Labeling) | Latest |
| Network | OkHttp 4.12 + Retrofit 2.11 | |
| AI Cloud | Google Generative AI | 0.9.0 |
| AI Local | LiteRT-LM | 0.8.0 |
| Location | Play Services Location | 21.3.0 |
| Async | Coroutines | 1.9.0 |
| Images | Coil | 2.7.0 |
| Permissions | Accompanist | 0.36.0 |
| Settings | DataStore Preferences | 1.1.1 |
| Background | WorkManager | 2.10.0 |
| Build | AGP 8.7.3, compileSdk 35, minSdk 26 |

---

## 📊 FEATURE STATUS

### ✅ 30 FULLY COMPLETE FEATURES
1. Multi-AI Engine (LiteRT + Ollama + Gemini with fallback)
2. Agent Loop (10-step autonomous execution)
3. Screen Reading + UI Tree Parsing
4. UI Action Performer (click, type, scroll, swipe)
5. App Automator (WhatsApp, Telegram, Instagram, Maps, Play Store)
6. Voice Input (STT, Hindi)
7. Voice Output (Gemini Live API TTS, 5 voices + offline)
8. Wake Word Detection (9 wake words)
9. Notification Reading + Reply (WhatsApp/Telegram)
10. Phone Calls + Contacts
11. SMS Send/Read/Receive
12. System Controls (WiFi/BT/Volume/Brightness/Flash/DND/Data/Airplane/Hotspot)
13. Device Info (Battery/RAM/Storage/Device Details)
14. Alarm / Timer
15. Calendar Events (Create/Read/Open)
16. Clipboard (Copy/Get/Clear)
17. Camera + OCR + Image Labeling
18. File Management (Read/Write/Search/List)
19. Location (GPS + Geocoding)
20. Music Playback
21. Email Compose
22. Floating Overlay Bubble
23. Boot Auto-Start
24. Foreground Service
25. Permission Management (15 groups)
26. Chat UI with Compose (messages, voice, diagnostics)
27. Settings Screen (AI engines, voice, overlay)
28. Zero-Latency Shortcuts (10+ common commands bypass AI)
29. Chat History (Room DB)
30. Dark/Light Theme

### ⚠️ 5 INCOMPLETE STUBS
1. LocationTrackingService — empty boilerplate
2. Camera Screen — route defined, screen not built
3. ForegroundService voice → ViewModel — TODO comment
4. WorkManager — dependency added, no workers
5. DataStore — dependency added, using SharedPreferences instead

---

## 📦 ANDROID MANIFEST SUMMARY
- **50+ permissions** (31 normal + 25 dangerous + 3 special)
- **10 hardware features** (all optional)
- **Components:** MainActivity, MyAccessibilityService, MyNotificationListener, AssistantForegroundService, OverlayService, LocationTrackingService, BootReceiver, SmsReceiver
- **compileSdk 35, minSdk 26, targetSdk 35**

---

## 🔑 KEY PATTERNS
- **Hilt DI** — @HiltAndroidApp, @AndroidEntryPoint, @HiltViewModel, @Module @InstallIn
- **MVVM** — ViewModel ← Repository ← Room DAO
- **StateFlow** — UI state management via _uiState MutableStateFlow
- **Singleton Services** — MyAccessibilityService.instance, MyNotificationListener.instance
- **Coroutines** — viewModelScope.launch, suspend functions throughout
- **Fallback Chain** — AI: LiteRT → Ollama → Gemini → Offline
- **Zero-Latency** — Regex-based shortcut parser in ViewModel bypasses AI for common commands

---

## 📝 ADVANCEMENT PHASES

### Phase 1 ✅ (Fix Stubs) — 1-2 days
- Connect ForegroundService voice → ViewModel
- Migrate SharedPreferences → DataStore
- Build Camera Preview Screen
- Implement LocationTrackingService
- Add WorkManager scheduled tasks

### Phase 2 (Intelligence) — 3-5 days
- Conversation Memory (Room table)
- Gemini Vision (multimodal image input)
- Smart Intent Detection (pre-classifier)
- Context Chain (screen + history)
- Error Recovery in Agent Loop

### Phase 3 ✅ (Premium UX) — 3-5 days
- Onboarding Flow
- Rich Message Cards
- Continuous Voice Mode
- Quick Actions Bar
- Home Screen Widget
- Chat Themes
- Haptic Feedback
- Markdown Rendering

### Phase 4 (Power Features) — 1-2 weeks
- Routine Automation
- Notification AI Summary
- Web Search + Browsing
- PDF/Document Reader
- App Usage Analytics
- Smart Reply Suggestions
- Chat Export/Backup
- Multi-language

### Phase 5 (Futuristic) — 2-4 weeks
- RAG Knowledge Base
- Multi-Device Sync
- Smart Home Integration
- Proactive Notifications
- Screen Recording + Analysis
