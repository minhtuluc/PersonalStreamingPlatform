# Implementation Plan – Sprint 1: Authentication & Token Management 🔐

Triển khai toàn bộ luồng xác thực Google OAuth 2.0, lưu trữ token mã hóa phần cứng, proactive token refresh (< 300s), concurrent deduplication, OkHttp TokenInterceptor tự động gán Bearer token và retry khi 401, cùng giao diện LoginScreen chuẩn Material 3 Dark.

---

## User Review Required

> [!IMPORTANT]
> **Google Cloud OAuth 2.0 Client ID Configuration:**
> - Để thực hiện đăng nhập Google OAuth trên thiết bị thật hoặc emulator, Google yêu cầu tạo **OAuth 2.0 Client ID (Android)** trên Google Cloud Console với Package Name `com.drivestream.app` và SHA-1 fingerprint của debug keystore.
> - Trong Sprint 1, `GoogleAuthManager` sẽ được kiến trúc theo interface phân tách (`AuthRepository` / `GoogleAuthManager`) cho phép mock test 100% không cần phụ thuộc vào network hay keystore của Google. Bạn chỉ cần cấu hình Client ID thực tế vào `strings.xml` hoặc `BuildConfig` khi test trực tiếp trên điện thoại.

> [!NOTE]
> **Drive Scope Minimal:** Tuân thủ nghiêm ngặt rule Section 4.1 trong [coding-standards.md](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/.agents/rules/coding-standards.md), app chỉ xin duy nhất scope `https://www.googleapis.com/auth/drive.readonly`. Tuyệt đối không yêu cầu write access hoặc full drive scope.

---

## Proposed Changes

### 1. Auth & Token Storage Layer (`com.drivestream.app.auth`)

#### [NEW] [AuthState.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/main/java/com/drivestream/app/auth/AuthState.kt)
- Sealed interface đại diện cho trạng thái Auth: `Unauthenticated`, `Authenticating`, `Authenticated(val user: AuthUser)`, `Error(val error: AppError)`.
- Data class `AuthUser(val email: String, val displayName: String?, val photoUrl: String?)`.
- Data class `AuthTokens(val accessToken: String, val refreshToken: String?, val expiresAtEpochSeconds: Long)`.

#### [NEW] [TokenStorage.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/main/java/com/drivestream/app/auth/TokenStorage.kt)
- Interface cho phép lưu/đọc/xóa tokens.
- Implementation `EncryptedTokenStorage` sử dụng `EncryptedSharedPreferences` với `MasterKey.DEFAULT_AES256_GCM_SPEC`.
- Lưu trữ an toàn: `access_token`, `refresh_token`, `expires_at`, `user_email`, `user_name`, `user_photo`.

#### [NEW] [TokenManager.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/main/java/com/drivestream/app/auth/TokenManager.kt)
- Quản lý vòng đời Access Token:
  - `getValidAccessToken()`: Kiểm tra thời hạn token. Nếu thời gian còn lại `< 300` giây (5 phút), tự động kích hoạt refresh trước khi trả về token.
  - Concurrent deduplication: Dùng `kotlinx.coroutines.sync.Mutex` để khi có 5-10 coroutine cùng gọi `getValidAccessToken()`, chỉ có đúng 1 network call refresh token thực tế diễn ra, các coroutine khác đợi kết quả dùng chung.
  - Không bao giờ log token ra Logcat/console (tuân thủ `SEC-01` & `SEC-04`).
  - Hỗ trợ injectable `Clock` interface để unit test thời gian mô phỏng chính xác.

#### [NEW] [GoogleAuthManager.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/main/java/com/drivestream/app/auth/GoogleAuthManager.kt)
- Bọc Google Sign-In SDK (`GoogleSignInClient` / Google Identity).
- Cấu hình GoogleSignInOptions với scope `drive.readonly` và `requestServerAuthCode` (để lấy refresh token qua OAuth exchange endpoint nếu cần).
- Cung cấp phương thức `getSignInIntent()`, `handleSignInResult(Intent?)`, `refreshToken()`, và `signOut()`.

---

### 2. Network & Interceptor Layer (`com.drivestream.app.network`)

#### [NEW] [TokenInterceptor.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/main/java/com/drivestream/app/network/TokenInterceptor.kt)
- OkHttp Interceptor:
  - Tự động gắn header `Authorization: Bearer <validAccessToken>` vào mọi request đến `googleapis.com`.
  - Bắt HTTP 401 Unauthorized: Gọi `TokenManager.refreshToken()` và retry lại request ban đầu 1 lần duy nhất. Nếu vẫn 401, ném `AppError.TokenExpired` để trigger re-login.

#### [MODIFY] [AppModule.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/main/java/com/drivestream/app/di/AppModule.kt)
- Thêm provide methods cho `TokenStorage`, `TokenManager`, `GoogleAuthManager`, và inject `TokenInterceptor` vào `OkHttpClient`.

---

### 3. Presentation Layer (`com.drivestream.app.ui.screens`)

#### [NEW] [AuthViewModel.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/main/java/com/drivestream/app/ui/screens/AuthViewModel.kt)
- Hilt ViewModel quản lý trạng thái đăng nhập theo MVVM + UDF (`StateFlow<AuthUiState>`).
- Kiểm tra auth state lúc khởi động app (`checkInitialAuthState()`): Nếu đã có token còn hạn hoặc refresh được -> navigate vào Home screen.
- Xử lý intent kết quả Sign-In, sign-out và thông báo lỗi qua `AppError`.

#### [MODIFY] [LoginScreen.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/main/java/com/drivestream/app/ui/screens/LoginScreen.kt)
- Nâng cấp từ scaffold ban đầu thành màn hình đăng nhập hoàn chỉnh:
  - Brand header sang trọng ("DriveStream - Native 4K Player").
  - Google Sign In button tùy chỉnh chuẩn Google brand guidelines trên nền Dark.
  - Loading spinner khi đang xác thực.
  - Error banner / Snackbar khi gặp sự cố mạng hoặc xác thực thất bại.
  - Tự động điều hướng sang Home khi đăng nhập thành công.

#### [MODIFY] [NavGraph.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/main/java/com/drivestream/app/ui/navigation/NavGraph.kt)
- Kết nối `AuthViewModel` vào `NavGraph`.
- Xử lý logic route khởi đầu linh hoạt: Nếu đã đăng nhập chuyển thẳng vào `Home`, nếu chưa chuyển vào `Login`.
- Kết nối sự kiện Sign-Out từ `HomeScreen` trở lại `LoginScreen` kèm việc dọn dẹp state.

---

### 4. Unit & Integration Tests (`src/test/java/com/drivestream/app`)

#### [NEW] [TokenManagerTest.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/test/java/com/drivestream/app/auth/TokenManagerTest.kt)
- `getAccessToken_whenTokenValid_returnsExistingToken`
- `getAccessToken_whenTokenExpiresInLessThan300s_triggersRefresh`
- `refreshToken_whenConcurrentRequests_deduplicatesToSingleCall`
- `refreshToken_whenNetworkFails_returnsNetworkError`
- `refreshToken_whenRefreshTokenInvalid401_triggersUnauthenticated`
- `signOut_clearsAllStoredTokensAndState`

#### [NEW] [TokenInterceptorTest.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/test/java/com/drivestream/app/network/TokenInterceptorTest.kt)
- `intercept_attachesBearerTokenHeader`
- `intercept_when401Received_refreshesTokenAndRetriesRequest`
- `intercept_whenRetryFailsWith401_propagatesError`

#### [NEW] [AuthViewModelTest.kt](file:///c:/Users/tumin/OneDrive/Documents/STREAMING/app/src/test/java/com/drivestream/app/ui/AuthViewModelTest.kt)
- Test các state transitions: `Unauthenticated` → `Authenticating` → `Authenticated` và `Error`.

---

## Verification Plan

### Automated Tests
1. **Unit tests**:
   ```powershell
   .\gradlew.bat testDebugUnitTest
   ```
   Xác nhận tất cả test suites (`TokenManagerTest`, `TokenInterceptorTest`, `AuthViewModelTest`, `SprintZeroSetupTest`) đều PASS.

2. **Static code analysis**:
   ```powershell
   .\gradlew.bat detekt
   ```
   Xác nhận 0 issue detekt, tuân thủ tất cả rules về naming, line length, không log token.

3. **Android Lint**:
   ```powershell
   .\gradlew.bat lintDebug
   ```
   Xác nhận 0 error, các security flags và permissions được tuân thủ.

4. **Debug APK Build**:
   ```powershell
   .\gradlew.bat assembleDebug
   ```
   Xác nhận APK build thành công, Hilt injection và Dagger dependency graph compile sạch.

### Manual Verification
- Chạy verify build script để kiểm tra kích thước APK và tính toàn vẹn của artifacts.
