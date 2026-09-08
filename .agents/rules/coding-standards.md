# DriveStream – Coding Standards & Agent Rules

> Tài liệu này là **quy tắc bắt buộc** cho tất cả coding agents (chính và thứ cấp) khi làm việc trên dự án DriveStream.
> Vi phạm bất kỳ quy tắc nào dưới đây đều KHÔNG được chấp nhận.

---

## 1. Nguyên Tắc Cốt Lõi

### 1.1 Think Before Coding
- **PHẢI** đọc hiểu file hiện có trước khi sửa đổi. Không bao giờ sửa code mà chưa đọc toàn bộ file.
- **PHẢI** hiểu context xung quanh: file nào import module này? Ai gọi function này?
- **KHÔNG BAO GIỜ** viết code dựa trên giả định. Nếu không chắc, hãy tìm hiểu trước.

### 1.2 Simplicity First
- Ưu tiên giải pháp đơn giản, dễ đọc. Code phức tạp chỉ được phép khi có lý do kỹ thuật rõ ràng.
- Mỗi function làm đúng 1 việc. Nếu function dài hơn 40 dòng, cần refactor.
- Mỗi class có đúng 1 responsibility. Nếu class có hơn 300 dòng, cần tách.

### 1.3 Surgical Changes
- Chỉ thay đổi đúng những gì cần thiết. Không refactor code không liên quan.
- Giữ nguyên mọi comment, docstring, và format của code xung quanh.
- Mỗi commit/change phải có scope rõ ràng, không trộn lẫn nhiều mục đích.

### 1.4 Zero Tolerance for Regressions
- Mọi thay đổi PHẢI được verify không break code hiện có.
- Nếu thêm dependency mới, PHẢI kiểm tra compatibility với dependencies hiện có.
- Nếu đổi API/interface, PHẢI update tất cả call sites.

---

## 2. Kotlin Coding Conventions

### 2.1 Naming
```kotlin
// Classes: PascalCase
class GDriveDataSource
class PlayerViewModel
class WatchHistoryDao

// Functions: camelCase, verb-first
fun loadFiles()
fun refreshTokenIfNeeded()
fun getAccessToken(): String

// Properties: camelCase, noun
val currentPosition: Long
val isPlaying: Boolean
private val _uiState = MutableStateFlow<UiState>()

// Constants: SCREAMING_SNAKE_CASE
const val MAX_BUFFER_SECONDS = 90
const val API_BASE_URL = "https://www.googleapis.com/drive/v3"

// Packages: all lowercase, no underscores
com.drivestream.app.player
com.drivestream.app.auth
```

### 2.2 Nullability
- **KHÔNG BAO GIỜ** dùng `!!` (non-null assertion). Thay vào đó dùng:
  - `?.let { }` cho optional chaining
  - `?: return` hoặc `?: throw` cho early exit
  - `requireNotNull()` khi giá trị null là lỗi logic
- Parameters không nullable trừ khi có lý do rõ ràng.

### 2.3 Coroutines
```kotlin
// ĐÚNG: Dùng viewModelScope hoặc lifecycleScope
viewModelScope.launch {
    val result = repository.loadFiles(folderId)
    _uiState.value = result
}

// SAI: Không bao giờ dùng GlobalScope
GlobalScope.launch { } // ❌ FORBIDDEN

// ĐÚNG: Dùng withContext cho IO operations
suspend fun loadFiles(): List<DriveFile> = withContext(Dispatchers.IO) {
    driveApi.files().list().execute()
}

// SAI: Không block main thread
runBlocking { } // ❌ FORBIDDEN trong production code
```

### 2.4 Error Handling
```kotlin
// ĐÚNG: Sealed class cho Result
sealed class DriveResult<out T> {
    data class Success<T>(val data: T) : DriveResult<T>()
    data class Error(val exception: Throwable, val message: String) : DriveResult<Nothing>()
    data object Loading : DriveResult<Nothing>()
}

// ĐÚNG: Catch specific exceptions
try {
    driveService.files().get(fileId).execute()
} catch (e: GoogleJsonResponseException) {
    when (e.statusCode) {
        401 -> tokenManager.refreshToken()
        403 -> handleRateLimit(e)
        404 -> handleFileNotFound(fileId)
        else -> throw e
    }
} catch (e: IOException) {
    handleNetworkError(e)
}

// SAI: Không bao giờ catch chung chung rồi bỏ qua
catch (e: Exception) { } // ❌ FORBIDDEN: swallowing exceptions
```

### 2.5 Immutability
- Ưu tiên `val` thay vì `var`. Chỉ dùng `var` khi giá trị thực sự cần thay đổi.
- Dùng `List`, `Map`, `Set` (immutable) thay vì `MutableList`, `MutableMap`, `MutableSet` trong public API.
- Data classes PHẢI có tất cả properties là `val`.

---

## 3. Android & Compose Conventions

### 3.1 Architecture Pattern: MVVM + UDF (Unidirectional Data Flow)
```
View (Compose) → Event → ViewModel → Repository → DataSource
     ↑                      ↓
     └──── State (StateFlow) ←┘
```

Mỗi màn hình PHẢI tuân thủ pattern:
```kotlin
// 1. UI State (sealed/data class)
data class BrowserUiState(
    val files: List<DriveFile> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentFolder: String = "root"
)

// 2. UI Events (sealed class)
sealed class BrowserEvent {
    data class OpenFolder(val folderId: String) : BrowserEvent()
    data class PlayVideo(val fileId: String) : BrowserEvent()
    data object Refresh : BrowserEvent()
    data object NavigateBack : BrowserEvent()
}

// 3. ViewModel
@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val repository: DriveRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    fun onEvent(event: BrowserEvent) {
        when (event) { /* handle each event */ }
    }
}

// 4. Composable Screen
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = hiltViewModel(),
    onNavigateToPlayer: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Render UI based on uiState
}
```

### 3.2 Compose Rules
- **KHÔNG** dùng `mutableStateOf` trong ViewModel. Dùng `MutableStateFlow` + `collectAsStateWithLifecycle()`.
- **KHÔNG** thực hiện side effects trong Composable body. Dùng `LaunchedEffect`, `SideEffect`, hoặc `DisposableEffect`.
- **PHẢI** dùng `remember` cho objects tạo trong Composable.
- **PHẢI** dùng `key()` hoặc `items(key = ...)` cho LazyColumn/LazyRow.
- Composable functions: PascalCase, prefix-free (không dùng `compose` prefix).
- Preview functions: PHẢI có `@Preview` annotation và suffix `Preview`.

```kotlin
// ĐÚNG
@Composable
fun VideoItem(
    video: DriveFile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier  // Modifier luôn là parameter cuối, default = Modifier
) { }

@Preview(showBackground = true)
@Composable
private fun VideoItemPreview() {
    VideoItem(video = previewVideo, onClick = {})
}
```

### 3.3 Navigation
- Dùng Navigation Compose với type-safe routes (Kotlin Serialization).
- Route names: PascalCase, mô tả destination.
- Arguments truyền qua route, KHÔNG qua shared ViewModel.

### 3.4 Dependency Injection (Hilt)
```kotlin
// Module: đặt trong package `di/`
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDriveRepository(/* deps */): DriveRepository { }
}

// Inject qua constructor, KHÔNG inject qua field
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: DriveRepository,
    private val tokenManager: TokenManager
) : ViewModel()
```

---

## 4. Google Drive API Rules

### 4.1 Scope Tối Thiểu
- CHỈ dùng scope `https://www.googleapis.com/auth/drive.readonly`
- **KHÔNG BAO GIỜ** request scope `drive` (full access) hoặc `drive.file`

### 4.2 Rate Limiting
```kotlin
// PHẢI implement rate limiter
// Tối đa 5 requests/giây cho metadata API
// Tối đa 2 concurrent download streams
// Exponential backoff: 1s → 2s → 4s → 8s → 16s → 32s (max)
```

### 4.3 Field Selection
```kotlin
// ĐÚNG: Chỉ lấy fields cần thiết
val fields = "nextPageToken, files(id, name, mimeType, size, thumbnailLink, videoMediaMetadata, modifiedTime)"

// SAI: Lấy tất cả fields
val fields = "*" // ❌ FORBIDDEN: wastes quota and bandwidth
```

### 4.4 Token Management
- Access token PHẢI được refresh proactively khi còn < 300 giây (5 phút).
- Refresh token PHẢI được lưu trong `EncryptedSharedPreferences`.
- **KHÔNG BAO GIỜ** log token ra Logcat/console.
- **KHÔNG BAO GIỜ** hardcode token hoặc API key trong source code.

---

## 5. Security Rules (KHÔNG THƯƠNG LƯỢNG)

| Rule | Description |
|---|---|
| **SEC-01** | KHÔNG log sensitive data (tokens, file IDs chứa nội dung nhạy cảm) |
| **SEC-02** | KHÔNG hardcode credentials trong source code |
| **SEC-03** | KHÔNG export Activities/Services/Receivers trừ khi bắt buộc |
| **SEC-04** | PHẢI dùng EncryptedSharedPreferences cho mọi data nhạy cảm |
| **SEC-05** | PHẢI set `android:allowBackup="false"` trong manifest |
| **SEC-06** | PHẢI dùng certificate pinning cho Google API endpoints |
| **SEC-07** | KHÔNG implement tính năng share/export video dưới bất kỳ hình thức nào |
| **SEC-08** | PHẢI validate mọi input từ API response trước khi sử dụng |
| **SEC-09** | File tải offline PHẢI lưu trong app-specific storage, KHÔNG lưu vào shared storage |
| **SEC-10** | PHẢI dùng HTTPS cho mọi network request (đã mặc định với Google APIs) |

---

## 6. File & Package Structure

```
com.drivestream.app/
├── auth/           ← Authentication & token management ONLY
├── data/           ← Room database, DAOs, entities ONLY
├── di/             ← Hilt modules ONLY
├── download/       ← Download manager & service ONLY
├── drive/          ← Google Drive API repository & models ONLY
├── player/         ← ExoPlayer DataSource, player logic ONLY
└── ui/
    ├── components/ ← Reusable Composable components
    ├── navigation/ ← Navigation graph & routes
    ├── screens/    ← Screen-level Composables + ViewModels (co-located)
    └── theme/      ← Material 3 theme, colors, typography
```

**Rules:**
- Mỗi package chỉ chứa code thuộc domain đó. Không cross-contaminate.
- Models/entities dùng chung đặt trong package gần nhất với nguồn gốc.
- Không tạo package `utils/`, `helpers/`, `common/` chung chung. Đặt utilities vào package sử dụng chúng.

---

## 7. Logging Standards

```kotlin
// Dùng Timber thay vì android.util.Log
// Setup trong Application class:
if (BuildConfig.DEBUG) {
    Timber.plant(Timber.DebugTree())
}

// Log levels:
Timber.d("Loading files from folder: %s", folderId)     // Debug: flow tracing
Timber.i("Token refreshed successfully")                  // Info: important events
Timber.w("Rate limit approached, backing off %d ms", ms)  // Warning: recoverable issues
Timber.e(exception, "Failed to load file: %s", fileId)    // Error: failures

// KHÔNG BAO GIỜ log:
// - Access/Refresh tokens
// - Full API responses (chỉ log status code + relevant fields)
// - User email/personal data
// - File content hoặc binary data
```

---

## 8. Performance Rules

| Rule | Threshold | Action if violated |
|---|---|---|
| **PERF-01** | App cold start < 2 giây | Defer non-critical init, dùng App Startup library |
| **PERF-02** | Frame rate ≥ 55fps khi scroll | Profile với Compose compiler metrics, tránh recomposition thừa |
| **PERF-03** | Memory: app idle < 100MB RAM | Profile với Android Profiler, fix memory leaks |
| **PERF-04** | Video start playback < 3 giây (WiFi) | Tune buffer parameters, preload metadata |
| **PERF-05** | Không ANR (Application Not Responding) | KHÔNG BAO GIỜ block main thread > 100ms |
| **PERF-06** | Network: mỗi API call < 5 giây timeout | Set OkHttp timeout, implement retry |
| **PERF-07** | Database query < 50ms | Index critical columns, dùng Room @Query optimization |
| **PERF-08** | Compose recomposition: skip rate > 80% | Dùng stable types, immutable state |

---

## 9. Git & Commit Conventions

```
# Format: <type>(<scope>): <description>
# Types: feat, fix, refactor, test, docs, chore, perf, sec

# Ví dụ:
feat(player): implement GDriveDataSource with Range header support
fix(auth): proactive token refresh at 300s before expiry
test(drive): add unit tests for DriveRepository.listFiles
refactor(ui): extract VideoItem into reusable component
perf(player): optimize buffer config for 4K streaming
sec(auth): encrypt refresh token with EncryptedSharedPreferences
docs(readme): add setup instructions for Google Cloud Console
chore(gradle): update Media3 to 1.4.0
```

---

## 10. Definition of Done (DoD)

Một task/feature chỉ được coi là **DONE** khi đáp ứng TẤT CẢ các tiêu chí sau:

- [ ] Code compiles without warnings (hoặc warnings đã được suppress với lý do documented)
- [ ] Unit tests written và pass (coverage ≥ 80% cho logic code)
- [ ] No hardcoded strings (dùng `strings.xml` cho UI text)
- [ ] Error states được handle (loading, error, empty, success)
- [ ] Logging thêm vào cho critical paths
- [ ] Không có `TODO` hoặc `FIXME` chưa được track
- [ ] Code tuân thủ mọi rules trong document này
- [ ] Build thành công: `./gradlew assembleDebug`
- [ ] Lint pass: `./gradlew lintDebug` (0 errors)
