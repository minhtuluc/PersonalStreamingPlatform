# DriveStream – Testing Strategy & Quality Assurance

> Version: 1.0
> Status: Approved
> Last Updated: 2026-09-08

---

## 1. Testing Philosophy

### Nguyên Tắc
1. **Test Pyramid**: Nhiều unit tests (nhanh, rẻ) → Ít integration tests → Rất ít E2E tests
2. **Test Behavior, Not Implementation**: Test kết quả đầu ra, không test cách code hoạt động bên trong
3. **Every Bug Gets a Test**: Khi phát hiện bug, viết test reproduce bug TRƯỚC khi fix
4. **No Test, No Merge**: Code mới PHẢI có test đi kèm

### Coverage Targets

| Layer | Target | Tool |
|---|---|---|
| **ViewModel** | ≥ 90% | JUnit 5 + Turbine (Flow testing) |
| **Repository** | ≥ 85% | JUnit 5 + MockK |
| **DataSource** | ≥ 80% | JUnit 5 + OkHttp MockWebServer |
| **Room DAO** | ≥ 90% | AndroidX Test + In-memory Room DB |
| **Compose UI** | ≥ 70% | Compose UI Test |
| **Overall** | ≥ 80% | JaCoCo coverage report |

---

## 2. Test Structure & Conventions

### 2.1 Directory Structure
```
app/src/
├── main/java/com/drivestream/app/     ← Production code
├── test/java/com/drivestream/app/     ← Unit tests (JVM)
│   ├── auth/
│   │   └── GoogleAuthManagerTest.kt
│   ├── drive/
│   │   ├── DriveRepositoryTest.kt
│   │   └── DriveViewModelTest.kt
│   ├── player/
│   │   ├── GDriveDataSourceTest.kt
│   │   └── PlayerViewModelTest.kt
│   ├── download/
│   │   └── DownloadManagerTest.kt
│   └── testutil/
│       ├── FakeDriveRepository.kt
│       ├── FakeTokenManager.kt
│       └── TestCoroutineRule.kt
│
└── androidTest/java/com/drivestream/app/  ← Instrumented tests (Device)
    ├── data/
    │   ├── WatchHistoryDaoTest.kt
    │   ├── FileCacheDaoTest.kt
    │   └── DownloadedVideoDaoTest.kt
    └── ui/
        ├── screens/
        │   ├── LoginScreenTest.kt
        │   ├── BrowserScreenTest.kt
        │   ├── PlayerScreenTest.kt
        │   └── HomeScreenTest.kt
        └── navigation/
            └── NavGraphTest.kt
```

### 2.2 Naming Convention
```kotlin
// Format: `methodName_condition_expectedResult`
@Test
fun `refreshToken_whenTokenExpiredIn3Minutes_refreshesSuccessfully`() { }

@Test
fun `listFiles_whenRateLimited_retriesWithExponentialBackoff`() { }

@Test
fun `loadVideo_whenFileNotFound_returnsError404`() { }

// Nested classes for grouping
class DriveRepositoryTest {
    @Nested
    inner class ListFiles {
        @Test
        fun `returns files filtered by video mimeType`() { }

        @Test
        fun `paginates with pageToken`() { }

        @Test
        fun `returns cached result when cache is fresh`() { }
    }

    @Nested
    inner class SearchVideos {
        @Test
        fun `searches by name across all folders`() { }
    }
}
```

### 2.3 Test Dependencies
```kotlin
// build.gradle.kts (app)
dependencies {
    // Unit Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("app.cash.turbine:turbine:1.1.0")           // Flow testing
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0") // HTTP mocking
    testImplementation("com.google.truth:truth:1.4.4")              // Assertions

    // Instrumented Testing
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("io.mockk:mockk-android:1.13.12")
}
```

---

## 3. Unit Test Specifications

### 3.1 TokenManager Tests

```kotlin
class TokenManagerTest {
    // Test cases:
    // ✅ getAccessToken returns valid token when not expired
    // ✅ getAccessToken triggers refresh when token expires in < 300s
    // ✅ refreshToken succeeds and updates stored token
    // ✅ refreshToken handles network failure gracefully
    // ✅ refreshToken handles invalid refresh token (401) → forces re-login
    // ✅ concurrent refresh requests are deduplicated (only 1 actual refresh)
    // ✅ token is stored encrypted in EncryptedSharedPreferences
    // ✅ signOut clears all stored tokens
}
```

### 3.2 DriveRepository Tests

```kotlin
class DriveRepositoryTest {
    // List Files:
    // ✅ returns video files and folders only (filters non-video)
    // ✅ maps API response to DriveFile domain model correctly
    // ✅ handles empty folder (returns empty list)
    // ✅ handles pagination (pageToken forwarding)
    // ✅ returns cached result when cache is fresh (< 5 min)
    // ✅ fetches from API when cache is expired (> 5 min)
    // ✅ handles 401 → triggers token refresh → retries
    // ✅ handles 403 rate limit → exponential backoff → retries
    // ✅ handles 404 → returns appropriate error
    // ✅ handles network timeout → returns error with retry option

    // Search:
    // ✅ searches across all Drive with fullText query
    // ✅ returns empty list for no matches
    // ✅ escapes special characters in search query

    // Subtitle Placeholder:
    // ✅ findSubtitleForVideo returns null (placeholder)
}
```

### 3.3 GDriveDataSource Tests

```kotlin
class GDriveDataSourceTest {
    // Using OkHttp MockWebServer

    // ✅ open() sends correct URL with fileId and alt=media
    // ✅ open() includes Authorization Bearer header
    // ✅ open() sends Range header for non-zero position
    // ✅ read() returns correct bytes from response body
    // ✅ read() tracks bytesRead for accurate position
    // ✅ close() releases connection and resources
    // ✅ handles 206 Partial Content response correctly
    // ✅ handles 401 → refreshes token → retries request
    // ✅ handles 403 rate limit → backs off → retries
    // ✅ handles network disconnect → retries up to 3 times
    // ✅ handles 416 Range Not Satisfiable → resets position
    // ✅ concurrent reads don't corrupt data
}
```

### 3.4 PlayerViewModel Tests

```kotlin
class PlayerViewModelTest {
    // Using Turbine for StateFlow testing

    // ✅ initial state is Loading
    // ✅ loadVideo sets up ExoPlayer with correct DataSource
    // ✅ saves position to WatchHistory every 10 seconds
    // ✅ saves position when player paused
    // ✅ restores position from WatchHistory on load
    // ✅ handles player error → shows error state with retry
    // ✅ playback speed change updates state
    // ✅ lifecycle: pauses on background, resumes on foreground
    // ✅ prefers local file over streaming when download exists
}
```

### 3.5 DownloadManager Tests

```kotlin
class DownloadManagerTest {
    // ✅ startDownload creates file and begins writing
    // ✅ startDownload updates progress via Flow (0→100)
    // ✅ pauseDownload stops writing and saves bytesDownloaded
    // ✅ resumeDownload sends Range header from last byte
    // ✅ cancelDownload deletes partial file and DB record
    // ✅ deleteDownloaded deletes completed file and DB record
    // ✅ handles token refresh during large file download
    // ✅ handles network disconnect → sets status to PAUSED
    // ✅ concurrent downloads limited to 1
    // ✅ download queue processes sequentially
}
```

### 3.6 ViewModel UI State Tests

```kotlin
class BrowserViewModelTest {
    // ✅ initial state: loading = true, files = empty
    // ✅ after load: loading = false, files = populated
    // ✅ on error: loading = false, error = message
    // ✅ openFolder pushes to navigation stack
    // ✅ navigateBack pops from navigation stack
    // ✅ refresh reloads from API (ignores cache)
    // ✅ sort changes file order in state
}
```

---

## 4. Integration Test Specifications

### 4.1 Room Database Tests (Instrumented)

```kotlin
@RunWith(AndroidJUnit4::class)
class WatchHistoryDaoTest {
    // ✅ insert and query by fileId
    // ✅ update lastPosition for existing entry
    // ✅ getRecentlyWatched returns sorted by lastWatched DESC
    // ✅ getContinueWatching returns only entries with lastPosition > 0 and < duration
    // ✅ deleteAll clears all entries
    // ✅ upsert: insert if new, update if exists
}

@RunWith(AndroidJUnit4::class)
class FileCacheDaoTest {
    // ✅ insert and query by folderId
    // ✅ isExpired returns true when cachedAt > 5 minutes ago
    // ✅ deleteExpired removes old cache entries
}

@RunWith(AndroidJUnit4::class)
class DownloadedVideoDaoTest {
    // ✅ insert and query by fileId
    // ✅ updateProgress updates downloadedBytes and status
    // ✅ getByStatus returns filtered by DownloadStatus
    // ✅ getCompleted returns only COMPLETED downloads
    // ✅ getTotalStorageUsed returns sum of fileSize for COMPLETED
    // ✅ delete removes entry by fileId
}
```

### 4.2 API Integration Tests (MockWebServer)

```kotlin
class DriveApiIntegrationTest {
    // Full flow tests with MockWebServer:
    // ✅ List files → parse response → return DriveFile list
    // ✅ List files with pagination → first page → next page
    // ✅ Stream video → open DataSource → read bytes → close
    // ✅ Token expired mid-stream → refresh → retry → success
    // ✅ Rate limit → backoff → retry → success
    // ✅ Server error → retry 3 times → fail gracefully
}
```

---

## 5. UI Test Specifications (Compose)

```kotlin
class LoginScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    // ✅ shows app logo and sign-in button
    // ✅ sign-in button triggers Google Sign-In flow
    // ✅ shows loading indicator during sign-in
    // ✅ shows error message on sign-in failure
}

class BrowserScreenTest {
    // ✅ shows loading indicator when loading
    // ✅ displays video items with name, size, resolution
    // ✅ displays folder items with folder icon
    // ✅ tap on folder triggers navigation event
    // ✅ tap on video triggers play event
    // ✅ pull-to-refresh triggers reload
    // ✅ shows empty state when no videos in folder
    // ✅ shows error state with retry button
}

class PlayerScreenTest {
    // ✅ shows video player view
    // ✅ shows playback controls on tap
    // ✅ hides controls after 3 seconds
    // ✅ shows buffering indicator when buffering
    // ✅ back button exits player
}

class HomeScreenTest {
    // ✅ shows "Continue Watching" section with in-progress videos
    // ✅ shows "Downloaded" section with offline videos
    // ✅ shows "Browse Drive" navigation button
    // ✅ tap on continue watching item navigates to player
}
```

---

## 6. Performance Testing

### 6.1 Startup Benchmark
```kotlin
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() {
        benchmarkRule.measureRepeated(
            packageName = "com.drivestream.app",
            metrics = listOf(StartupTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD
        ) {
            pressHome()
            startActivityAndWait()
        }
        // Assert: startup < 2000ms
    }
}
```

### 6.2 Compose Performance
```kotlin
// Monitor recomposition with Compose Compiler Metrics
// Build with: ./gradlew assembleRelease -PcomposeCompilerReports=true
// Check: app/build/compose_metrics/

// Rules:
// - All data classes used in Composable params MUST be @Stable or @Immutable
// - StateFlow.collectAsStateWithLifecycle() instead of collectAsState()
// - key() parameter MUST be set for all LazyColumn items
// - Avoid lambda allocations in hot paths (use remember { })
```

### 6.3 Memory Leak Detection
```kotlin
// Integration with LeakCanary (debug builds only)
debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

// Critical leak scenarios to monitor:
// - Player not released on screen exit
// - Context leak in DownloadService
// - Coroutine scope leak in ViewModel
// - Bitmap/Thumbnail cache not cleared
```

---

## 7. Technical Debt Prevention

### 7.1 Static Analysis Pipeline

```yaml
# Run on every code change:
Lint:
  tool: Android Lint (./gradlew lintDebug)
  threshold: 0 errors, < 5 warnings
  blockers:
    - HardcodedText (use strings.xml)
    - MissingPermission
    - ObsoleteSdkInt
    - UnusedResources
    - PrivateApi

Detekt:
  tool: detekt (Kotlin static analysis)
  config: config/detekt.yml
  rules:
    - complexity:
        LongMethod: { threshold: 40 }
        LongParameterList: { threshold: 6 }
        ComplexCondition: { threshold: 4 }
        CyclomaticComplexity: { threshold: 10 }
    - style:
        MagicNumber: { active: true }
        MaxLineLength: { maxLineLength: 120 }
        WildcardImport: { active: true }
    - exceptions:
        SwallowedException: { active: true }
        TooGenericExceptionCaught: { active: true }

Dependency Check:
  tool: Gradle Versions Plugin
  schedule: Weekly
  action: Report outdated dependencies, flag security vulnerabilities
```

### 7.2 Code Quality Gates

Mỗi Phase PHẢI pass tất cả quality gates trước khi chuyển sang Phase tiếp theo:

```
┌─────────────────────────────────────────────────┐
│              QUALITY GATE CHECKLIST               │
├─────────────────────────────────────────────────┤
│ □ ./gradlew assembleDebug           → SUCCESS    │
│ □ ./gradlew lintDebug               → 0 ERRORS   │
│ □ ./gradlew testDebugUnitTest       → ALL PASS    │
│ □ ./gradlew connectedDebugAndroidTest → ALL PASS  │
│ □ ./gradlew detekt                  → 0 ERRORS   │
│ □ Code coverage ≥ 80%              → VERIFIED    │
│ □ No new TODO/FIXME without ticket  → VERIFIED   │
│ □ All UI states handled             → VERIFIED   │
│ □ Error logging added               → VERIFIED   │
│ □ No hardcoded strings              → VERIFIED   │
└─────────────────────────────────────────────────┘
```

### 7.3 Debt Tracking Rules
- Nếu phải tạo technical debt (vì deadline hoặc scope), **PHẢI**:
  1. Tạo `// TODO(debt): <description> – tracked in <issue-id>` comment
  2. Ghi vào `docs/TECH_DEBT_LOG.md` với severity (P0/P1/P2) và estimated effort
  3. Debt P0 PHẢI được resolve trong Phase hiện tại
  4. Debt P1 PHẢI được resolve trước khi kết thúc Phase tiếp theo
  5. Debt P2 có thể defer nhưng không quá 2 Phases

---

## 8. Test Data & Fixtures

### 8.1 Fake Implementations

```kotlin
// FakeDriveRepository.kt – for ViewModel unit tests
class FakeDriveRepository : DriveRepository {
    var filesToReturn: List<DriveFile> = emptyList()
    var errorToThrow: Exception? = null
    var listFilesCallCount = 0

    override suspend fun listFiles(folderId: String): DriveResult<List<DriveFile>> {
        listFilesCallCount++
        errorToThrow?.let { return DriveResult.Error(it, it.message ?: "") }
        return DriveResult.Success(filesToReturn)
    }
}

// FakeTokenManager.kt – for DataSource and Repository tests
class FakeTokenManager : TokenManager {
    var tokenToReturn = "fake-access-token-123"
    var shouldFailRefresh = false
    var refreshCallCount = 0

    override suspend fun getAccessToken(): String = tokenToReturn

    override suspend fun refreshTokenIfNeeded() {
        refreshCallCount++
        if (shouldFailRefresh) throw AuthException("Refresh failed")
    }
}
```

### 8.2 Test Fixtures

```kotlin
object TestFixtures {
    val sampleVideoFile = DriveFile(
        id = "file-001",
        name = "Sample Movie.mp4",
        mimeType = "video/mp4",
        size = 2_147_483_648, // 2GB
        thumbnailUrl = "https://thumbnail.example.com/001",
        resolution = Resolution(1920, 1080),
        durationMs = 7_200_000, // 2 hours
        modifiedAt = Instant.parse("2026-09-01T10:00:00Z"),
        isFolder = false
    )

    val sample4KFile = sampleVideoFile.copy(
        id = "file-002",
        name = "4K Documentary.mkv",
        mimeType = "video/x-matroska",
        size = 5_000_000_000, // 5GB
        resolution = Resolution(3840, 2160)
    )

    val sampleFolder = DriveFile(
        id = "folder-001",
        name = "Movies",
        mimeType = "application/vnd.google-apps.folder",
        size = 0,
        thumbnailUrl = null,
        resolution = null,
        durationMs = null,
        modifiedAt = Instant.parse("2026-08-15T08:00:00Z"),
        isFolder = true
    )

    val sampleWatchHistory = WatchHistoryEntity(
        fileId = "file-001",
        fileName = "Sample Movie.mp4",
        lastPosition = 3_600_000, // 1 hour in
        duration = 7_200_000,
        lastWatched = System.currentTimeMillis(),
        thumbnailUrl = "https://thumbnail.example.com/001",
        resolution = "1920x1080",
        fileSize = 2_147_483_648
    )
}
```

---

## 9. Continuous Verification Commands

```bash
# === Run all checks (use before completing any Phase) ===

# 1. Compile check
./gradlew assembleDebug

# 2. Unit tests
./gradlew testDebugUnitTest

# 3. Instrumented tests (requires device/emulator)
./gradlew connectedDebugAndroidTest

# 4. Lint
./gradlew lintDebug

# 5. Static analysis
./gradlew detekt

# 6. Coverage report
./gradlew jacocoTestReport
# Report at: app/build/reports/jacoco/

# 7. Dependency vulnerability check
./gradlew dependencyCheckAnalyze

# === Quick check (use during development) ===
./gradlew assembleDebug testDebugUnitTest lintDebug
```
