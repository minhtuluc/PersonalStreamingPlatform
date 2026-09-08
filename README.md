# DriveStream 🎬

> **Native 4K HDR Video Streaming directly from your personal Google Drive to Android.**  
> Zero compression, original resolution, custom buffer optimization, and hardware-backed security.

---

## 🌟 Overview

Google Drive's built-in web and mobile previewers heavily compress and transcode videos down to low-bitrate 720p/1080p. **DriveStream** allows personal Google One / Drive users (e.g. 5TB AI Pro tiers) to stream their original video files (MP4, MKV, MOV up to 4K 60fps) directly with native bitrates, custom chunk buffering, and hardware-accelerated playback.

---

## 🚀 Key Features

- **Direct Native Streaming:** Media3 ExoPlayer integration optimized with custom chunk ranges (2MB) and back-buffers (30s) to prevent Google API rate limits.
- **Strict Minimal OAuth Scope:** Connects solely via `https://www.googleapis.com/auth/drive.readonly`. Zero write permissions, zero public sharing, avoiding any policy violation risks.
- **Hardware-Backed Encryption:** Tokens stored via `EncryptedSharedPreferences` backed by Android Keystore (`AES256_GCM`).
- **Proactive Silent Token Refresh:** Tokens automatically refresh when `< 300s` lifetime remains, eliminating playback interruptions.
- **Concurrent Request Deduplication:** Mutex-based double-checked locking ensures multiple parallel network requests share a single refresh call.
- **Offline & Download Engine (Sprint 4):** Resumable downloads via HTTP Range headers with foreground service support.

---

## 🛠 Tech Stack & Architecture

- **Language & Runtime:** Kotlin 2.0.0, OpenJDK 21 LTS, Target SDK 35 (Android 15), Min SDK 26 (Android 8.0).
- **Architecture:** MVVM + Clean Architecture + Unidirectional Data Flow (UDF).
- **UI:** 100% Jetpack Compose with Material 3 Dark Luxury theme tokens.
- **Dependency Injection:** Dagger Hilt 2.51.1.
- **Local Database:** Room 2.6.1 with Kotlin Coroutines Flow & TypeConverters.
- **Networking:** OkHttp 4.12.0 with custom `TokenInterceptor` (automatic Bearer injection & 401 recovery).
- **Player:** Jetpack Media3 ExoPlayer 1.4.0 with OkHttpDataSource.
- **Quality & Testing:** JUnit 5, MockK, Turbine, MockWebServer, Google Truth, Detekt, Android Lint.

---

## 📂 Project Structure

```
├── .agents/
│   └── rules/
│       └── coding-standards.md      # Strict engineering & security rules
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/drivestream/app/
│       │   │   ├── auth/            # GoogleAuthManager, TokenManager, EncryptedTokenStorage
│       │   │   ├── data/            # Room Database, DAOs, Entities, Converters
│       │   │   ├── di/              # Hilt Modules (AppModule, AuthModule, DatabaseModule)
│       │   │   ├── network/         # TokenInterceptor, API client
│       │   │   ├── player/          # BufferConfig, ExoPlayer engine
│       │   │   └── ui/              # Compose screens (Login, Home, Browser, Player, Downloads)
│       │   └── res/                 # Resources, Themes, Network security config
│       └── test/                    # Comprehensive unit tests (TokenManager, Interceptor, ViewModels)
├── config/
│   └── detekt/detekt.yml            # Static code analysis configuration
└── docs/
    ├── TECHNICAL_REQUIREMENTS.md    # Product specs & functional requirements
    ├── TESTING_STRATEGY.md          # Test matrices and testing standards
    ├── OPERATIONAL_GUIDE.md         # Deployment, Proguard, Error taxonomy
    ├── roadmap.md                   # 7-sprint production roadmap
    ├── sprint0_qc_review.md         # Sprint 0 QC Report (PASS)
    ├── sprint1_qc_review.md         # Sprint 1 QC Report (PASS)
    └── walkthrough.md               # Continuous implementation log
```

---

## 📋 Roadmap Status

- [x] **Sprint 0: Project Foundation** (Gradle, Theme, Navigation, Room DB, Detekt, Lint, CI/CD toolchain)
- [x] **Sprint 1: Authentication & Token Management** (OAuth 2.0, AES-256 Storage, Proactive Refresh, 401 Retry, LoginScreen)
- [ ] **Sprint 2: Drive File Browser** (Folder traversal, mimeType filtering, 5-min caching, rate-limiting)
- [ ] **Sprint 3: Video Streaming Player** (ExoPlayer Media3, custom buffering, landscape fullscreen, PiP)
- [ ] **Sprint 4: Download & Offline Engine** (Foreground Service, Range-resumable download)
- [ ] **Sprint 5: Home Screen & Playback History** (Continue Watching, Room sync)
- [ ] **Sprint 6: Hardening, Optimization & Release** (R8/Proguard, strict security audit)

---

## 🧪 Build & Verification

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Run static analysis
./gradlew detekt

# Run Android Lint
./gradlew lintDebug

# Build Debug APK
./gradlew assembleDebug
```

---

## 🔒 License & Disclaimer

Personal project designed strictly for personal media access. Not affiliated with or endorsed by Google LLC.
