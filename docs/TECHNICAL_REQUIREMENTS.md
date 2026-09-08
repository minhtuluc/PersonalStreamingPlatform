# DriveStream – Technical Requirements Document (TRD)

> Version: 1.0
> Status: Approved
> Last Updated: 2026-09-08

---

## 1. System Overview

**DriveStream** là ứng dụng Android cá nhân stream video từ Google Drive ở độ phân giải gốc, phục vụ **duy nhất 1 người dùng**. App được thiết kế với tiêu chuẩn production-grade, tối ưu hiệu suất streaming, và tuân thủ nghiêm ngặt chính sách Google API.

### 1.1 System Context Diagram

```
┌─────────────────────────────────────────────────────────┐
│                    Android Device                        │
│  ┌───────────────────────────────────────────────────┐  │
│  │              DriveStream App                       │  │
│  │  ┌──────┐  ┌──────────┐  ┌──────────────────┐    │  │
│  │  │ Auth │→│ Drive API │→│ ExoPlayer + Custom │    │  │
│  │  │Module│  │Repository│  │   DataSource       │    │  │
│  │  └──┬───┘  └────┬─────┘  └────────┬───────────┘   │  │
│  │     │           │                  │               │  │
│  │  ┌──▼───────────▼──────────────────▼───────────┐  │  │
│  │  │         Room Database (SQLite)               │  │  │
│  │  │  WatchHistory │ FileCache │ DownloadedVideo  │  │  │
│  │  └─────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────┘  │
│                                                          │
│  ┌───────────────────────────────────────────────────┐  │
│  │     App-Specific Storage (/Android/data/...)       │  │
│  │     └── Downloaded video files (offline)           │  │
│  └───────────────────────────────────────────────────┘  │
└────────────────────┬─────────────────────────────────────┘
                     │ HTTPS (TLS 1.3)
                     ▼
┌─────────────────────────────────────────────────────────┐
│              Google Cloud Platform                       │
│  ┌─────────────────┐    ┌──────────────────────────┐   │
│  │  OAuth 2.0       │    │  Google Drive API v3      │   │
│  │  Token Endpoint  │    │  files.list / files.get   │   │
│  │  Refresh Flow    │    │  ?alt=media (streaming)   │   │
│  └─────────────────┘    └──────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

## 2. Functional Requirements

### FR-01: Authentication
| ID | Requirement | Priority | Acceptance Criteria |
|---|---|---|---|
| FR-01-01 | Google Sign-In via OAuth 2.0 | P0 | User can sign in with Google account, app receives access + refresh tokens |
| FR-01-02 | Silent token refresh | P0 | Token refreshed automatically when < 300s remaining, zero user intervention |
| FR-01-03 | Secure token storage | P0 | Tokens encrypted with AES-256 via EncryptedSharedPreferences |
| FR-01-04 | Single account enforcement | P0 | Only 1 Google account can be signed in at a time |
| FR-01-05 | Sign-out with cleanup | P1 | Sign out clears all tokens, cache, watch history |
| FR-01-06 | Auth state persistence | P0 | User stays signed in across app restarts |

### FR-02: Drive File Browser
| ID | Requirement | Priority | Acceptance Criteria |
|---|---|---|---|
| FR-02-01 | List files in folder | P0 | Display video files and subfolders in any Drive folder |
| FR-02-02 | Video-only filter | P0 | Only show files with mimeType `video/*` and folders |
| FR-02-03 | Folder navigation | P0 | Tap folder to enter, back button to return to parent |
| FR-02-04 | File metadata display | P0 | Show name, size, resolution, thumbnail, modified date |
| FR-02-05 | Pull-to-refresh | P1 | Swipe down to reload current folder from API |
| FR-02-06 | Search videos | P1 | Full-text search across all video files in Drive |
| FR-02-07 | Sort options | P2 | Sort by name, date modified, size |
| FR-02-08 | Pagination | P0 | Load 50 files per page, infinite scroll for more |
| FR-02-09 | Local cache | P1 | Cache file list for 5 minutes to reduce API calls |

### FR-03: Video Player
| ID | Requirement | Priority | Acceptance Criteria |
|---|---|---|---|
| FR-03-01 | Stream at original quality | P0 | Video plays at source resolution (up to 4K), no transcoding |
| FR-03-02 | Seek support | P0 | User can seek to any position, buffering starts within 3s |
| FR-03-03 | Playback controls | P0 | Play, pause, seek bar, rewind 10s, forward 30s |
| FR-03-04 | Fullscreen landscape | P0 | Auto-rotate to landscape when playing, controls overlay |
| FR-03-05 | Gesture controls | P1 | Swipe left/right to seek, up/down for volume/brightness |
| FR-03-06 | Buffering indicator | P0 | Show buffering spinner with percentage loaded |
| FR-03-07 | Resume playback | P0 | Remember last position, prompt to resume on re-open |
| FR-03-08 | Picture-in-Picture | P2 | PiP mode when navigating away from player |
| FR-03-09 | Background audio | P2 | Continue audio playback when screen off |
| FR-03-10 | Codec support | P0 | MP4 (H.264/H.265), MKV (all codecs via FFmpeg), MOV |

### FR-04: Download & Offline
| ID | Requirement | Priority | Acceptance Criteria |
|---|---|---|---|
| FR-04-01 | Download video to device | P0 | Download full video file to app-specific storage |
| FR-04-02 | Download progress | P0 | Show real-time progress bar and download speed |
| FR-04-03 | Pause/Resume download | P1 | Pause and resume download using HTTP Range headers |
| FR-04-04 | Background download | P0 | Continue downloading when app is backgrounded (Foreground Service) |
| FR-04-05 | Offline playback | P0 | Play downloaded videos without internet connection |
| FR-04-06 | Delete downloaded | P0 | Delete individual downloaded videos, reclaim storage |
| FR-04-07 | Storage usage display | P1 | Show total storage used by downloaded videos |
| FR-04-08 | Download queue | P2 | Queue multiple downloads, process sequentially |

### FR-05: Home Screen
| ID | Requirement | Priority | Acceptance Criteria |
|---|---|---|---|
| FR-05-01 | Continue watching | P0 | Show videos with saved position, sorted by last watched |
| FR-05-02 | Recently viewed | P1 | Show last 20 watched videos |
| FR-05-03 | Downloaded videos | P0 | Quick access to offline videos |
| FR-05-04 | Browse Drive button | P0 | Navigate to Drive file browser |

---

## 3. Non-Functional Requirements

### NFR-01: Performance

| ID | Metric | Target | Measurement Method |
|---|---|---|---|
| NFR-01-01 | Cold start time | < 2 seconds | Android Vitals / Macrobenchmark |
| NFR-01-02 | Video start latency (WiFi) | < 3 seconds | Manual timing from tap to first frame |
| NFR-01-03 | Video start latency (4G) | < 6 seconds | Manual timing on cellular |
| NFR-01-04 | Seek latency | < 2 seconds | Time from seek action to playback resume |
| NFR-01-05 | UI frame rate | ≥ 55 fps | Android GPU Profiler |
| NFR-01-06 | Memory usage (idle) | < 100 MB | Android Profiler |
| NFR-01-07 | Memory usage (4K playback) | < 350 MB | Android Profiler during 4K stream |
| NFR-01-08 | APK size | < 25 MB | Build output |
| NFR-01-09 | Database query time | < 50 ms (p99) | Room query profiling |
| NFR-01-10 | File list load time | < 1.5 seconds | API call + render time |

### NFR-02: Reliability

| ID | Metric | Target |
|---|---|---|
| NFR-02-01 | Crash-free rate | ≥ 99.5% |
| NFR-02-02 | ANR rate | 0% |
| NFR-02-03 | Token refresh success rate | ≥ 99.9% |
| NFR-02-04 | Video playback completion rate | ≥ 95% (no mid-stream failures) |
| NFR-02-05 | Download completion rate | ≥ 98% (with resume capability) |
| NFR-02-06 | Data integrity | 0 data loss for watch history and downloads |

### NFR-03: Security

| ID | Requirement | Implementation |
|---|---|---|
| NFR-03-01 | Token encryption at rest | AES-256 via EncryptedSharedPreferences |
| NFR-03-02 | Network security | HTTPS only, TLS 1.3, certificate pinning for googleapis.com |
| NFR-03-03 | No data export | No share/export functionality for video files or links |
| NFR-03-04 | Minimal permissions | INTERNET, FOREGROUND_SERVICE only (no storage permissions needed) |
| NFR-03-05 | No backup | `android:allowBackup="false"` to prevent token extraction |
| NFR-03-06 | ProGuard/R8 | Code obfuscation enabled for release builds |
| NFR-03-07 | Input validation | Validate all API responses before processing |

### NFR-04: Compatibility

| ID | Requirement |
|---|---|
| NFR-04-01 | Min SDK: API 26 (Android 8.0 Oreo) |
| NFR-04-02 | Target SDK: API 35 (Android 15) |
| NFR-04-03 | Supported ABIs: arm64-v8a, armeabi-v7a, x86_64 |
| NFR-04-04 | Tested on: Pixel 6+, Samsung Galaxy S21+ |
| NFR-04-05 | Screen sizes: phone (360dp+), tablet (600dp+) |

---

## 4. Architecture Decision Records (ADRs)

### ADR-01: ExoPlayer Custom DataSource vs. Download-then-Play

**Decision:** Custom `HttpDataSource` streaming trực tiếp từ Google Drive API.

**Context:** Có 2 cách để phát video từ Google Drive:
1. Tải toàn bộ file về rồi phát (Download-then-Play)
2. Stream trực tiếp qua custom DataSource với Range headers

**Rationale:**
- Download-then-Play yêu cầu người dùng chờ tải xong (5GB file = 10-30 phút chờ)
- Custom DataSource cho phép phát ngay lập tức, chỉ buffer phần cần xem
- Google Drive API hỗ trợ HTTP Range requests (206 Partial Content)
- ExoPlayer được thiết kế để hoạt động với custom DataSource

**Consequences:**
- (+) Phát video ngay lập tức
- (+) Tiết kiệm bộ nhớ (không cần lưu toàn bộ file)
- (-) Phụ thuộc vào kết nối mạng
- (-) Cần xử lý token refresh trong DataSource

---

### ADR-02: Room Database vs. DataStore

**Decision:** Room Database cho structured data, EncryptedSharedPreferences cho tokens.

**Rationale:**
- Watch history và file cache cần query phức tạp (sort, filter, join) → Room
- Download tracking cần atomic updates và status queries → Room
- Tokens chỉ cần key-value đơn giản + encryption → EncryptedSharedPreferences
- DataStore không phù hợp cho relational data

---

### ADR-03: Hilt vs. Koin vs. Manual DI

**Decision:** Hilt (Dagger-based).

**Rationale:**
- Compile-time validation (phát hiện lỗi DI lúc build, không phải runtime)
- Tích hợp tốt với ViewModel, WorkManager, Navigation
- Google officially recommends Hilt cho Android
- Koin: runtime DI, dễ miss dependency errors

---

### ADR-04: Foreground Service vs. WorkManager cho Download

**Decision:** Foreground Service cho active downloads, không dùng WorkManager.

**Rationale:**
- WorkManager designed cho deferred work, có thể bị hệ thống delay
- Download cần chạy liên tục, user-initiated, cần notification realtime
- Foreground Service đảm bảo hệ thống không kill process khi đang tải
- Android 12+ yêu cầu Foreground Service cho long-running tasks có notification

---

## 5. API Contract Specifications

### 5.1 Google Drive API v3 – Endpoints Used

#### List Files in Folder
```http
GET https://www.googleapis.com/drive/v3/files
Parameters:
  q: "'<folderId>' in parents and (mimeType contains 'video/' or mimeType = 'application/vnd.google-apps.folder') and trashed = false"
  fields: "nextPageToken, files(id, name, mimeType, size, thumbnailLink, videoMediaMetadata, modifiedTime, parents)"
  pageSize: 50
  pageToken: <optional, for pagination>
  orderBy: "folder, name"
Headers:
  Authorization: Bearer <access_token>

Response 200:
{
  "nextPageToken": "...",
  "files": [
    {
      "id": "abc123",
      "name": "Movie.mp4",
      "mimeType": "video/mp4",
      "size": "5368709120",
      "thumbnailLink": "https://...",
      "videoMediaMetadata": {
        "width": 3840,
        "height": 2160,
        "durationMillis": "7200000"
      },
      "modifiedTime": "2026-09-01T10:00:00.000Z"
    }
  ]
}
```

#### Stream Video (Original Quality)
```http
GET https://www.googleapis.com/drive/v3/files/<fileId>?alt=media
Headers:
  Authorization: Bearer <access_token>
  Range: bytes=<start>-<end>    (optional, for seeking)

Response 200 (full file) or 206 (partial content):
  Content-Type: video/mp4
  Content-Length: <size>
  Content-Range: bytes <start>-<end>/<total>  (only for 206)
  Body: <binary video data>
```

#### Get File Metadata
```http
GET https://www.googleapis.com/drive/v3/files/<fileId>
Parameters:
  fields: "id, name, mimeType, size, videoMediaMetadata, thumbnailLink"
Headers:
  Authorization: Bearer <access_token>

Response 200:
{
  "id": "abc123",
  "name": "Movie.mp4",
  ...
}
```

### 5.2 Error Response Handling Matrix

| HTTP Code | Meaning | App Response |
|---|---|---|
| 200 | Success | Process response normally |
| 206 | Partial Content | Process range response (seeking) |
| 400 | Bad Request | Log error, show user-friendly message |
| 401 | Unauthorized | Silent token refresh → retry (max 1 retry) |
| 403 | Forbidden / Rate Limit | Check `reason` field: if `rateLimitExceeded` → exponential backoff; if `forbidden` → show "Access denied" |
| 404 | File Not Found | Remove from cache, show "File not found or deleted" |
| 416 | Range Not Satisfiable | Reset seek position to 0, retry |
| 500 | Server Error | Retry with backoff (max 3 retries) |
| 503 | Service Unavailable | Retry with backoff (max 3 retries) |

---

## 6. Data Models

### 6.1 Room Entities

```kotlin
@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val fileId: String,
    val fileName: String,
    val lastPosition: Long,        // milliseconds
    val duration: Long,            // milliseconds
    val lastWatched: Long,         // epoch millis
    val thumbnailUrl: String?,
    val resolution: String?,       // e.g. "3840x2160"
    val fileSize: Long             // bytes
)

@Entity(tableName = "file_cache")
data class FileCacheEntity(
    @PrimaryKey val folderId: String,
    val filesJson: String,         // JSON serialized list
    val cachedAt: Long             // epoch millis
)

@Entity(tableName = "downloaded_videos")
data class DownloadedVideoEntity(
    @PrimaryKey val fileId: String,
    val fileName: String,
    val localPath: String,
    val fileSize: Long,            // bytes
    val downloadedBytes: Long,     // bytes downloaded so far
    val downloadedAt: Long,        // epoch millis
    val status: DownloadStatus     // enum: QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED
)

enum class DownloadStatus {
    QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED
}
```

### 6.2 Domain Models

```kotlin
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val thumbnailUrl: String?,
    val resolution: Resolution?,
    val durationMs: Long?,
    val modifiedAt: Instant,
    val isFolder: Boolean
)

data class Resolution(
    val width: Int,
    val height: Int
) {
    val label: String get() = when {
        width >= 3840 -> "4K"
        width >= 2560 -> "1440p"
        width >= 1920 -> "1080p"
        width >= 1280 -> "720p"
        else -> "${height}p"
    }
}

data class PlaybackState(
    val fileId: String,
    val position: Long,
    val duration: Long,
    val isPlaying: Boolean,
    val bufferPercentage: Int,
    val playbackSpeed: Float = 1.0f
)
```

---

## 7. ExoPlayer Buffer Configuration

```kotlin
// Optimized for: MP4/MKV/MOV, up to 4K, files ≤ 5GB
// Network: WiFi primary, 4G secondary

object BufferConfig {
    // Minimum buffer before playback starts
    const val MIN_BUFFER_MS = 30_000            // 30 seconds

    // Maximum buffer to keep in memory
    const val MAX_BUFFER_MS = 90_000            // 90 seconds

    // Buffer to accumulate before resuming after rebuffer
    const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5_000  // 5 seconds

    // Buffer needed before initial playback starts
    const val BUFFER_FOR_PLAYBACK_MS = 2_500    // 2.5 seconds

    // Keep 30s of already-played content for quick rewind
    const val BACK_BUFFER_DURATION_MS = 30_000  // 30 seconds

    // Allow back buffer to be retained (don't discard)
    const val RETAIN_BACK_BUFFER = true

    // HTTP connection/read timeouts
    const val CONNECT_TIMEOUT_MS = 10_000       // 10 seconds
    const val READ_TIMEOUT_MS = 30_000          // 30 seconds
}
```

---

## 8. Android Manifest Permissions

```xml
<manifest>
    <!-- Network access for Google Drive API -->
    <uses-permission android:name="android.permission.INTERNET" />

    <!-- Foreground service for downloads -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

    <!-- POST_NOTIFICATIONS for download progress (Android 13+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- PiP support declaration -->
    <uses-feature android:name="android.software.picture_in_picture" android:required="false" />

    <application
        android:allowBackup="false"
        android:usesCleartextTraffic="false"
        android:networkSecurityConfig="@xml/network_security_config">
        <!-- ... -->
    </application>
</manifest>
```
