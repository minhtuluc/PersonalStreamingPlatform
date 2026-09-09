# DriveStream 🎬

> **Native 4K HDR Video Streaming & Offline Player directly from Personal Google Drive to Android.**  
> Zero transcoding, original bitrates, custom chunk buffering, hardware-backed security, and Dark Luxury UI.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-purple.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Android Gradle Plugin](https://img.shields.io/badge/AGP-8.5.2-green.svg?style=flat&logo=android)](https://developer.android.com/studio/releases/gradle-plugin)
[![Compose BOM](https://img.shields.io/badge/Compose_BOM-2024.08.00-blue.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Media3 ExoPlayer](https://img.shields.io/badge/Media3_ExoPlayer-1.4.0-red.svg?style=flat)](https://developer.android.com/media/media3)
[![License](https://img.shields.io/badge/License-Personal_Use_Only-orange.svg)](#-license--disclaimer)

---

## 🌟 Overview

Google Drive's built-in web interface and official mobile apps heavily compress and transcode uploaded videos down to low-bitrate 720p or 1080p, completely stripping HDR metadata and original audio tracks.

**DriveStream** is an open-source, private Android media client crafted for personal Google Drive / Google One users (e.g., 2TB – 30TB tiers) to stream and download their original video libraries (MP4, MKV, MOV up to 4K 60fps HDR) directly:
* **No Server Intermediary:** Direct client-to-Google-storage connection via Google Drive API v3.
* **Zero Quality Loss:** Streams raw byte ranges without server-side transcoding or re-encoding.
* **Intelligent Buffering:** Custom chunked range requests and back-buffers preventing Google API rate limits (`HTTP 429`).
* **Offline-First Playback:** Full background download manager with resume support and automatic local playback preference.

---

## 🚀 Key Features

### 1. 🎬 High-Performance Video Player
* **Media3 ExoPlayer Integration:** Custom `GDriveDataSource` handling HTTP Range requests (`206 Partial Content`) with Bearer token injection.
* **Tailored Buffer Profile:**
  * Minimum buffer: **30 seconds** | Maximum buffer: **90 seconds**.
  * Playback start threshold: **2.5 seconds** | Buffer for rebuffer: **5 seconds**.
  * Back-buffer retention: **30 seconds** for instantaneous instant-replay without re-fetching.
* **Interactive Luxury HUD:**
  * Auto-hiding controls (4-second timeout).
  * Double-tap to seek (±10s) with animated chevron feedback.
  * Vertical swipe gestures for brightness (left) and volume (right).
  * Screen lock toggle to prevent accidental touches.
  * **Screen Rotation Button:** Instant toggle between Landscape and Portrait orientation with safe system reset on exit.
  * Picture-in-Picture (PiP) support for seamless multitasking.
* **Smart Resume:** Tracks watch progress automatically in Room DB; prompts to resume when video was watched between 5% and 95%.

### 2. 📁 Intelligent Drive Browser
* **Hierarchical Navigation:** Seamlessly traverse My Drive, Synced Computer Folders, and Shared Folders via parent IDs.
* **Lightweight Video-Only Filter:** Automatically filters items to show only folders and video mime types (`video/*`), ignoring images, audio, and documents for blazing fast browsing.
* **Global Drive Search:** Instant search across the entire Drive with 400ms debounce, querying only video files.
* **Room Cache & Rate-Limiter:** 5-minute local cache for directory listings with pull-to-refresh and sliding-window rate-limiting.
* **CircuitBreaker Protection:** 3-state circuit breaker (`CLOSED`, `OPEN`, `HALF_OPEN`) protecting against Google Drive API quota lockouts upon consecutive network errors.

### 3. 📥 Download & Offline Viewing Engine
* **Foreground Service (`dataSync`):** Persistent interactive notifications with live progress bars and Pause/Resume/Cancel controls.
* **Resumable HTTP Range Downloads:** Downloads write directly to `.part` files and resume from the exact byte offset if interrupted.
* **Pre-flight Storage Verification:** Validates available disk space before starting downloads to avoid storage exhaustion.
* **Automatic Local Playback Preference:** When tapping a video, the app automatically checks if a completed download exists locally on disk; if found, it plays from local storage with 0% network usage.

### 4. 🏠 Home Screen & Polished Experience
* **Continue Watching Carousel:** Displays in-progress videos with thumbnail cards, remaining time, and progress bars.
* **Quick Access Cards:** One-tap navigation to Drive Browser and Offline Downloads.
* **Recent Watch History:** Comprehensive playback history with direct tap-to-play.
* **Edge-to-Edge Display:** Tailored `statusBarsPadding()` and `navigationBarsPadding()` preventing content clipping on notches, dynamic islands, or camera cutouts.
* **Signature LMT Adaptive Icon:** Custom gold-on-dark luxury vector branding replacing standard Android defaults.
* **Housekeeping Automation:** Background cleanup service running on app startup to purge stale HTTP cache (>7d), old watch logs (>90d), and orphaned partial downloads.

---

## 🛡️ Security & Privacy Architecture

* **Strict Minimal OAuth Scope:** Requests exclusively `https://www.googleapis.com/auth/drive.readonly`. The app has **zero write permissions** and **zero delete permissions**.
* **Zero Public Link Sharing:** Videos are streamed strictly using temporary, authenticated OAuth 2.0 Bearer tokens. Files are never set to public or shared externally.
* **Hardware-Backed Cryptography:** Tokens and session metadata are stored in `EncryptedSharedPreferences` backed by the Android Keystore (`MasterKeys.AES256_GCM`).
* **Proactive Silent Token Refresh:** Tokens are automatically refreshed in the background when less than 300 seconds of validity remain, preventing playback freezes.
* **Concurrency Deduplication:** Mutex-based double-checked locking prevents multiple parallel network requests from triggering redundant token refresh calls.

---

## 🛠 Tech Stack

| Layer | Technologies |
|---|---|
| **Language & Tooling** | Kotlin 2.0.0, KSP 2.0.0-1.0.24, Android Gradle Plugin 8.5.2, Java 17 |
| **UI Framework** | 100% Jetpack Compose (BOM 2024.08.00), Material 3 Dark Luxury theme |
| **Architecture** | MVVM + Clean Architecture + Unidirectional Data Flow (UDF) |
| **Dependency Injection** | Dagger Hilt 2.51.1 |
| **Media Playback** | AndroidX Media3 (ExoPlayer) 1.4.0, Media3 UI, Media3 DataSource |
| **Local Persistence** | AndroidX Room 2.6.1 (SQLite with Coroutines Flow & TypeConverters) |
| **Networking & HTTP** | OkHttp 4.12.0, Custom Interceptors, Google API Client Android 2.6.0, Google Drive API v3 |
| **Security** | AndroidX Security Crypto 1.1.0-alpha06 (Keystore AES256_GCM) |
| **Image Loading** | Coil Compose 2.7.0 |
| **Code Quality & Testing** | Detekt 1.23.6, Android Lint, JUnit 5, MockK 1.13.12, Turbine 1.1.0, MockWebServer, Google Truth |

---

## 📂 Project Structure

```
STREAMING/
├── .agents/
│   └── rules/
│       └── coding-standards.md      # Engineering rules, security constraints & testing standards
├── app/
│   ├── proguard-rules.pro           # R8 optimization & reflection preserve rules
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/drivestream/app/
│   │   │   │   ├── auth/            # GoogleAuthManager, TokenManager, EncryptedTokenStorage
│   │   │   │   ├── data/            # Room Database, DAOs, Entities, Converters, Repositories
│   │   │   │   ├── di/              # Hilt Modules (AppModule, AuthModule, DatabaseModule, etc.)
│   │   │   │   ├── download/        # DownloadManager, DownloadService, NotificationHandler
│   │   │   │   ├── maintenance/     # HousekeepingManager (cache & log purger)
│   │   │   │   ├── network/         # TokenInterceptor, CircuitBreaker, RateLimiter, GDrive Remote
│   │   │   │   ├── observability/   # PerfTracker, NetworkTracker
│   │   │   │   ├── player/          # BufferConfig, GDriveDataSource, MediaSourceFactory
│   │   │   │   └── ui/              # Compose UI
│   │   │   │       ├── browser/     # DriveViewModel, DriveUiState
│   │   │   │       ├── components/  # FileItemCard, NavigationBar, TopBars
│   │   │   │       ├── home/        # HomeViewModel, HomeUiState
│   │   │   │       ├── navigation/  # NavGraph, Screen routes
│   │   │   │       ├── player/      # PlayerViewModel, PlayerGestureOverlay
│   │   │   │       ├── screens/     # LoginScreen, HomeScreen, BrowserScreen, PlayerScreen, DownloadsScreen
│   │   │   │       └── theme/       # Dark Luxury Color, Typography, Shapes
│   │   │   └── res/                 # Adaptive icons, drawables, strings, network security config
│   │   └── test/                    # Comprehensive unit tests (MockK, Turbine, JUnit 5)
├── config/
│   └── detekt/detekt.yml            # Static code analysis configuration
└── docs/
    ├── TECHNICAL_REQUIREMENTS.md    # System specifications & functional requirements
    ├── TESTING_STRATEGY.md          # Test matrices, mock data & quality criteria
    ├── OPERATIONAL_GUIDE.md         # Deployment, Google Cloud setup & troubleshooting
    └── walkthrough.md               # Continuous sprint implementation history
```

---

## ⚙️ Google Cloud Console Setup

To enable personal Google Sign-In and Google Drive API access:

1. **Create / Select a Project:**
   * Open [Google Cloud Console](https://console.cloud.google.com/).
   * Create a new project (e.g., `PersonalStreamingPlatform`).
2. **Enable Google Drive API:**
   * Navigate to **APIs & Services > Library**.
   * Search for **Google Drive API** and click **Enable**.
3. **Configure OAuth Consent Screen:**
   * Go to **APIs & Services > OAuth consent screen**.
   * Select User Type: **External** (for personal @gmail.com accounts).
   * Fill in required app details (App name: `DriveStream`, User support email).
   * **Scopes:** Add `https://www.googleapis.com/auth/drive.readonly`.
   * **Test Users:** Add the specific Gmail address(es) that will be logging into the app. *(Crucial: While in Testing status, only accounts listed here can log in!)*
4. **Create Android OAuth 2.0 Client ID:**
   * Go to **APIs & Services > Credentials > Create Credentials > OAuth client ID**.
   * Application type: **Android**.
   * **Package name:** `com.drivestream.app`
   * **SHA-1 certificate fingerprint:** Extract your debug/release keystore fingerprint:
     ```powershell
     # For debug keystore:
     keytool -list -v -keystore $env:USERPROFILE\.android\debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```

---

## 🔧 Building & Deployment

### Prerequisites
* JDK 17 or JDK 21 configured (`JAVA_HOME`).
* Android SDK Platform 35 and Build Tools installed.

### Build Commands

```bash
# 1. Run all unit tests (31 test suites across auth, network, player, download)
./gradlew testDebugUnitTest

# 2. Run Detekt static analysis
./gradlew detekt

# 3. Run Android Lint check
./gradlew lintDebug

# 4. Build Debug APK (Package: com.drivestream.app.debug)
./gradlew assembleDebug

# 5. Build Optimized Release APK with R8 Minification & Resource Shrinking (~4.3MB)
./gradlew assembleRelease
```

### APK Output Paths
* **Release APK:** `app/build/outputs/apk/release/app-release.apk`
* **Debug APK:** `app/build/outputs/apk/debug/app-debug.apk`

---

## 📱 Device Deployment & Troubleshooting

### Sideloading via ADB
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

### Common Issues & Tips

* **Login Fails / Immediately Disappears on Xiaomi (MIUI / HyperOS):**
  * **Cause 1 (Test Users):** Ensure the logged-in Gmail is added to **Test users** in Google Cloud Console.
  * **Cause 2 (Google Services):** Go to *Settings > Accounts & Sync > Basic Google services* and ensure it is **ON**.
  * **Cause 3 (Account Permissions):** Go to *Settings > Apps > Manage Apps > DriveStream > Permissions / Other Permissions*, and enable **"Get account info"** and **"Display pop-up windows while running in the background"**.
* **Video Playback Fails with 401 / 403:**
  * Verify that the Google Drive API is Enabled in your Google Cloud Project.
  * In the app, tap Sign Out from the Home screen and log in again to trigger fresh token retrieval.

---

## 📋 Production Status

| Phase | Description | Status |
|---|---|---|
| **Sprint 0** | Project Foundation, Toolchain, Theme, Room DB, Navigation | ✅ Completed |
| **Sprint 1** | Authentication & Token Lifecycle (AES256, Silent Refresh, 401 Retry) | ✅ Completed |
| **Sprint 2** | Drive File Browser (Hierarchy, Video filter, Debounce search, Cache) | ✅ Completed |
| **Sprint 3** | Video Streaming Engine (Media3, Range requests, Buffer profile, HUD) | ✅ Completed |
| **Sprint 4** | Download & Offline Engine (Foreground Service, Range resume, Local preference) | ✅ Completed |
| **Sprint 5** | Home Screen & Hardening (Continue watching, CircuitBreaker, Housekeeping) | ✅ Completed |
| **Polish** | Safe Insets (Status Bar), Player Rotation Toggle, Signature LMT Vector Icon | ✅ Completed |

---

## 🔒 License & Disclaimer

This software is developed strictly for **personal media streaming** by the owner of the respective Google Drive account.
* Not affiliated with, sponsored by, or endorsed by Google LLC.
* All trademarks belong to their respective owners.
